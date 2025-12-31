package com.hualien.taxidriver.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.hualien.taxidriver.data.remote.RetrofitClient
import com.hualien.taxidriver.data.remote.WebSocketManager
import com.hualien.taxidriver.data.remote.dto.PassengerVoiceCommand
import com.hualien.taxidriver.data.remote.dto.SubmitRatingRequest
import com.hualien.taxidriver.data.repository.OrderRepository
import com.hualien.taxidriver.domain.model.Order
import com.hualien.taxidriver.service.DirectionsApiService
import com.hualien.taxidriver.service.DirectionsResult
import com.hualien.taxidriver.service.DriverMatchingService
import com.hualien.taxidriver.service.DriverWithETA
import com.hualien.taxidriver.service.HybridLocationService
import com.hualien.taxidriver.service.LocationState
import com.hualien.taxidriver.service.LocationSource
import com.hualien.taxidriver.service.VoiceRecorderService
import com.hualien.taxidriver.service.PlacesApiService
import com.hualien.taxidriver.service.PlaceDetails
import com.hualien.taxidriver.service.PlacePrediction
import com.hualien.taxidriver.utils.FareCalculator
import com.hualien.taxidriver.utils.FareResult
import com.hualien.taxidriver.utils.PassengerVoiceCommandHandler
import com.hualien.taxidriver.utils.VoiceAssistant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

/**
 * 附近司機資訊
 */
data class NearbyDriver(
    val driverId: String,
    val driverName: String,
    val location: LatLng,
    val rating: Float = 5.0f
)

/**
 * 語音錄製狀態
 */
enum class VoiceRecordingStatus {
    IDLE,           // 閒置
    RECORDING,      // 錄音中
    PROCESSING,     // 處理中（上傳/解析）
    ERROR           // 錯誤
}

/**
 * 語音自動流程狀態
 */
enum class VoiceAutoflowState {
    IDLE,                      // 閒置
    SEARCHING_DESTINATION,     // 正在搜尋目的地
    CONFIRMING_DESTINATION,    // 等待用戶確認目的地
    SELECTING_PICKUP,          // 用戶正在選擇上車點（Uber風格地圖）
    BOOKING                    // 正在叫車
}

/**
 * 乘客端UI狀態
 */
