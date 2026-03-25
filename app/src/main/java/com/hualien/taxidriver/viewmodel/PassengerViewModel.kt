package com.hualien.taxidriver.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.hualien.taxidriver.data.remote.RetrofitClient
import com.hualien.taxidriver.data.remote.WebSocketManager
import com.hualien.taxidriver.data.remote.dto.PassengerVoiceCommand
import com.hualien.taxidriver.data.remote.dto.SubmitRatingRequest
import com.hualien.taxidriver.data.remote.dto.VoiceChatMessage
import com.hualien.taxidriver.data.remote.dto.VoiceChatState
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
import com.hualien.taxidriver.utils.GeocodingUtils
import com.hualien.taxidriver.utils.FareResult
import com.hualien.taxidriver.utils.PassengerNotificationHelper
import com.hualien.taxidriver.utils.PassengerVoiceCommandHandler
import com.hualien.taxidriver.utils.VoiceAssistant
import com.hualien.taxidriver.utils.VoiceChatManager
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
    ASKING_TRAIN_TIME,         // 詢問火車時間（火車站專用）
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
    val originalVoiceQuery: String? = null,                 // 語音原始查詢（用於確認時對比）
    val showPickupMapSelector: Boolean = false,             // 顯示 Uber 風格地圖選點

    // 語音狀態
    val currentSpeechText: String? = null,                  // 當前語音文字（顯示在畫面上）
    val isSpeaking: Boolean = false,                        // 是否正在播放語音

    // 司機 ETA 追蹤（用於語音廣播）
    val driverToPickupDistanceMeters: Float? = null,        // 司機到上車點的距離（公尺）
    val driverToPickupEtaMinutes: Int? = null,              // 司機到上車點的預估時間（分鐘）
    val initialDriverDistance: Float? = null,               // 司機接單時的初始距離
    val etaAnnouncedInitial: Boolean = false,               // 已廣播：初始 ETA
    val etaAnnouncedHalfway: Boolean = false,               // 已廣播：一半距離
    val etaAnnouncedOneKm: Boolean = false,                 // 已廣播：剩餘 1 公里

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

    // 語音對講管理器
    private val voiceChatManager = VoiceChatManager(
        context = application,
        voiceAssistant = voiceAssistant,
        voiceRecorderService = voiceRecorderService,
        webSocketManager = webSocketManager
    )

    // 語音對講狀態（暴露給 UI）
    val voiceChatHistory: StateFlow<List<VoiceChatMessage>> = voiceChatManager.chatHistory
    val voiceChatState: StateFlow<VoiceChatState> = voiceChatManager.state
    val voiceChatRecording: StateFlow<Boolean> = voiceChatManager.isRecording
    val voiceChatAmplitude: StateFlow<Int> = voiceChatManager.amplitude

    // 語音對講面板顯示狀態
    private val _showVoiceChatPanel = MutableStateFlow(false)
    val showVoiceChatPanel: StateFlow<Boolean> = _showVoiceChatPanel.asStateFlow()

    // 語音對講未讀訊息數
    private val _voiceChatUnreadCount = MutableStateFlow(0)
    val voiceChatUnreadCount: StateFlow<Int> = _voiceChatUnreadCount.asStateFlow()

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

        // 監聯司機實時位置（實時更新地圖標記 + ETA 廣播）
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

                    // 【ETA 廣播】如果有進行中的訂單，計算司機到上車點的距離
                    val orderStatus = _uiState.value.orderStatus
                    val currentOrder = _uiState.value.currentOrder
                    val pickup = _uiState.value.pickupLocation

                    if (orderStatus in listOf(OrderStatus.ACCEPTED, OrderStatus.DRIVER_ARRIVING) &&
                        currentOrder != null &&
                        pickup != null &&
                        currentOrder.driverId == location.driverId) {

                        // 優先使用後端計算的距離和 ETA，否則本地計算
                        val distanceMeters: Float
                        val etaMinutes: Int

                        if (location.distanceToPickup != null && location.etaMinutes != null) {
                            // 使用後端傳來的精確數據
                            distanceMeters = location.distanceToPickup.toFloat()
                            etaMinutes = location.etaMinutes
                            android.util.Log.d(TAG, "使用後端 ETA: 距離=${distanceMeters}m, ETA=${etaMinutes}分")
                        } else {
                            // 本地計算（備援）
                            val driverLatLng = LatLng(location.latitude, location.longitude)
                            distanceMeters = calculateDistanceMeters(driverLatLng, pickup)
                            etaMinutes = (distanceMeters / 500).toInt().coerceAtLeast(1)
                            android.util.Log.d(TAG, "本地計算 ETA: 距離=${distanceMeters}m, ETA=${etaMinutes}分")
                        }

                        // 更新狀態
                        _uiState.value = _uiState.value.copy(
                            driverToPickupDistanceMeters = distanceMeters,
                            driverToPickupEtaMinutes = etaMinutes
                        )

                        // 檢查是否需要廣播
                        checkAndAnnounceETA(distanceMeters, etaMinutes)
                    }
                }
            }
        }

        // 監聽語音對講訊息
        viewModelScope.launch {
            webSocketManager.voiceChatMessage.collect { message ->
                message?.let {
                    android.util.Log.d(TAG, "收到語音對講訊息: ${it.senderName}說「${it.messageText}」")

                    // 交給 VoiceChatManager 處理（播報 + 添加到記錄）
                    voiceChatManager.receiveMessage(it)

                    // 如果對講面板未開啟，增加未讀計數
                    if (!_showVoiceChatPanel.value) {
                        _voiceChatUnreadCount.value = _voiceChatUnreadCount.value + 1
                    }

                    // 清除 WebSocket 的訊息狀態
                    webSocketManager.clearVoiceChatMessage()
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
                // 傳遞 Directions API 計算的道路距離和車資給後端
                // 確保司機端和乘客端顯示一致的車資
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
                    paymentType = "CASH",
                    tripDistanceMeters = state.routeInfo?.distanceMeters,
                    estimatedFare = state.fareEstimate?.totalFare
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
        android.util.Log.d("PassengerViewModel", "========== 取消訂單 ==========")
        android.util.Log.d("PassengerViewModel", "乘客ID: $passengerId")

        viewModelScope.launch {
            val currentState = _uiState.value
            val orderId = currentState.currentOrder?.orderId

            android.util.Log.d("PassengerViewModel", "訂單ID: $orderId")
            android.util.Log.d("PassengerViewModel", "當前訂單狀態: ${currentState.orderStatus}")

            if (orderId.isNullOrEmpty()) {
                android.util.Log.e("PassengerViewModel", "❌ 取消失敗：沒有訂單ID (orderId=$orderId)")
                _uiState.value = currentState.copy(error = "沒有可取消的訂單")
                return@launch
            }

            // 防止重複點擊
            if (currentState.isLoading) {
                android.util.Log.w("PassengerViewModel", "⚠️ 已在處理中，忽略重複點擊")
                return@launch
            }

            _uiState.value = _uiState.value.copy(isLoading = true)
            android.util.Log.d("PassengerViewModel", "📡 發送取消訂單請求...")

            try {
                val result = passengerRepository.cancelOrder(
                    orderId = orderId,
                    passengerId = passengerId,
                    reason = "乘客取消"
                )

                result.onSuccess { message ->
                    android.util.Log.d("PassengerViewModel", "✅ 取消訂單成功: $message")
                    // 使用當前狀態 copy，避免覆蓋其他狀態變更
                    _uiState.value = _uiState.value.copy(
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
                    android.util.Log.e("PassengerViewModel", "❌ 取消訂單失敗: ${error.message}")

                    // 檢查是否是「訂單已取消」的錯誤（後端狀態已經是 CANCELLED）
                    // 這種情況下應該清除前端訂單狀態，避免 UI 卡住
                    val errorMsg = error.message ?: "取消失敗"
                    val isAlreadyCancelled = errorMsg.contains("CANCELLED") ||
                                             errorMsg.contains("無法取消") ||
                                             errorMsg.contains("不存在")

                    if (isAlreadyCancelled) {
                        android.util.Log.w("PassengerViewModel", "訂單已被取消或不存在，清除前端訂單狀態")
                        _uiState.value = _uiState.value.copy(
                            currentOrder = null,
                            orderStatus = OrderStatus.CANCELLED,
                            isLoading = false,
                            pickupLocation = null,
                            destinationLocation = null,
                            pickupAddress = "",
                            destinationAddress = "",
                            routeInfo = null,
                            fareEstimate = null,
                            error = null  // 不顯示錯誤，因為實際上已取消
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = errorMsg
                        )
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("PassengerViewModel", "❌ 取消訂單異常: ${e.message}", e)

                // 網路異常時也檢查是否是訂單已取消的情況
                val errorMsg = e.message ?: "取消失敗"
                val isAlreadyCancelled = errorMsg.contains("CANCELLED") ||
                                         errorMsg.contains("無法取消") ||
                                         errorMsg.contains("不存在")

                if (isAlreadyCancelled) {
                    _uiState.value = _uiState.value.copy(
                        currentOrder = null,
                        orderStatus = OrderStatus.CANCELLED,
                        isLoading = false,
                        error = null
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = errorMsg
                    )
                }
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

        // 檢查是否是新接單（觸發初始 ETA 廣播）
        val previousStatus = _uiState.value.orderStatus
        val isNewlyAccepted = previousStatus != OrderStatus.ACCEPTED && orderStatus == OrderStatus.ACCEPTED

        _uiState.value = _uiState.value.copy(
            currentOrder = order,
            orderStatus = orderStatus
        )

        // 檢查是否是新到達（觸發到達通知）
        val isNewlyArrived = previousStatus != OrderStatus.ARRIVED && orderStatus == OrderStatus.ARRIVED

        // 處理 ETA 廣播邏輯
        when (orderStatus) {
            OrderStatus.ACCEPTED -> {
                if (isNewlyAccepted) {
                    // 重置 ETA 追蹤
                    resetETATracking()
                    // 重置到達通知追蹤（新訂單開始）
                    PassengerNotificationHelper.resetArrivalTracking()

                    val driverName = order.driverName ?: "司機"

                    // 使用訂單中的 ETA 資訊（來自後端計算）
                    val etaMinutes = order.etaToPickup ?: order.getGoogleEtaMinutes()
                    val distanceKm = order.distanceToPickup

                    if (etaMinutes != null || distanceKm != null) {
                        // 有後端提供的 ETA，直接使用
                        val distanceMeters = ((distanceKm ?: 2.0) * 1000).toFloat()
                        val eta = etaMinutes ?: (distanceMeters / 500).toInt().coerceAtLeast(1)

                        _uiState.value = _uiState.value.copy(
                            initialDriverDistance = distanceMeters,
                            driverToPickupDistanceMeters = distanceMeters,
                            driverToPickupEtaMinutes = eta,
                            etaAnnouncedInitial = true
                        )

                        // 廣播初始 ETA
                        val message = if (eta <= 2) {
                            "${driverName}已接單，很快就到！"
                        } else {
                            "${driverName}已接單，預計 ${eta} 分鐘後到達"
                        }
                        announceDriverETA(message)
                        android.util.Log.d(TAG, "📢 ETA 廣播（初始）: $message")
                    } else {
                        // 沒有 ETA 資訊，嘗試從附近司機列表計算
                        val driverLocation = _uiState.value.nearbyDrivers
                            .find { it.driverId == order.driverId }
                            ?.location
                        val pickupLocation = _uiState.value.pickupLocation

                        setInitialDriverDistanceAndAnnounce(driverLocation, pickupLocation, driverName)
                    }
                }
            }
            OrderStatus.ARRIVED -> {
                if (isNewlyArrived) {
                    // 司機已到達上車點，發送通知
                    val driverName = order.driverName ?: "司機"

                    // 顯示系統通知（即使 App 在背景）
                    PassengerNotificationHelper.showDriverArrivedNotification(
                        context = getApplication(),
                        driverName = driverName,
                        orderId = order.orderId
                    )

                    // 同時語音播報
                    val message = "${driverName}已到達上車點，請準備上車"
                    announceDriverETA(message)

                    android.util.Log.d(TAG, "📢 司機到達通知已發送: $driverName")
                }
            }
            OrderStatus.COMPLETED, OrderStatus.CANCELLED -> {
                // 訂單結束，重置 ETA 追蹤
                resetETATracking()
            }
            else -> { /* 其他狀態不處理 */ }
        }

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

    // ==================== 語音播報相關 ====================

    // 追蹤是否已播放過歡迎語
    private var hasPlayedWelcome = false

    /**
     * 播放歡迎語音（只在首次進入時播放）
     */
    fun playWelcomeGreeting() {
        if (hasPlayedWelcome) {
            android.util.Log.d(TAG, "歡迎語已播放過，跳過")
            return
        }
        hasPlayedWelcome = true

        // 延遲一點播放，等 UI 完全載入
        viewModelScope.launch {
            kotlinx.coroutines.delay(500)
            speakWithBubbleAndCallback(
                message = "大豐你好，哪裡搭車呢？",
                onComplete = {
                    android.util.Log.d(TAG, "歡迎語播放完成，開始錄音")
                    startVoiceRecording()
                }
            )
        }
    }

    /**
     * 播放語音並顯示文字
     * @param message 要說的話
     * @param showBubble 是否顯示文字（預設顯示）
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
     * 播放語音並顯示文字，播放完成後執行回調
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
    fun connectWebSocket(
        passengerId: String,
        onRestoreActiveOrder: ((Boolean?) -> Unit)? = null
    ) {
        viewModelScope.launch {
            webSocketManager.connectAsPassenger(passengerId)

            // 連接成功後，獲取當前進行中的訂單（恢復訂單狀態）
            val hasActiveOrder = fetchActiveOrder(passengerId)
            onRestoreActiveOrder?.invoke(hasActiveOrder)
        }
    }

    /**
     * 獲取當前進行中的訂單（用於 APP 重開時恢復訂單狀態）
     */
    private suspend fun fetchActiveOrder(passengerId: String): Boolean? {
        android.util.Log.d(TAG, "========== 獲取活動訂單 ==========")
        android.util.Log.d(TAG, "乘客ID: $passengerId")

        return try {
            // 嘗試獲取各種進行中狀態的訂單
            val activeStatuses = listOf("WAITING", "ACCEPTED", "ARRIVED", "ON_TRIP", "SETTLING")

            for (status in activeStatuses) {
                val response = RetrofitClient.passengerApiService.getOrderHistory(
                    passengerId = passengerId,
                    status = status
                )

                if (response.isSuccessful) {
                    val orderHistory = response.body()
                    if (orderHistory?.success == true && orderHistory.orders.isNotEmpty()) {
                        val orderDto = orderHistory.orders.first()
                        android.util.Log.d(TAG, "✅ 找到活動訂單: ${orderDto.orderId}, 狀態: ${orderDto.status}")

                        // 轉換 OrderDto 到 UI 狀態
                        val orderStatus = when (orderDto.status) {
                            "WAITING" -> OrderStatus.WAITING
                            "ACCEPTED" -> OrderStatus.ACCEPTED
                            "ARRIVED" -> OrderStatus.ARRIVED
                            "ON_TRIP" -> OrderStatus.ON_TRIP
                            "SETTLING" -> OrderStatus.SETTLING
                            else -> OrderStatus.IDLE
                        }

                        // 轉換為 domain Order 模型
                        val order = Order(
                            orderId = orderDto.orderId,
                            passengerId = orderDto.passengerId ?: passengerId,
                            passengerName = orderDto.passengerName ?: "乘客",
                            passengerPhone = orderDto.passengerPhone,
                            driverId = orderDto.driverId,
                            driverName = orderDto.driverName,
                            driverPhone = orderDto.driverPhone,
                            pickup = com.hualien.taxidriver.domain.model.Location(
                                latitude = orderDto.pickup.lat,
                                longitude = orderDto.pickup.lng,
                                address = orderDto.pickup.address
                            ),
                            destination = orderDto.destination?.let { dest ->
                                com.hualien.taxidriver.domain.model.Location(
                                    latitude = dest.lat,
                                    longitude = dest.lng,
                                    address = dest.address
                                )
                            },
                            statusString = orderDto.status
                        )

                        _uiState.value = _uiState.value.copy(
                            currentOrder = order,
                            orderStatus = orderStatus
                        )
                        return true
                    }
                }
            }

            android.util.Log.d(TAG, "📭 無活動訂單")
            false
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ 獲取活動訂單失敗: ${e.message}", e)
            null
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

                        // 火車站特殊流程：直接用轉錄文字處理火車時間
                        if (_uiState.value.voiceAutoflowState == VoiceAutoflowState.ASKING_TRAIN_TIME) {
                            android.util.Log.d(TAG, "火車時間回覆: ${result.command.transcription}")
                            handleTrainTimeResponse(result.command.transcription)
                            return@launch
                        }

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
     * 偵測查詢是否為街道門牌地址（含 路/街/巷/弄/號）
     */
    private fun isStreetAddress(query: String): Boolean {
        return Regex("[路街巷弄號]").containsMatchIn(query)
    }

    /**
     * 從街道地址查詢中提取街道名稱（用於驗證結果）
     * 例如 "信豐街805" → "信豐街", "中山路100號" → "中山路"
     */
    private fun extractStreetName(query: String): String? {
        val match = Regex("([\\u4e00-\\u9fa5]+[路街巷弄])").find(query)
        return match?.groupValues?.get(1)
    }

    /**
     * 使用 Android Geocoder 解析街道地址
     * 自動加花蓮前綴，仿後端 PhoneCallProcessor.geocodeWithGeocodingAPI
     */
    private suspend fun resolveStreetAddress(query: String): PlaceDetails? {
        val townships = listOf("吉安","新城","壽豐","光復","豐濱","瑞穗","富里","秀林","萬榮","卓溪","玉里","鳳林")
        val prefix = when {
            query.startsWith("花蓮") -> ""
            townships.any { query.contains(it) } -> "花蓮縣"
            else -> "花蓮縣花蓮市"
        }
        val fullAddress = "$prefix$query"

        android.util.Log.d(TAG, "Geocoder 街道地址查詢: $fullAddress")

        val appContext = getApplication<android.app.Application>()
        val latLng = GeocodingUtils.getLocationFromAddress(appContext, fullAddress)
        if (latLng != null) {
            val formattedAddress = GeocodingUtils.getAddressFromLocation(appContext, latLng)
            android.util.Log.d(TAG, "Geocoder 成功: $formattedAddress ($latLng)")

            return PlaceDetails(
                placeId = "",
                name = query,
                address = formattedAddress,
                latLng = latLng,
                phoneNumber = null,
                types = listOf("street_address")
            )
        }

        android.util.Log.w(TAG, "Geocoder 無結果: $fullAddress")
        return null
    }

    /**
     * 語音自動流程：搜尋目的地
     * 街道地址 → Geocoder API，地標 → Places API
     */
    fun searchAndAutoSelectDestination(destinationQuery: String) {
        android.util.Log.d(TAG, "語音自動流程：搜尋目的地「$destinationQuery」")

        _uiState.value = _uiState.value.copy(
            voiceAutoflowState = VoiceAutoflowState.SEARCHING_DESTINATION,
            originalVoiceQuery = destinationQuery
        )

        viewModelScope.launch {
            try {
                if (isStreetAddress(destinationQuery)) {
                    // 街道地址 → 優先使用 Geocoder API
                    android.util.Log.d(TAG, "偵測為街道地址，使用 Geocoder")
                    val geocoderResult = resolveStreetAddress(destinationQuery)

                    if (geocoderResult != null) {
                        val streetName = extractStreetName(destinationQuery)
                        if (streetName != null && !geocoderResult.address.contains(streetName)) {
                            // Geocoder 結果街名不匹配，fallback 到 Places
                            android.util.Log.w(TAG, "Geocoder 結果不匹配: 查詢=$destinationQuery, 結果=${geocoderResult.address}")
                            searchWithPlacesApi(destinationQuery)
                        } else {
                            setPendingDestination(geocoderResult)
                        }
                    } else {
                        // Geocoder 失敗，fallback 到 Places
                        android.util.Log.d(TAG, "Geocoder 無結果，回退到 Places API")
                        searchWithPlacesApi(destinationQuery)
                    }
                } else {
                    // 地標 → Places API（原有邏輯）
                    android.util.Log.d(TAG, "偵測為地標名稱，使用 Places API")
                    searchWithPlacesApi(destinationQuery)
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
     * 使用 Places Autocomplete 搜尋地點（地標路徑 / Geocoder fallback）
     */
    private suspend fun searchWithPlacesApi(destinationQuery: String) {
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
                return
            }

            // 如果是街道地址查詢（Geocoder fallback），優先選包含街名的結果
            val streetName = extractStreetName(destinationQuery)
            val bestPrediction = if (streetName != null) {
                predictions.firstOrNull { it.fullText.contains(streetName) }
                    ?: predictions.first()
            } else {
                predictions.first()
            }

            android.util.Log.d(TAG, "自動選擇: ${bestPrediction.primaryText}")

            val detailsResult = placesApiService.getPlaceDetails(bestPrediction.placeId)
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
    }

    /**
     * 語音自動流程：設置待確認的目的地（含車資計算）
     */
    private fun setPendingDestination(details: PlaceDetails) {
        android.util.Log.d(TAG, "設置待確認目的地: ${details.name}")

        // 檢測是否為火車站
        val isTrainStation = details.name.contains("火車站") ||
                             details.name.contains("車站") ||
                             details.address.contains("火車站")

        if (isTrainStation) {
            // 火車站特殊流程：先詢問火車時間
            android.util.Log.d(TAG, "偵測到火車站目的地，詢問火車時間")
            _uiState.value = _uiState.value.copy(
                pendingDestinationDetails = details,
                voiceAutoflowState = VoiceAutoflowState.ASKING_TRAIN_TIME
            )
            askTrainTime()
            return
        }

        _uiState.value = _uiState.value.copy(
            pendingDestinationDetails = details,
            voiceAutoflowState = VoiceAutoflowState.CONFIRMING_DESTINATION
        )

        val currentLocation = _uiState.value.currentLocation
        val destLatLng = details.latLng

        // 如果有當前位置和目的地座標，計算路線和車資
        if (currentLocation != null && destLatLng != null) {
            viewModelScope.launch {
                try {
                    val result = directionsService.getDirections(currentLocation, destLatLng)
                    result.onSuccess { directions ->
                        // 計算車資
                        val fare = FareCalculator.calculateFare(directions.distanceMeters)
                        val distanceKm = String.format("%.1f", directions.distanceMeters / 1000.0)

                        _uiState.value = _uiState.value.copy(
                            routeInfo = directions,
                            fareEstimate = fare
                        )

                        android.util.Log.d(TAG, "路線計算成功: ${directions.distanceText}, 車資: NT$ ${fare.totalFare}")

                        // 語音詢問確認（含車資+派車時間），播報完成後自動開始錄音
                        val fareMessage = if (fare.isNightTime) {
                            "約 ${fare.totalFare} 元，含夜間加成"
                        } else {
                            "約 ${fare.totalFare} 元"
                        }

                        // 計算最近司機的預估到達時間
                        val etaMessage = getEstimatedPickupTime()

                        // 判斷解析結果是否與用戶原話不同，需明確提示差異
                        val originalQuery = _uiState.value.originalVoiceQuery
                        val resolvedName = details.name
                        val needsExplicitConfirm = originalQuery != null
                                && resolvedName != originalQuery
                                && !resolvedName.contains(originalQuery)
                                && !originalQuery.contains(resolvedName)

                        val confirmMessage = if (needsExplicitConfirm) {
                            "您說的是「$originalQuery」，找到的是「$resolvedName」，$fareMessage，$etaMessage，對嗎？"
                        } else {
                            "去$resolvedName，$fareMessage，$etaMessage，可以等嗎？"
                        }

                        speakWithBubbleAndCallback(
                            message = confirmMessage,
                            onComplete = {
                                android.util.Log.d(TAG, "語音詢問完成，自動開始錄音監聽回應")
                                if (_uiState.value.voiceAutoflowState == VoiceAutoflowState.CONFIRMING_DESTINATION) {
                                    startVoiceRecording()
                                }
                            }
                        )
                    }.onFailure { error ->
                        android.util.Log.w(TAG, "路線計算失敗，使用簡單確認: ${error.message}")
                        // 路線計算失敗，仍然詢問確認但不含車資
                        speakSimpleConfirmation(details)
                    }
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "路線計算異常，使用簡單確認: ${e.message}")
                    speakSimpleConfirmation(details)
                }
            }
        } else {
            // 沒有當前位置，使用簡單確認
            android.util.Log.w(TAG, "無法計算車資（缺少位置資訊），使用簡單確認")
            speakSimpleConfirmation(details)
        }
    }

    /**
     * 簡單確認目的地（不含車資）- 備援方案
     */
    private fun speakSimpleConfirmation(details: PlaceDetails) {
        val etaMessage = getEstimatedPickupTime()
        val originalQuery = _uiState.value.originalVoiceQuery
        val resolvedName = details.name
        val needsExplicitConfirm = originalQuery != null
                && resolvedName != originalQuery
                && !resolvedName.contains(originalQuery)
                && !originalQuery.contains(resolvedName)

        val confirmMessage = if (needsExplicitConfirm) {
            "您說的是「$originalQuery」，找到的是「$resolvedName」，$etaMessage，對嗎？"
        } else {
            "去$resolvedName，$etaMessage，可以等嗎？"
        }
        speakWithBubbleAndCallback(
            message = confirmMessage,
            onComplete = {
                android.util.Log.d(TAG, "語音詢問完成，自動開始錄音監聽回應")
                if (_uiState.value.voiceAutoflowState == VoiceAutoflowState.CONFIRMING_DESTINATION) {
                    startVoiceRecording()
                }
            }
        )
    }

    /**
     * 估算最近司機的到達時間
     */
    private fun getEstimatedPickupTime(): String {
        val currentLocation = _uiState.value.currentLocation ?: return "派車約 3 分鐘"
        val nearbyDrivers = _uiState.value.nearbyDrivers

        if (nearbyDrivers.isEmpty()) {
            return "派車約 3 分鐘"
        }

        // 找最近的司機
        val nearestDriver = nearbyDrivers.minByOrNull { driver ->
            val results = FloatArray(1)
            android.location.Location.distanceBetween(
                currentLocation.latitude, currentLocation.longitude,
                driver.location.latitude, driver.location.longitude,
                results
            )
            results[0]
        }

        nearestDriver?.let { driver ->
            val results = FloatArray(1)
            android.location.Location.distanceBetween(
                currentLocation.latitude, currentLocation.longitude,
                driver.location.latitude, driver.location.longitude,
                results
            )
            val distanceMeters = results[0]
            // 假設平均車速 30 km/h = 500m/分鐘
            val etaMinutes = (distanceMeters / 500).toInt().coerceAtLeast(1)
            return "派車約 $etaMinutes 分鐘"
        }

        return "派車約 3 分鐘"
    }

    /**
     * 火車站專用：詢問火車時間
     */
    private fun askTrainTime() {
        speakWithBubbleAndCallback(
            message = "趕火車是幾點的？",
            onComplete = {
                android.util.Log.d(TAG, "詢問火車時間完成，開始錄音")
                if (_uiState.value.voiceAutoflowState == VoiceAutoflowState.ASKING_TRAIN_TIME) {
                    startVoiceRecording()
                }
            }
        )
    }

    /**
     * 火車站專用：處理火車時間回覆
     * @param trainTimeText 用戶說的火車時間（如 "十點半"、"下午三點"）
     */
    fun handleTrainTimeResponse(trainTimeText: String) {
        android.util.Log.d(TAG, "處理火車時間回覆: $trainTimeText")

        val details = _uiState.value.pendingDestinationDetails ?: run {
            android.util.Log.w(TAG, "沒有待確認的目的地")
            resetVoiceAutoflow()
            return
        }

        // 解析火車時間，計算距離現在多少分鐘
        val minutesUntilTrain = parseTrainTime(trainTimeText)
        android.util.Log.d(TAG, "距離火車發車還有 $minutesUntilTrain 分鐘")

        // 如果 30 分鐘內要到車站，檢查是否有車
        if (minutesUntilTrain != null && minutesUntilTrain <= 30) {
            val hasNearbyDriver = _uiState.value.nearbyDrivers.isNotEmpty()

            if (!hasNearbyDriver) {
                // 沒有司機，拒絕服務
                android.util.Log.d(TAG, "30分鐘內趕火車但附近沒車，拒絕服務")
                speakWithBubble("抱歉，目前附近沒有司機，改天再為您服務")
                _uiState.value = _uiState.value.copy(
                    voiceAutoflowState = VoiceAutoflowState.IDLE,
                    pendingDestinationDetails = null
                )
                return
            }

            // 有司機，繼續正常流程但提醒時間緊迫
            android.util.Log.d(TAG, "30分鐘內趕火車，附近有車，繼續流程")
        }

        // 繼續正常確認流程
        _uiState.value = _uiState.value.copy(
            voiceAutoflowState = VoiceAutoflowState.CONFIRMING_DESTINATION
        )
        proceedWithDestinationConfirmation(details)
    }

    /**
     * 解析火車時間文字，返回距離現在的分鐘數
     * @return 分鐘數，如果無法解析則返回 null
     */
    private fun parseTrainTime(timeText: String): Int? {
        try {
            val now = java.util.Calendar.getInstance()
            val currentHour = now.get(java.util.Calendar.HOUR_OF_DAY)
            val currentMinute = now.get(java.util.Calendar.MINUTE)

            // 嘗試解析各種時間格式
            var targetHour: Int? = null
            var targetMinute: Int = 0

            // 處理 "X點半"
            val halfPattern = Regex("(\\d+|[一二三四五六七八九十]+)點半")
            halfPattern.find(timeText)?.let { match ->
                targetHour = parseChineseNumber(match.groupValues[1])
                targetMinute = 30
            }

            // 處理 "X點" (不含半)
            if (targetHour == null) {
                val hourPattern = Regex("(\\d+|[一二三四五六七八九十]+)點")
                hourPattern.find(timeText)?.let { match ->
                    targetHour = parseChineseNumber(match.groupValues[1])
                    targetMinute = 0
                }
            }

            // 處理 "X:XX" 或 "X：XX"
            if (targetHour == null) {
                val colonPattern = Regex("(\\d+)[：:](\\d+)")
                colonPattern.find(timeText)?.let { match ->
                    targetHour = match.groupValues[1].toIntOrNull()
                    targetMinute = match.groupValues[2].toIntOrNull() ?: 0
                }
            }

            // 處理下午/晚上
            if (targetHour != null && targetHour!! < 12) {
                if (timeText.contains("下午") || timeText.contains("晚上")) {
                    targetHour = targetHour!! + 12
                } else if (!timeText.contains("上午") && !timeText.contains("早上")) {
                    // 沒有明確指定，根據當前時間推斷
                    if (currentHour >= 12 && targetHour!! < currentHour - 12) {
                        targetHour = targetHour!! + 12
                    }
                }
            }

            if (targetHour == null) {
                android.util.Log.w(TAG, "無法解析時間: $timeText")
                return null
            }

            // 計算距離現在的分鐘數
            var minutesUntil = (targetHour!! - currentHour) * 60 + (targetMinute - currentMinute)

            // 如果是負數，可能是明天的時間（但趕火車通常不會是明天）
            if (minutesUntil < 0) {
                minutesUntil += 24 * 60
            }

            return minutesUntil
        } catch (e: Exception) {
            android.util.Log.e(TAG, "解析時間失敗: ${e.message}")
            return null
        }
    }

    /**
     * 解析中文數字
     */
    private fun parseChineseNumber(text: String): Int? {
        // 先嘗試直接解析阿拉伯數字
        text.toIntOrNull()?.let { return it }

        // 中文數字對照
        val chineseMap = mapOf(
            "一" to 1, "二" to 2, "三" to 3, "四" to 4, "五" to 5,
            "六" to 6, "七" to 7, "八" to 8, "九" to 9, "十" to 10,
            "十一" to 11, "十二" to 12
        )

        return chineseMap[text]
    }

    /**
     * 繼續目的地確認流程（火車站詢問時間後使用）
     */
    private fun proceedWithDestinationConfirmation(details: PlaceDetails) {
        val currentLocation = _uiState.value.currentLocation
        val destLatLng = details.latLng

        if (currentLocation != null && destLatLng != null) {
            viewModelScope.launch {
                try {
                    val result = directionsService.getDirections(currentLocation, destLatLng)
                    result.onSuccess { directions ->
                        val fare = FareCalculator.calculateFare(directions.distanceMeters)

                        _uiState.value = _uiState.value.copy(
                            routeInfo = directions,
                            fareEstimate = fare
                        )

                        val fareMessage = if (fare.isNightTime) {
                            "約 ${fare.totalFare} 元，含夜間加成"
                        } else {
                            "約 ${fare.totalFare} 元"
                        }
                        val etaMessage = getEstimatedPickupTime()

                        speakWithBubbleAndCallback(
                            message = "去${details.name}，$fareMessage，$etaMessage，可以等嗎？",
                            onComplete = {
                                if (_uiState.value.voiceAutoflowState == VoiceAutoflowState.CONFIRMING_DESTINATION) {
                                    startVoiceRecording()
                                }
                            }
                        )
                    }.onFailure {
                        speakSimpleConfirmation(details)
                    }
                } catch (e: Exception) {
                    speakSimpleConfirmation(details)
                }
            }
        } else {
            speakSimpleConfirmation(details)
        }
    }

    /**
     * 重置語音自動流程
     */
    private fun resetVoiceAutoflow() {
        _uiState.value = _uiState.value.copy(
            voiceAutoflowState = VoiceAutoflowState.IDLE,
            pendingDestinationDetails = null,
            originalVoiceQuery = null
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
            pendingDestinationDetails = null,
            originalVoiceQuery = null
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
            originalVoiceQuery = null,
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

    // ==================== 語音對講功能 ====================

    /**
     * 開始語音對講錄音（按住按鈕時調用）
     */
    fun startVoiceChatRecording() {
        val currentOrder = _uiState.value.currentOrder
        if (currentOrder == null) {
            android.util.Log.w(TAG, "無法開始對講：沒有進行中的訂單")
            return
        }

        // 設置 VoiceChatManager 的用戶資訊
        voiceChatManager.setCurrentUser(
            orderId = currentOrder.orderId,
            userId = currentPassengerId,
            userName = currentOrder.passengerName ?: "乘客",
            userType = VoiceChatMessage.SENDER_TYPE_PASSENGER
        )

        voiceChatManager.startRecording()
    }

    /**
     * 停止語音對講錄音並發送（放開按鈕時調用）
     */
    fun stopVoiceChatRecording() {
        voiceChatManager.stopRecordingAndSend()
    }

    /**
     * 取消語音對講錄音（不發送）
     */
    fun cancelVoiceChatRecording() {
        voiceChatManager.cancelRecording()
    }

    /**
     * 顯示語音對講面板
     */
    fun showVoiceChatPanel() {
        _showVoiceChatPanel.value = true
        _voiceChatUnreadCount.value = 0  // 清除未讀計數
    }

    /**
     * 隱藏語音對講面板
     */
    fun hideVoiceChatPanel() {
        _showVoiceChatPanel.value = false
    }

    /**
     * 判斷是否可以使用語音對講
     * 只有在訂單進行中（司機已接單 ~ 行程中）才能使用
     */
    fun canUseVoiceChat(): Boolean {
        val status = _uiState.value.orderStatus
        return status in listOf(
            OrderStatus.ACCEPTED,
            OrderStatus.DRIVER_ARRIVING,
            OrderStatus.ARRIVED,
            OrderStatus.ON_TRIP
        )
    }

    /**
     * 清除語音對講記錄（訂單結束時調用）
     */
    fun clearVoiceChatHistory() {
        voiceChatManager.clearHistory()
        _voiceChatUnreadCount.value = 0
    }

    // ==================== 司機 ETA 廣播功能 ====================

    /**
     * 計算兩點之間的距離（公尺）- Haversine 公式
     */
    private fun calculateDistanceMeters(from: LatLng, to: LatLng): Float {
        val earthRadius = 6371000.0 // 地球半徑（公尺）

        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val deltaLat = Math.toRadians(to.latitude - from.latitude)
        val deltaLng = Math.toRadians(to.longitude - from.longitude)

        val a = kotlin.math.sin(deltaLat / 2) * kotlin.math.sin(deltaLat / 2) +
                kotlin.math.cos(lat1) * kotlin.math.cos(lat2) *
                kotlin.math.sin(deltaLng / 2) * kotlin.math.sin(deltaLng / 2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))

        return (earthRadius * c).toFloat()
    }

    /**
     * 檢查並廣播 ETA
     * 三個廣播時機：
     * 1. 司機剛接單時（初始 ETA）
     * 2. 距離減少到一半時
     * 3. 剩餘 1 公里時
     */
    private fun checkAndAnnounceETA(distanceMeters: Float, etaMinutes: Int) {
        val state = _uiState.value
        val initialDistance = state.initialDriverDistance ?: return
        val driverName = state.currentOrder?.driverName ?: "司機"

        // 1. 初始廣播（只在設置初始距離時觸發，由 updateOrderStatus 處理）
        // 這裡不處理，因為初始廣播在訂單狀態變為 ACCEPTED 時觸發

        // 2. 一半距離廣播
        if (!state.etaAnnouncedHalfway && distanceMeters <= initialDistance / 2) {
            _uiState.value = _uiState.value.copy(etaAnnouncedHalfway = true)
            val message = if (etaMinutes <= 1) {
                "${driverName}馬上就到了，請準備上車"
            } else {
                "${driverName}還有大約 ${etaMinutes} 分鐘到達"
            }
            announceDriverETA(message)
            android.util.Log.d(TAG, "📢 ETA 廣播（一半距離）: $message")
        }

        // 3. 剩餘 1 公里廣播
        if (!state.etaAnnouncedOneKm && distanceMeters <= 1000) {
            _uiState.value = _uiState.value.copy(etaAnnouncedOneKm = true)
            val message = if (etaMinutes <= 1) {
                "${driverName}快到了，還有不到一公里"
            } else {
                "${driverName}還有約 ${etaMinutes} 分鐘，請到上車點等候"
            }
            announceDriverETA(message)
            android.util.Log.d(TAG, "📢 ETA 廣播（剩餘1公里）: $message")
        }
    }

    /**
     * 廣播司機 ETA（使用 TTS 語音）
     */
    private fun announceDriverETA(message: String) {
        speakWithBubble(message)
    }

    /**
     * 重置 ETA 追蹤（新訂單開始時調用）
     */
    private fun resetETATracking() {
        _uiState.value = _uiState.value.copy(
            driverToPickupDistanceMeters = null,
            driverToPickupEtaMinutes = null,
            initialDriverDistance = null,
            etaAnnouncedInitial = false,
            etaAnnouncedHalfway = false,
            etaAnnouncedOneKm = false
        )
    }

    /**
     * 設置初始司機距離並廣播初始 ETA
     */
    private fun setInitialDriverDistanceAndAnnounce(driverLocation: LatLng?, pickupLocation: LatLng?, driverName: String) {
        if (driverLocation == null || pickupLocation == null) return

        val distanceMeters = calculateDistanceMeters(driverLocation, pickupLocation)
        val etaMinutes = (distanceMeters / 500).toInt().coerceAtLeast(1)

        _uiState.value = _uiState.value.copy(
            initialDriverDistance = distanceMeters,
            driverToPickupDistanceMeters = distanceMeters,
            driverToPickupEtaMinutes = etaMinutes,
            etaAnnouncedInitial = true
        )

        // 廣播初始 ETA
        val message = if (etaMinutes <= 2) {
            "${driverName}已接單，很快就到！"
        } else {
            "${driverName}已接單，預計 ${etaMinutes} 分鐘後到達"
        }
        announceDriverETA(message)
        android.util.Log.d(TAG, "📢 ETA 廣播（初始）: $message, 距離: ${distanceMeters.toInt()}m")
    }

    override fun onCleared() {
        super.onCleared()
        hybridLocationService.stopLocationUpdates()
        voiceRecorderService.release()
        voiceAssistant.release()
        voiceChatManager.release()
        viewModelScope.launch {
            webSocketManager.disconnect()
        }
    }
}
