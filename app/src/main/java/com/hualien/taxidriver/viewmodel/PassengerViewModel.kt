package com.hualien.taxidriver.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.hualien.taxidriver.data.remote.WebSocketManager
import com.hualien.taxidriver.data.repository.OrderRepository
import com.hualien.taxidriver.domain.model.Order
import com.hualien.taxidriver.service.DirectionsApiService
import com.hualien.taxidriver.service.DirectionsResult
import com.hualien.taxidriver.service.DriverMatchingService
import com.hualien.taxidriver.service.DriverWithETA
import com.hualien.taxidriver.service.HybridLocationService
import com.hualien.taxidriver.service.LocationState
import com.hualien.taxidriver.service.LocationSource
import com.hualien.taxidriver.utils.FareCalculator
import com.hualien.taxidriver.utils.FareResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

    private val orderRepository = OrderRepository()
    private val passengerRepository = com.hualien.taxidriver.data.repository.PassengerRepository()
    private val webSocketManager = WebSocketManager.getInstance()
    private val directionsService = DirectionsApiService(application)
    private val driverMatchingService = DriverMatchingService(application)
    private val hybridLocationService = HybridLocationService(application)

    private val _uiState = MutableStateFlow(PassengerUiState())
    val uiState: StateFlow<PassengerUiState> = _uiState.asStateFlow()

    init {
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

                    _uiState.value = state.copy(
                        currentOrder = rideResult.order,
                        orderStatus = OrderStatus.WAITING,
                        isLoading = false,
                        error = null
                    )
                }.onFailure { error ->
                    android.util.Log.e("PassengerViewModel", "❌ 叫車失敗: ${error.message}")
                    android.util.Log.e("PassengerViewModel", "錯誤詳情: ", error)

                    _uiState.value = state.copy(
                        isLoading = false,
                        orderStatus = OrderStatus.IDLE,
                        error = error.message ?: "叫車失敗"
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("PassengerViewModel", "❌ 叫車異常: ${e.message}")
                android.util.Log.e("PassengerViewModel", "異常詳情: ", e)

                _uiState.value = state.copy(
                    isLoading = false,
                    orderStatus = OrderStatus.IDLE,
                    error = e.message ?: "叫車失敗"
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

    override fun onCleared() {
        super.onCleared()
        hybridLocationService.stopLocationUpdates()
        viewModelScope.launch {
            webSocketManager.disconnect()
        }
    }
}