data class PassengerUiState(
    // 位置相關
    val currentLocation: LatLng? = null,
    val pickupLocation: LatLng? = null,
    val destinationLocation: LatLng? = null,
    val pickupAddress: String = "",
    val destinationAddress: String = "",
    val locationState: LocationState = LocationState.Idle,  // 定位狀態
    val locationSource: LocationSource? = null,             // 定位來源
    val locationAccuracy: Double? = null,                   // 定位精度（公尺）

    // 路線資訊（Directions API）
    val routeInfo: DirectionsResult? = null,
    val fareEstimate: FareResult? = null,
    val isCalculatingRoute: Boolean = false,

    // 附近司機
    val nearbyDrivers: List<NearbyDriver> = emptyList(),
    val driversWithETA: List<DriverWithETA> = emptyList(),  // 包含 ETA 的司機列表
    val isCalculatingETA: Boolean = false,                   // 是否正在計算 ETA

    // 訂單狀態
    val currentOrder: Order? = null,
    val orderStatus: OrderStatus = OrderStatus.IDLE,

    // 語音助理狀態
    val voiceRecordingStatus: VoiceRecordingStatus = VoiceRecordingStatus.IDLE,
    val voiceAmplitude: Int = 0,                            // 錄音振幅（用於波形動畫）
    val lastVoiceCommand: PassengerVoiceCommand? = null,    // 最後解析的語音指令
    val voiceError: String? = null,                         // 語音錯誤訊息
    val pendingDestinationQuery: String? = null,            // 待搜尋的目的地（語音叫車）
    val pendingPickupQuery: String? = null,                 // 待搜尋的上車點（語音設置）

    // 語音自動流程狀態
    val voiceAutoflowState: VoiceAutoflowState = VoiceAutoflowState.IDLE,
    val pendingDestinationDetails: PlaceDetails? = null,    // 待確認的目的地詳情
    val showPickupMapSelector: Boolean = false,             // 顯示 Uber 風格地圖選點

    // 對話泡泡狀態
    val currentSpeechText: String? = null,                  // 小橘當前說的話（顯示在對話泡泡中）
    val isSpeaking: Boolean = false,                        // 小橘是否正在說話

    // UI狀態
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * 訂單狀態
 */
enum class OrderStatus {
    IDLE,           // 閒置（未叫車）
    REQUESTING,     // 叫車中
    WAITING,        // 等待司機接單
    ACCEPTED,       // 司機已接單
    DRIVER_ARRIVING,// 司機前往中
    ARRIVED,        // 司機已到達
    ON_TRIP,        // 行程中
    SETTLING,       // 結算中
    COMPLETED,      // 已完成
    CANCELLED       // 已取消
}

/**
 * 乘客端ViewModel
 */
class PassengerViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "PassengerViewModel"
    }

    private val orderRepository = OrderRepository()
    private val passengerRepository = com.hualien.taxidriver.data.repository.PassengerRepository()
    private val webSocketManager = WebSocketManager.getInstance()
    private val directionsService = DirectionsApiService(application)
    private val driverMatchingService = DriverMatchingService(application)
    private val hybridLocationService = HybridLocationService(application)

    // 語音相關服務
    private val voiceAssistant = VoiceAssistant(application)
    private val voiceRecorderService = VoiceRecorderService(application)
    private val voiceCommandHandler = PassengerVoiceCommandHandler(application, voiceAssistant)

    // 地點搜尋服務（語音自動流程）
    private val placesApiService = PlacesApiService(application)

    // 乘客 ID（用於語音上下文）
    private var currentPassengerId: String = ""

    private val _uiState = MutableStateFlow(PassengerUiState())
    val uiState: StateFlow<PassengerUiState> = _uiState.asStateFlow()

    init {
        // 初始化語音指令處理器回調
        setupVoiceCommandHandler()

        // 監聽錄音狀態
        viewModelScope.launch {
            voiceRecorderService.state.collect { recordingState ->
                val status = when (recordingState) {
                    is VoiceRecorderService.RecordingState.Idle -> VoiceRecordingStatus.IDLE
                    is VoiceRecorderService.RecordingState.Recording -> VoiceRecordingStatus.RECORDING
                    is VoiceRecorderService.RecordingState.Processing -> VoiceRecordingStatus.PROCESSING
                    is VoiceRecorderService.RecordingState.Error -> VoiceRecordingStatus.ERROR
                }
                _uiState.value = _uiState.value.copy(
                    voiceRecordingStatus = status,
                    voiceError = if (recordingState is VoiceRecorderService.RecordingState.Error) {
                        recordingState.message
                    } else null
                )
            }
        }

        // 監聽錄音振幅
        viewModelScope.launch {
            voiceRecorderService.amplitude.collect { amplitude ->
                _uiState.value = _uiState.value.copy(voiceAmplitude = amplitude)
            }
        }

        // 監聽混合定位狀態更新
        viewModelScope.launch {
            hybridLocationService.locationState.collect { locationState ->
                when (locationState) {
                    is LocationState.Success -> {
                        _uiState.value = _uiState.value.copy(
                            currentLocation = locationState.location,
                            locationState = locationState,
                            locationSource = locationState.source,
                            locationAccuracy = locationState.accuracy
                        )

                        android.util.Log.d("PassengerViewModel",
                            "Location updated: ${locationState.location}, " +
                            "accuracy: ${locationState.accuracy}m, " +
                            "source: ${locationState.source}")
                    }
                    is LocationState.Error -> {
                        _uiState.value = _uiState.value.copy(
                            locationState = locationState,
                            error = locationState.message
                        )
                    }
                    else -> {
                        _uiState.value = _uiState.value.copy(locationState = locationState)
                    }
                }
            }
        }

        // 監聽 WebSocket 推送的附近司機位置
        viewModelScope.launch {
            android.util.Log.d("PassengerViewModel", "開始監聽 WebSocket 附近司機推送...")
            webSocketManager.nearbyDrivers.collect { nearbyDriversInfo ->
                android.util.Log.d("PassengerViewModel", "========== 收到WebSocket附近司機推送 ==========")
                android.util.Log.d("PassengerViewModel", "司機數量: ${nearbyDriversInfo.size}")

                if (nearbyDriversInfo.isNotEmpty()) {
                    val drivers = nearbyDriversInfo.map { info ->
                        android.util.Log.d("PassengerViewModel", "司機: ${info.driverId}")
                        android.util.Log.d("PassengerViewModel", "  位置: (${info.latitude}, ${info.longitude})")

                        NearbyDriver(
                            driverId = info.driverId,
                            driverName = "司機 ${info.driverId.takeLast(4)}",
                            location = LatLng(info.latitude, info.longitude),
                            rating = 5.0f // 目前沒有評分信息
                        )
                    }

                    android.util.Log.d("PassengerViewModel", "✅ 更新UI顯示 ${drivers.size} 位司機")
                    _uiState.value = _uiState.value.copy(nearbyDrivers = drivers)

                    // 如果有上車點，自動計算司機 ETA
                    _uiState.value.pickupLocation?.let { pickupLocation ->
                        calculateDriverETAs(drivers, pickupLocation)
                    }
                } else {
                    android.util.Log.d("PassengerViewModel", "✅ 清空司機列表（無在線司機）")
                    _uiState.value = _uiState.value.copy(nearbyDrivers = emptyList())
                }
            }
        }

        // 監聽訂單狀態更新（乘客端）
        viewModelScope.launch {
            webSocketManager.passengerOrderUpdate.collect { order ->
                order?.let {
                    updateOrderStatus(it)
                }
            }
        }

        // 監聽司機實時位置（實時更新地圖標記）
        viewModelScope.launch {
            webSocketManager.driverLocation.collect { driverLocation ->
                driverLocation?.let { location ->
                    android.util.Log.d("PassengerViewModel",
                        "Driver location updated: ${location.driverId} at (${location.latitude}, ${location.longitude})")

                    // 只更新已存在於列表中的司機位置（避免與 nearby:drivers 衝突）
                    val currentDrivers = _uiState.value.nearbyDrivers
                    val driverExists = currentDrivers.any { it.driverId == location.driverId }

                    if (driverExists) {
                        // 只在司機已存在時更新位置（防止重複創建）
                        val updatedDrivers = currentDrivers.map { driver ->
                            if (driver.driverId == location.driverId) {
                                val newLocation = LatLng(location.latitude, location.longitude)
                                // 檢查位置是否真的改變（避免不必要的 UI 更新）
                                if (driver.location.latitude != newLocation.latitude ||
                                    driver.location.longitude != newLocation.longitude) {
                                    driver.copy(location = newLocation)
                                } else {
                                    driver  // 位置沒變，返回原對象
                                }
                            } else {
                                driver
                            }
                        }

                        // 只在真的有變化時才更新 UI
                        if (updatedDrivers != currentDrivers) {
                            _uiState.value = _uiState.value.copy(nearbyDrivers = updatedDrivers)
                        }
                    }
                    // 註：新司機由 nearby:drivers 事件負責添加，這裡不處理
                }
            }
        }
    }

    /**
     * 更新當前位置
     */
    fun updateCurrentLocation(location: LatLng) {
        _uiState.value = _uiState.value.copy(currentLocation = location)
    }

    /**
     * 設置上車點
     */
    fun setPickupLocation(location: LatLng, address: String) {
        _uiState.value = _uiState.value.copy(
            pickupLocation = location,
            pickupAddress = address
        )

        // 如果有附近司機，計算 ETA
        if (_uiState.value.nearbyDrivers.isNotEmpty()) {
            calculateDriverETAs(_uiState.value.nearbyDrivers, location)
        }

        // 如果已有目的地，自動計算路線
        _uiState.value.destinationLocation?.let { destination ->
            calculateRoute(location, destination)
        }
    }

    /**
     * 設置目的地
     */
    fun setDestinationLocation(location: LatLng, address: String) {
        _uiState.value = _uiState.value.copy(
            destinationLocation = location,
            destinationAddress = address
        )

        // 如果已有上車點，自動計算路線
        _uiState.value.pickupLocation?.let { pickup ->
            calculateRoute(pickup, location)
        }
    }

    /**
     * 更新附近司機列表
     */
    fun updateNearbyDrivers() {
        viewModelScope.launch {
            val currentLocation = _uiState.value.currentLocation
            if (currentLocation == null) {
                // 如果沒有當前位置，使用花蓮預設位置
                val defaultLat = 23.9871
                val defaultLng = 121.6015
                fetchNearbyDrivers(defaultLat, defaultLng)
            } else {
                fetchNearbyDrivers(currentLocation.latitude, currentLocation.longitude)
            }
        }
    }

    /**
     * 從 API 獲取附近司機
     */
    private suspend fun fetchNearbyDrivers(latitude: Double, longitude: Double) {
        try {
            android.util.Log.d("PassengerViewModel", "========== 從API獲取附近司機 ==========")
            android.util.Log.d("PassengerViewModel", "查詢位置: ($latitude, $longitude)")
            android.util.Log.d("PassengerViewModel", "查詢半徑: 5000公尺")

            val result = passengerRepository.getNearbyDrivers(
                latitude = latitude,
                longitude = longitude,
                radius = 5000
            )

            result.onSuccess { drivers ->
                android.util.Log.d("PassengerViewModel", "✅ API返回司機數量: ${drivers.size}")
                drivers.forEachIndexed { index, driver ->
                    android.util.Log.d("PassengerViewModel", "司機${index + 1}: ${driver.driverId} - ${driver.driverName}")
                    android.util.Log.d("PassengerViewModel", "  位置: (${driver.location.latitude}, ${driver.location.longitude})")
                }

                _uiState.value = _uiState.value.copy(nearbyDrivers = drivers)
            }.onFailure { error ->
                android.util.Log.e("PassengerViewModel", "❌ API獲取司機失敗: ${error.message}")
                _uiState.value = _uiState.value.copy(
                    error = error.message ?: "無法獲取附近司機"
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("PassengerViewModel", "❌ 獲取司機異常: ${e.message}")
            _uiState.value = _uiState.value.copy(
                error = e.message ?: "網路錯誤"
            )
        }
    }

    /**
     * 發送叫車請求
     */
    fun requestTaxi(
        passengerId: String,
        passengerName: String,
        passengerPhone: String
    ) {
        val state = _uiState.value

        android.util.Log.d("PassengerViewModel", "========== 開始叫車流程 ==========")
        android.util.Log.d("PassengerViewModel", "乘客資訊: ID=$passengerId, 姓名=$passengerName, 電話=$passengerPhone")

        // 驗證必要資訊
        if (state.pickupLocation == null || state.pickupAddress.isEmpty()) {
            android.util.Log.e("PassengerViewModel", "❌ 叫車失敗：未選擇上車地點")
            _uiState.value = state.copy(error = "請選擇上車地點")
            return
        }

        android.util.Log.d("PassengerViewModel", "上車點: ${state.pickupAddress}")
        android.util.Log.d("PassengerViewModel", "上車座標: (${state.pickupLocation.latitude}, ${state.pickupLocation.longitude})")
        android.util.Log.d("PassengerViewModel", "目的地: ${state.destinationAddress.ifEmpty { "未設定" }}")
        if (state.destinationLocation != null) {
            android.util.Log.d("PassengerViewModel", "目的地座標: (${state.destinationLocation.latitude}, ${state.destinationLocation.longitude})")
        }

        viewModelScope.launch {
            _uiState.value = state.copy(
                isLoading = true,
                orderStatus = OrderStatus.REQUESTING
            )

            android.util.Log.d("PassengerViewModel", "📡 準備發送API請求...")

            try {
                val result = passengerRepository.requestRide(
                    passengerId = passengerId,
                    passengerName = passengerName,
                    passengerPhone = passengerPhone,
                    pickupLat = state.pickupLocation!!.latitude,
                    pickupLng = state.pickupLocation.longitude,
                    pickupAddress = state.pickupAddress,
                    destLat = state.destinationLocation?.latitude,
                    destLng = state.destinationLocation?.longitude,
                    destAddress = state.destinationAddress.takeIf { it.isNotEmpty() },
                    paymentType = "CASH"
                )

                result.onSuccess { rideResult ->
                    android.util.Log.d("PassengerViewModel", "✅ 叫車請求成功！")
                    android.util.Log.d("PassengerViewModel", "訂單ID: ${rideResult.order.orderId}")
                    android.util.Log.d("PassengerViewModel", "訂單狀態: ${rideResult.order.statusString}")
                    android.util.Log.d("PassengerViewModel", "推送給司機: ${rideResult.offeredToDrivers}")
                    android.util.Log.d("PassengerViewModel", "訊息: ${rideResult.message}")

                    // 使用當前狀態 copy，避免覆蓋其他狀態變更
                    _uiState.value = _uiState.value.copy(
                        currentOrder = rideResult.order,
                        orderStatus = OrderStatus.WAITING,
                        isLoading = false,
                        error = null,
                        // 重置語音自動流程狀態
                        voiceAutoflowState = VoiceAutoflowState.IDLE,
                        showPickupMapSelector = false
                    )
                }.onFailure { error ->
                    android.util.Log.e("PassengerViewModel", "❌ 叫車失敗: ${error.message}")
                    android.util.Log.e("PassengerViewModel", "錯誤詳情: ", error)

                    // 使用當前狀態 copy
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        orderStatus = OrderStatus.IDLE,
                        error = error.message ?: "叫車失敗",
                        // 失敗時也重置語音流程
                        voiceAutoflowState = VoiceAutoflowState.IDLE,
                        showPickupMapSelector = false
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("PassengerViewModel", "❌ 叫車異常: ${e.message}")
                android.util.Log.e("PassengerViewModel", "異常詳情: ", e)

                // 使用當前狀態 copy
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    orderStatus = OrderStatus.IDLE,
                    error = e.message ?: "叫車失敗",
                    // 異常時也重置語音流程
                    voiceAutoflowState = VoiceAutoflowState.IDLE,
                    showPickupMapSelector = false
                )
            }
        }
    }

    /**
     * 取消訂單
     */
    fun cancelOrder(passengerId: String) {
        viewModelScope.launch {
            val state = _uiState.value
            val orderId = state.currentOrder?.orderId ?: return@launch

            _uiState.value = state.copy(isLoading = true)

            try {
                val result = passengerRepository.cancelOrder(
                    orderId = orderId,
                    passengerId = passengerId,
                    reason = "乘客取消"
                )

                result.onSuccess { message ->
                    _uiState.value = state.copy(
                        currentOrder = null,
                        orderStatus = OrderStatus.CANCELLED,
                        isLoading = false,
                        pickupLocation = null,
                        destinationLocation = null,
                        pickupAddress = "",
                        destinationAddress = "",
                        routeInfo = null,
                        fareEstimate = null
                    )
                }.onFailure { error ->
                    _uiState.value = state.copy(
                        isLoading = false,
                        error = error.message ?: "取消失敗"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = state.copy(
                    isLoading = false,
                    error = e.message ?: "取消失敗"
                )
            }
        }
    }

    /**
     * 更新訂單狀態（從WebSocket接收）
     */
    fun updateOrderStatus(order: Order) {
        android.util.Log.d("PassengerViewModel", "========== 更新訂單狀態 ==========")
        android.util.Log.d("PassengerViewModel", "訂單ID: ${order.orderId}")
        android.util.Log.d("PassengerViewModel", "狀態字串: ${order.statusString}")
        android.util.Log.d("PassengerViewModel", "司機ID: ${order.driverId}")
        android.util.Log.d("PassengerViewModel", "司機姓名: ${order.driverName}")

        val orderStatus = when (order.statusString) {
            "WAITING" -> OrderStatus.WAITING
            "OFFERED" -> OrderStatus.WAITING
            "ACCEPTED" -> OrderStatus.ACCEPTED
            "ARRIVED" -> OrderStatus.ARRIVED
            "ON_TRIP" -> OrderStatus.ON_TRIP
            "SETTLING" -> OrderStatus.SETTLING
            "COMPLETED" -> OrderStatus.COMPLETED
            "DONE" -> OrderStatus.COMPLETED
            "CANCELLED" -> OrderStatus.CANCELLED
            else -> {
                android.util.Log.w("PassengerViewModel", "未知的訂單狀態: ${order.statusString}")
                OrderStatus.IDLE
            }
        }

        android.util.Log.d("PassengerViewModel", "映射後的訂單狀態: $orderStatus")

        _uiState.value = _uiState.value.copy(
            currentOrder = order,
            orderStatus = orderStatus
        )

        android.util.Log.d("PassengerViewModel", "✅ UI 狀態已更新")
    }

    /**
     * 清除錯誤訊息
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /**
     * 清除當前訂單（用於完成後繼續叫車）
     */
    fun clearOrder() {
        android.util.Log.d("PassengerViewModel", "清除當前訂單，準備繼續叫車")
        _uiState.value = _uiState.value.copy(
            currentOrder = null,
            orderStatus = OrderStatus.IDLE,
            pickupLocation = null,
            destinationLocation = null,
            pickupAddress = "",
            destinationAddress = "",
            routeInfo = null,
            fareEstimate = null
        )
    }

    /**
     * 計算路線（Directions API）
     */
    fun calculateRoute(origin: LatLng, destination: LatLng) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCalculatingRoute = true)

            try {
                val result = directionsService.getDirections(origin, destination)

                result.onSuccess { directions ->
                    // 計算車資
                    val fare = FareCalculator.calculateFare(directions.distanceMeters)

                    _uiState.value = _uiState.value.copy(
                        routeInfo = directions,
                        fareEstimate = fare,
                        isCalculatingRoute = false,
                        error = null
                    )

                    android.util.Log.d("PassengerViewModel",
                        "路線計算成功: ${directions.distanceText}, ${directions.durationText}, 車資: NT$ ${fare.totalFare}")
                }.onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isCalculatingRoute = false,
                        error = error.message ?: "路線計算失敗"
                    )
                    android.util.Log.e("PassengerViewModel", "路線計算失敗", error)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isCalculatingRoute = false,
                    error = e.message ?: "路線計算失敗"
                )
                android.util.Log.e("PassengerViewModel", "路線計算失敗", e)
            }
        }
    }

    /**
     * 清除路線資訊
     */
    fun clearRoute() {
        _uiState.value = _uiState.value.copy(
            routeInfo = null,
            fareEstimate = null
        )
    }

    /**
     * 計算司機到上車點的 ETA（預估到達時間）
     */
    fun calculateDriverETAs(drivers: List<NearbyDriver>, pickupLocation: LatLng) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCalculatingETA = true)

            try {
                val result = driverMatchingService.enrichDriversWithETA(drivers, pickupLocation)

                result.onSuccess { driversWithETA ->
                    _uiState.value = _uiState.value.copy(
                        driversWithETA = driversWithETA,
                        isCalculatingETA = false
                    )

                    if (driversWithETA.isNotEmpty()) {
                        val nearest = driversWithETA.first()
                        android.util.Log.d("PassengerViewModel",
                            "最近司機: ${nearest.driver.driverName}, ETA: ${nearest.etaText}, 距離: ${nearest.distanceText}")
                    }
                }.onFailure { error ->
                    _uiState.value = _uiState.value.copy(isCalculatingETA = false)
                    android.util.Log.e("PassengerViewModel", "計算司機 ETA 失敗", error)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isCalculatingETA = false)
                android.util.Log.e("PassengerViewModel", "計算司機 ETA 失敗", e)
            }
        }
    }

    /**
     * 找出最佳司機（最快到達）
     */
    fun findBestDriver(): DriverWithETA? {
        return _uiState.value.driversWithETA.firstOrNull()
    }

    /**
     * 啟動混合定位（GPS + Geolocation API）
     */
    fun startLocationUpdates() {
        viewModelScope.launch {
            hybridLocationService.startLocationUpdates()
        }
    }

    /**
     * 強制使用 Geolocation API（例如在室內）
     */
    fun forceGeolocation() {
        viewModelScope.launch {
            hybridLocationService.forceGeolocation()
        }
    }

    /**
     * 停止定位更新
     */
    fun stopLocationUpdates() {
        hybridLocationService.stopLocationUpdates()
    }

    // ==================== 對話泡泡相關 ====================

    /**
     * 讓小橘說話並顯示對話泡泡
     * @param message 要說的話
     * @param showBubble 是否顯示對話泡泡（預設顯示）
     */
    fun speakWithBubble(message: String, showBubble: Boolean = true) {
        if (showBubble) {
            _uiState.value = _uiState.value.copy(
                currentSpeechText = message,
                isSpeaking = true
            )
        }

        voiceAssistant.speakWithCallback(
            message = message,
            onComplete = {
                viewModelScope.launch {
                    // 延遲一下再隱藏，讓用戶有時間看完
                    kotlinx.coroutines.delay(500)
                    _uiState.value = _uiState.value.copy(
                        currentSpeechText = null,
                        isSpeaking = false
                    )
                }
            }
        )
    }

    /**
     * 讓小橘說話並顯示對話泡泡，播放完成後執行回調
     */
    fun speakWithBubbleAndCallback(
        message: String,
        showBubble: Boolean = true,
        onComplete: (() -> Unit)? = null
    ) {
        if (showBubble) {
            _uiState.value = _uiState.value.copy(
                currentSpeechText = message,
                isSpeaking = true
            )
        }

        voiceAssistant.speakWithCallback(
            message = message,
            onComplete = {
                viewModelScope.launch {
                    kotlinx.coroutines.delay(500)
                    _uiState.value = _uiState.value.copy(
                        currentSpeechText = null,
                        isSpeaking = false
                    )
                    onComplete?.invoke()
                }
            }
        )
    }

    /**
     * 隱藏對話泡泡
     */
    fun hideSpeechBubble() {
        _uiState.value = _uiState.value.copy(
            currentSpeechText = null,
            isSpeaking = false
        )
    }

    /**
     * 連接 WebSocket（乘客端）
     */
    fun connectWebSocket(passengerId: String) {
        viewModelScope.launch {
            webSocketManager.connectAsPassenger(passengerId)
        }
    }

    /**
     * 斷開 WebSocket 連接
     */
    fun disconnectWebSocket() {
        viewModelScope.launch {
            webSocketManager.disconnect()
        }
    }

    /**
     * 乘客對司機評分
     * @param passengerId 乘客ID
     * @param orderId 訂單ID
     * @param driverId 司機ID
     * @param rating 評分 (1-5)
     * @param comment 評語（可選）
     * @param onSuccess 成功回調
     * @param onError 錯誤回調
     */
    fun submitDriverRating(
        passengerId: String,
        orderId: String,
        driverId: String,
        rating: Int,
        comment: String?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                android.util.Log.d("PassengerViewModel", "========== 提交司機評價 ==========")
                android.util.Log.d("PassengerViewModel", "訂單ID: $orderId")
                android.util.Log.d("PassengerViewModel", "乘客ID: $passengerId")
                android.util.Log.d("PassengerViewModel", "司機ID: $driverId")
                android.util.Log.d("PassengerViewModel", "評分: $rating")
                android.util.Log.d("PassengerViewModel", "評語: ${comment ?: "無"}")

                val request = SubmitRatingRequest(
                    orderId = orderId,
                    fromType = "passenger",
                    fromId = passengerId,
                    toType = "driver",
                    toId = driverId,
                    rating = rating,
                    comment = comment
                )

                val response = RetrofitClient.passengerApiService.submitRating(request)

                if (response.isSuccessful && response.body()?.success == true) {
                    android.util.Log.d("PassengerViewModel", "✅ 評價提交成功")
                    onSuccess()
                } else {
                    val errorMsg = response.body()?.error ?: response.message() ?: "評價提交失敗"
                    android.util.Log.e("PassengerViewModel", "❌ 評價提交失敗: $errorMsg")
                    onError(errorMsg)
                }
            } catch (e: Exception) {
                android.util.Log.e("PassengerViewModel", "❌ 評價提交異常", e)
                onError(e.message ?: "網路錯誤")
            }
        }
    }

    // ==================== 語音助理功能 ====================

    /**
     * 設置乘客 ID（用於語音上下文）
     */
    fun setPassengerId(passengerId: String) {
        currentPassengerId = passengerId
    }

    /**
     * 初始化語音指令處理器回調
     */
    private fun setupVoiceCommandHandler() {
        voiceCommandHandler.setCallback(object : PassengerVoiceCommandHandler.CommandCallback {
            override fun onBookRide(destinationQuery: String) {
                // 注意：不再設置 pendingDestinationQuery
                // 由 handleVoiceCommandResult 處理，直接走自動流程
                android.util.Log.d(TAG, "語音叫車回調: 目的地=$destinationQuery（自動流程處理）")
            }

            override fun onSetDestination(destinationQuery: String) {
                // 注意：不再設置 pendingDestinationQuery
                // 由 handleVoiceCommandResult 處理，直接走自動流程
                android.util.Log.d(TAG, "語音設置目的地回調: $destinationQuery（自動流程處理）")
            }

            override fun onSetPickup(pickupQuery: String) {
                // 上車點仍使用舊流程（彈出對話框）
                android.util.Log.d(TAG, "語音設置上車點: $pickupQuery")
                _uiState.value = _uiState.value.copy(
                    pendingPickupQuery = pickupQuery
                )
            }

            override fun onCancelOrder() {
                android.util.Log.d(TAG, "語音取消訂單")
                cancelOrder(currentPassengerId)
            }

            override fun onCallDriver() {
                android.util.Log.d(TAG, "語音聯絡司機")
                // 由 UI 層處理撥打電話
            }

            override fun onCheckStatus() {
                android.util.Log.d(TAG, "語音查詢狀態")
                // 狀態已由 voiceCommandHandler 播報
            }

            override fun hasActiveOrder(): Boolean {
                return _uiState.value.currentOrder != null &&
                        _uiState.value.orderStatus != OrderStatus.IDLE &&
                        _uiState.value.orderStatus != OrderStatus.COMPLETED &&
                        _uiState.value.orderStatus != OrderStatus.CANCELLED
            }

            override fun getOrderStatus(): String? {
                return _uiState.value.currentOrder?.statusString
            }

            override fun getDriverPhone(): String? {
                return _uiState.value.currentOrder?.driverPhone
            }

            override fun getDriverName(): String? {
                return _uiState.value.currentOrder?.driverName
            }
        })
    }

    /**
     * 開始語音錄製
     */
    fun startVoiceRecording() {
        android.util.Log.d(TAG, "開始語音錄製")
        voiceRecorderService.startRecording { audioFile ->
            android.util.Log.d(TAG, "錄音完成，開始上傳: ${audioFile.absolutePath}")
            uploadAndProcessAudio(audioFile)
        }
    }

    /**
     * 停止語音錄製（手動）
     */
    fun stopVoiceRecording() {
        android.util.Log.d(TAG, "手動停止錄音")
        voiceRecorderService.stopRecording()
    }

    /**
     * 取消語音錄製
     */
    fun cancelVoiceRecording() {
        android.util.Log.d(TAG, "取消語音錄製")
        voiceRecorderService.cancelRecording()
    }

    /**
     * 上傳音檔並處理
     */
    private fun uploadAndProcessAudio(audioFile: File) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    voiceRecordingStatus = VoiceRecordingStatus.PROCESSING
                )

                val state = _uiState.value
                val hasActiveOrder = state.currentOrder != null &&
                        state.orderStatus != OrderStatus.IDLE &&
                        state.orderStatus != OrderStatus.COMPLETED &&
                        state.orderStatus != OrderStatus.CANCELLED

                // 準備 multipart 請求
                val audioPart = MultipartBody.Part.createFormData(
                    "audio",
                    audioFile.name,
                    audioFile.asRequestBody("audio/m4a".toMediaTypeOrNull())
                )

                val passengerIdPart = currentPassengerId
                    .toRequestBody("text/plain".toMediaTypeOrNull())
                val hasActiveOrderPart = hasActiveOrder.toString()
                    .toRequestBody("text/plain".toMediaTypeOrNull())
                val orderStatusPart = state.currentOrder?.statusString
                    ?.toRequestBody("text/plain".toMediaTypeOrNull())
                val pickupAddressPart = state.pickupAddress.takeIf { it.isNotEmpty() }
                    ?.toRequestBody("text/plain".toMediaTypeOrNull())
                val destAddressPart = state.destinationAddress.takeIf { it.isNotEmpty() }
                    ?.toRequestBody("text/plain".toMediaTypeOrNull())
                val driverNamePart = state.currentOrder?.driverName
                    ?.toRequestBody("text/plain".toMediaTypeOrNull())
                val driverPhonePart = state.currentOrder?.driverPhone
                    ?.toRequestBody("text/plain".toMediaTypeOrNull())

                android.util.Log.d(TAG, "上傳音檔到服務器...")

                val response = RetrofitClient.passengerApiService.transcribePassengerAudio(
                    audio = audioPart,
                    passengerId = passengerIdPart,
                    hasActiveOrder = hasActiveOrderPart,
                    orderStatus = orderStatusPart,
                    currentPickupAddress = pickupAddressPart,
                    currentDestinationAddress = destAddressPart,
                    driverName = driverNamePart,
                    driverPhone = driverPhonePart
                )

                if (response.isSuccessful) {
                    val result = response.body()
                    if (result?.success == true && result.command != null) {
                        android.util.Log.d(TAG, "語音解析成功: ${result.command.action}")
                        android.util.Log.d(TAG, "轉錄內容: ${result.command.transcription}")

                        _uiState.value = _uiState.value.copy(
                            lastVoiceCommand = result.command,
                            voiceRecordingStatus = VoiceRecordingStatus.IDLE,
                            voiceError = null
                        )

                        // 處理語音指令
                        val commandResult = voiceCommandHandler.handleCommand(result.command)
                        handleVoiceCommandResult(commandResult)
                    } else {
                        val errorMsg = result?.error ?: "語音解析失敗"
                        android.util.Log.e(TAG, "語音解析失敗: $errorMsg")
                        speakWithBubble("抱歉，$errorMsg")
                        _uiState.value = _uiState.value.copy(
                            voiceRecordingStatus = VoiceRecordingStatus.IDLE,
                            voiceError = errorMsg
                        )
                    }
                } else {
                    val errorMsg = "服務器錯誤: ${response.code()}"
                    android.util.Log.e(TAG, errorMsg)
                    speakWithBubble("抱歉，語音服務暫時不可用")
                    _uiState.value = _uiState.value.copy(
                        voiceRecordingStatus = VoiceRecordingStatus.IDLE,
                        voiceError = errorMsg
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "語音處理異常", e)
                speakWithBubble("抱歉，網路連接失敗，請稍後再試")
                _uiState.value = _uiState.value.copy(
                    voiceRecordingStatus = VoiceRecordingStatus.IDLE,
                    voiceError = e.message ?: "未知錯誤"
                )
            } finally {
                // 清理臨時檔案
                if (audioFile.exists()) {
                    audioFile.delete()
                }
                voiceRecorderService.resetState()
            }
        }
    }

    /**
     * 處理語音指令結果
     */
    private fun handleVoiceCommandResult(result: PassengerVoiceCommandHandler.CommandResult) {
        when (result) {
            is PassengerVoiceCommandHandler.CommandResult.BookRide -> {
                android.util.Log.d(TAG, "語音指令結果: 叫車到 ${result.destinationQuery}")
                // 語音自動流程：自動搜尋並選擇目的地
                searchAndAutoSelectDestination(result.destinationQuery)
            }
            is PassengerVoiceCommandHandler.CommandResult.SetDestination -> {
                android.util.Log.d(TAG, "語音指令結果: 設置目的地 ${result.destinationQuery}")
                // 也觸發自動搜尋（與 BookRide 相同行為）
                searchAndAutoSelectDestination(result.destinationQuery)
            }
            is PassengerVoiceCommandHandler.CommandResult.SetPickup -> {
                android.util.Log.d(TAG, "語音指令結果: 設置上車點 ${result.pickupQuery}")
                // 上車點暫不支援語音自動選擇，仍由 UI 處理
            }
            is PassengerVoiceCommandHandler.CommandResult.CancelOrder -> {
                android.util.Log.d(TAG, "語音指令結果: 取消訂單")
            }
            is PassengerVoiceCommandHandler.CommandResult.CallDriver -> {
                android.util.Log.d(TAG, "語音指令結果: 聯絡司機 ${result.phoneNumber}")
            }
            is PassengerVoiceCommandHandler.CommandResult.CheckStatus -> {
                android.util.Log.d(TAG, "語音指令結果: 查詢狀態")
            }
            is PassengerVoiceCommandHandler.CommandResult.NeedConfirmation -> {
                android.util.Log.d(TAG, "語音指令需要確認: ${result.message}")
            }
            is PassengerVoiceCommandHandler.CommandResult.Error -> {
                android.util.Log.e(TAG, "語音指令錯誤: ${result.message}")
            }
            is PassengerVoiceCommandHandler.CommandResult.NeedDestination -> {
                // 缺少目的地，播放提示並自動開始錄音等待用戶說出目的地
                android.util.Log.d(TAG, "需要目的地，準備自動錄音: ${result.message}")
                speakWithBubbleAndCallback(
                    message = result.message,
                    onComplete = {
                        android.util.Log.d(TAG, "提示播放完成，自動開始錄音監聽目的地")
                        startVoiceRecording()
                    }
                )
            }
            PassengerVoiceCommandHandler.CommandResult.Ignored -> {
                android.util.Log.d(TAG, "語音指令被忽略（信心度過低）")
            }
            // 語音自動流程 - 確認/拒絕
            PassengerVoiceCommandHandler.CommandResult.Confirmed -> {
                android.util.Log.d(TAG, "用戶確認")
                // 根據當前狀態執行對應動作
                when (_uiState.value.voiceAutoflowState) {
                    VoiceAutoflowState.CONFIRMING_DESTINATION -> confirmDestination()
                    else -> android.util.Log.d(TAG, "非確認目的地狀態，忽略確認指令")
                }
            }
            PassengerVoiceCommandHandler.CommandResult.Rejected -> {
                android.util.Log.d(TAG, "用戶拒絕")
                // 根據當前狀態執行對應動作
                when (_uiState.value.voiceAutoflowState) {
                    VoiceAutoflowState.CONFIRMING_DESTINATION -> rejectDestination()
                    VoiceAutoflowState.SELECTING_PICKUP -> cancelVoiceAutoflow()
                    else -> android.util.Log.d(TAG, "非等待確認狀態，忽略拒絕指令")
                }
            }
        }
    }

    /**
     * 清除待處理的語音查詢
     */
    fun clearPendingVoiceQuery() {
        _uiState.value = _uiState.value.copy(
            pendingDestinationQuery = null,
            pendingPickupQuery = null
        )
    }

    /**
     * 清除語音錯誤
     */
    fun clearVoiceError() {
        _uiState.value = _uiState.value.copy(voiceError = null)
    }

    /**
     * 播報歡迎訊息（首次打開語音時）
     */
    fun speakWelcome() {
        voiceCommandHandler.announceWelcome()
    }

    /**
     * 播報訂單狀態變更
     */
    fun announceOrderStatusChange(status: String, driverName: String? = null) {
        voiceCommandHandler.announceOrderUpdate(status, driverName)
    }

    // ==================== 語音自動流程 ====================

    /**
     * 語音自動流程：搜尋目的地並自動選擇第一個結果
     * 用戶說「去中原大學」後觸發
     */
    fun searchAndAutoSelectDestination(destinationQuery: String) {
        android.util.Log.d(TAG, "語音自動流程：搜尋目的地「$destinationQuery」")

        _uiState.value = _uiState.value.copy(
            voiceAutoflowState = VoiceAutoflowState.SEARCHING_DESTINATION
        )

        viewModelScope.launch {
            try {
                // 使用 PlacesApiService 搜尋地點
                val currentLocation = _uiState.value.currentLocation
                val result = placesApiService.searchPlaces(
                    query = destinationQuery,
                    bias = currentLocation
                )

                result.onSuccess { predictions ->
                    if (predictions.isEmpty()) {
                        android.util.Log.w(TAG, "搜尋無結果")
                        speakWithBubble("抱歉，找不到「$destinationQuery」，請重新說出目的地")
                        _uiState.value = _uiState.value.copy(
                            voiceAutoflowState = VoiceAutoflowState.IDLE
                        )
                        return@launch
                    }

                    // 自動選擇第一個結果
                    val firstPrediction = predictions.first()
                    android.util.Log.d(TAG, "自動選擇: ${firstPrediction.primaryText}")

                    // 獲取詳細資訊（座標）
                    val detailsResult = placesApiService.getPlaceDetails(firstPrediction.placeId)
                    detailsResult.onSuccess { details ->
                        setPendingDestination(details)
                    }.onFailure { error ->
                        android.util.Log.e(TAG, "獲取地點詳情失敗", error)
                        speakWithBubble("抱歉，無法獲取地點資訊，請再試一次")
                        _uiState.value = _uiState.value.copy(
                            voiceAutoflowState = VoiceAutoflowState.IDLE
                        )
                    }
                }.onFailure { error ->
                    android.util.Log.e(TAG, "搜尋地點失敗", error)
                    speakWithBubble("抱歉，搜尋失敗，請再試一次")
                    _uiState.value = _uiState.value.copy(
                        voiceAutoflowState = VoiceAutoflowState.IDLE
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "搜尋異常", e)
                speakWithBubble("抱歉，發生錯誤，請再試一次")
                _uiState.value = _uiState.value.copy(
                    voiceAutoflowState = VoiceAutoflowState.IDLE
                )
            }
        }
    }

    /**
     * 語音自動流程：設置待確認的目的地
     */
    private fun setPendingDestination(details: PlaceDetails) {
        android.util.Log.d(TAG, "設置待確認目的地: ${details.name}")

        _uiState.value = _uiState.value.copy(
            pendingDestinationDetails = details,
            voiceAutoflowState = VoiceAutoflowState.CONFIRMING_DESTINATION
        )

        // 語音詢問確認，播報完成後自動開始錄音
        speakWithBubbleAndCallback(
            message = "您是要去「${details.name}」嗎？請說「對」確認，或說「不對」重選",
            onComplete = {
                // 語音播報完成後，自動開始錄音監聽用戶回應
                android.util.Log.d(TAG, "語音詢問完成，自動開始錄音監聽回應")
                // 確保還在確認狀態才開始錄音
                if (_uiState.value.voiceAutoflowState == VoiceAutoflowState.CONFIRMING_DESTINATION) {
                    startVoiceRecording()
                }
            }
        )
    }

    /**
     * 語音自動流程：用戶確認目的地
     */
    fun confirmDestination() {
        // 立即停止當前播放的語音
        voiceAssistant.stop()

        val details = _uiState.value.pendingDestinationDetails ?: run {
            android.util.Log.w(TAG, "沒有待確認的目的地")
            return
        }

        android.util.Log.d(TAG, "用戶確認目的地: ${details.name}")

        details.latLng?.let { latLng ->
            // 設置目的地
            setDestinationLocation(latLng, details.address)

            // 進入選擇上車點模式
            _uiState.value = _uiState.value.copy(
                voiceAutoflowState = VoiceAutoflowState.SELECTING_PICKUP,
                showPickupMapSelector = true,
                pendingDestinationDetails = null
            )

            speakWithBubble("好的，請在地圖上選擇上車地點")
        } ?: run {
            android.util.Log.e(TAG, "目的地缺少座標")
            speakWithBubble("抱歉，無法獲取目的地位置")
            _uiState.value = _uiState.value.copy(
                voiceAutoflowState = VoiceAutoflowState.IDLE,
                pendingDestinationDetails = null
            )
        }
    }

    /**
     * 語音自動流程：用戶拒絕，重新搜尋
     */
    fun rejectDestination() {
        // 立即停止當前播放的語音
        voiceAssistant.stop()

        android.util.Log.d(TAG, "用戶拒絕目的地，重新搜尋")

        _uiState.value = _uiState.value.copy(
            voiceAutoflowState = VoiceAutoflowState.IDLE,
            pendingDestinationDetails = null
        )

        // 語音提示後自動開始錄音
        speakWithBubbleAndCallback(
            message = "好的，請重新說出您要去的地方",
            onComplete = {
                android.util.Log.d(TAG, "拒絕提示完成，自動開始錄音監聽新目的地")
                startVoiceRecording()
            }
        )
    }

    /**
     * 語音自動流程：確認上車點並自動叫車
     * @param pickupLatLng 上車點座標
     * @param pickupAddress 上車點地址
     * @param passengerId 乘客 ID
     * @param passengerName 乘客姓名
     * @param passengerPhone 乘客電話
     */
    fun confirmPickupAndBook(
        pickupLatLng: LatLng,
        pickupAddress: String,
        passengerId: String,
        passengerName: String,
        passengerPhone: String
    ) {
        android.util.Log.d(TAG, "========== confirmPickupAndBook 被調用 ==========")
        android.util.Log.d(TAG, "上車點座標: (${pickupLatLng.latitude}, ${pickupLatLng.longitude})")
        android.util.Log.d(TAG, "上車點地址: $pickupAddress")
        android.util.Log.d(TAG, "乘客資訊: ID=$passengerId, 姓名=$passengerName, 電話=$passengerPhone")

        // 設置上車點
        setPickupLocation(pickupLatLng, pickupAddress)
        android.util.Log.d(TAG, "✅ 已設置上車點")

        // 更新狀態
        _uiState.value = _uiState.value.copy(
            voiceAutoflowState = VoiceAutoflowState.BOOKING,
            showPickupMapSelector = false
        )
        android.util.Log.d(TAG, "✅ 已更新狀態為 BOOKING，關閉地圖選擇器")

        // 語音播報
        val destAddress = _uiState.value.destinationAddress
        android.util.Log.d(TAG, "目的地地址: $destAddress")
        speakWithBubble("正在為您叫車前往$destAddress")

        // 檢查當前狀態
        val currentState = _uiState.value
        android.util.Log.d(TAG, "當前狀態檢查:")
        android.util.Log.d(TAG, "  - pickupLocation: ${currentState.pickupLocation}")
        android.util.Log.d(TAG, "  - pickupAddress: ${currentState.pickupAddress}")
        android.util.Log.d(TAG, "  - destinationLocation: ${currentState.destinationLocation}")
        android.util.Log.d(TAG, "  - destinationAddress: ${currentState.destinationAddress}")

        // 自動叫車
        android.util.Log.d(TAG, "📡 準備調用 requestTaxi...")
        requestTaxi(passengerId, passengerName, passengerPhone)
        android.util.Log.d(TAG, "✅ requestTaxi 已調用")
    }

    /**
     * 語音自動流程：取消（任何階段都可以）
     */
    fun cancelVoiceAutoflow() {
        android.util.Log.d(TAG, "取消語音自動流程")

        _uiState.value = _uiState.value.copy(
            voiceAutoflowState = VoiceAutoflowState.IDLE,
            pendingDestinationDetails = null,
            showPickupMapSelector = false
        )

        speakWithBubble("已取消")
    }

    /**
     * 關閉上車點選擇器（不叫車）
     */
    fun dismissPickupMapSelector() {
        _uiState.value = _uiState.value.copy(
            showPickupMapSelector = false,
            voiceAutoflowState = VoiceAutoflowState.IDLE
        )
    }

    override fun onCleared() {
        super.onCleared()
        hybridLocationService.stopLocationUpdates()
        voiceRecorderService.release()
        voiceAssistant.release()
        viewModelScope.launch {
            webSocketManager.disconnect()
        }
    }
}
