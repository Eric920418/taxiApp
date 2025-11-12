package com.hualien.taxidriver.data.remote

import android.util.Log
import com.google.gson.Gson
import com.hualien.taxidriver.domain.model.Order
import com.hualien.taxidriver.utils.Constants
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * WebSocket管理器（Socket.io）
 */
class WebSocketManager private constructor() {

    private var socket: Socket? = null
    private val gson = Gson()

    // 連接狀態
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    // 訂單通知
    private val _orderOffer = MutableStateFlow<Order?>(null)
    val orderOffer: StateFlow<Order?> = _orderOffer.asStateFlow()

    // 訂單狀態更新
    private val _orderStatusUpdate = MutableStateFlow<Order?>(null)
    val orderStatusUpdate: StateFlow<Order?> = _orderStatusUpdate.asStateFlow()

    // 乘客端：附近司機列表
    private val _nearbyDrivers = MutableStateFlow<List<NearbyDriverInfo>>(emptyList())
    val nearbyDrivers: StateFlow<List<NearbyDriverInfo>> = _nearbyDrivers.asStateFlow()

    // 乘客端：訂單更新
    private val _passengerOrderUpdate = MutableStateFlow<Order?>(null)
    val passengerOrderUpdate: StateFlow<Order?> = _passengerOrderUpdate.asStateFlow()

    // 乘客端：司機實時位置
    private val _driverLocation = MutableStateFlow<DriverLocationInfo?>(null)
    val driverLocation: StateFlow<DriverLocationInfo?> = _driverLocation.asStateFlow()

    /**
     * 連接到伺服器（司機端）
     */
    fun connect(driverId: String) {
        try {
            Log.d(TAG, "========== 司機端WebSocket初始化 ==========")
            Log.d(TAG, "司機ID: $driverId")
            Log.d(TAG, "WebSocket URL: ${Constants.WS_URL}")

            // 如果已經有連接，先斷開
            if (socket != null) {
                Log.w(TAG, "⚠️ 檢測到已存在的 Socket 連接，先斷開...")
                socket?.disconnect()
                socket?.off()  // 移除所有事件監聽
                socket = null
            }

            val options = IO.Options().apply {
                reconnection = true
                reconnectionDelay = Constants.WS_RECONNECT_DELAY
                reconnectionAttempts = Constants.WS_MAX_RECONNECT_ATTEMPTS
            }

            socket = IO.socket(Constants.WS_URL, options).apply {
                on(Socket.EVENT_CONNECT) {
                    Log.d(TAG, "✅ 司機端 WebSocket 已連接")
                    _isConnected.value = true

                    val onlineData = JSONObject().apply {
                        put("driverId", driverId)
                    }
                    Log.d(TAG, "📤 發送 driver:online 事件: $onlineData")
                    emit("driver:online", onlineData)
                }

                on(Socket.EVENT_DISCONNECT) {
                    Log.w(TAG, "❌ 司機端 WebSocket 已斷開")
                    _isConnected.value = false
                }

                on(Socket.EVENT_CONNECT_ERROR) { args ->
                    Log.e(TAG, "❌ 司機端 WebSocket 連接錯誤")
                    Log.e(TAG, "錯誤詳情: ${args.firstOrNull()}")
                    _isConnected.value = false
                }

                // 監聽派單通知
                on("order:offer") { args ->
                    try {
                        Log.d(TAG, "========== 收到新訂單 ==========")
                        Log.d(TAG, "📥 收到 order:offer 事件")
                        val data = args.firstOrNull() as? JSONObject
                        Log.d(TAG, "原始訂單資料: $data")

                        data?.let {
                            val order = gson.fromJson(it.toString(), Order::class.java)
                            Log.d(TAG, "✅ 訂單解析成功")
                            Log.d(TAG, "訂單ID: ${order.orderId}")
                            Log.d(TAG, "乘客: ${order.passengerName}")
                            Log.d(TAG, "電話: ${order.passengerPhone}")
                            Log.d(TAG, "上車點: ${order.pickup.address}")
                            Log.d(TAG, "狀態: ${order.status}")

                            _orderOffer.value = order
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ 解析訂單失敗", e)
                    }
                }

                // 監聽訂單狀態更新
                on("order:status") { args ->
                    try {
                        Log.d(TAG, "📥 收到 order:status 事件")
                        val data = args.firstOrNull() as? JSONObject
                        Log.d(TAG, "狀態更新資料: $data")

                        data?.let {
                            val order = gson.fromJson(it.toString(), Order::class.java)
                            Log.d(TAG, "✅ 訂單狀態更新: ${order.orderId} -> ${order.status}")
                            _orderStatusUpdate.value = order
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ 解析訂單狀態失敗", e)
                    }
                }
            }

            Log.d(TAG, "正在連接 WebSocket...")
            socket?.connect()
        } catch (e: Exception) {
            Log.e(TAG, "❌ 初始化司機端 WebSocket 失敗", e)
        }
    }

    /**
     * 更新司機定位
     */
    fun updateLocation(
        driverId: String,
        latitude: Double,
        longitude: Double,
        speed: Float = 0f,
        bearing: Float = 0f
    ) {
        socket?.emit("driver:location", JSONObject().apply {
            put("driverId", driverId)
            put("lat", latitude)
            put("lng", longitude)
            put("speed", speed)
            put("bearing", bearing)
        })
    }

    /**
     * 更新司機狀態（實時通知 server）
     */
    fun updateDriverStatus(driverId: String, status: String) {
        Log.d(TAG, "========== 發送司機狀態更新事件 ==========")
        Log.d(TAG, "司機ID: $driverId")
        Log.d(TAG, "狀態: $status")

        socket?.emit("driver:status", JSONObject().apply {
            put("driverId", driverId)
            put("status", status)
        })

        Log.d(TAG, "✅ driver:status 事件已發送")
    }

    /**
     * 接受訂單
     */
    fun acceptOrder(orderId: String, driverId: String) {
        socket?.emit("order:accept", JSONObject().apply {
            put("orderId", orderId)
            put("driverId", driverId)
        })
    }

    /**
     * 拒絕訂單
     */
    fun rejectOrder(orderId: String, driverId: String) {
        socket?.emit("order:reject", JSONObject().apply {
            put("orderId", orderId)
            put("driverId", driverId)
        })
    }

    /**
     * 斷開連接
     */
    fun disconnect() {
        socket?.disconnect()
        socket?.off()
        socket = null
        _isConnected.value = false
        Log.d(TAG, "WebSocket disconnected manually")
    }

    /**
     * 清除訂單通知
     */
    fun clearOrderOffer() {
        _orderOffer.value = null
    }

    /**
     * 乘客端連接到伺服器
     */
    fun connectAsPassenger(passengerId: String) {
        try {
            Log.d(TAG, "========== 乘客端WebSocket初始化 ==========")
            Log.d(TAG, "乘客ID: $passengerId")
            Log.d(TAG, "WebSocket URL: ${Constants.WS_URL}")

            // 如果已經有連接，先斷開
            if (socket != null) {
                Log.w(TAG, "⚠️ 檢測到已存在的 Socket 連接，先斷開...")
                socket?.disconnect()
                socket?.off()  // 移除所有事件監聽
                socket = null
            }

            val options = IO.Options().apply {
                reconnection = true
                reconnectionDelay = Constants.WS_RECONNECT_DELAY
                reconnectionAttempts = Constants.WS_MAX_RECONNECT_ATTEMPTS
            }

            socket = IO.socket(Constants.WS_URL, options).apply {
                on(Socket.EVENT_CONNECT) {
                    Log.d(TAG, "✅ 乘客端 WebSocket 已連接")
                    _isConnected.value = true

                    val onlineData = JSONObject().apply {
                        put("passengerId", passengerId)
                    }
                    Log.d(TAG, "📤 發送 passenger:online 事件: $onlineData")
                    emit("passenger:online", onlineData)
                }

                on(Socket.EVENT_DISCONNECT) {
                    Log.w(TAG, "❌ 乘客端 WebSocket 已斷開")
                    _isConnected.value = false
                }

                on(Socket.EVENT_CONNECT_ERROR) { args ->
                    Log.e(TAG, "❌ 乘客端 WebSocket 連接錯誤")
                    Log.e(TAG, "錯誤詳情: ${args.firstOrNull()}")
                    _isConnected.value = false
                }

                // 監聽附近司機位置
                on("nearby:drivers") { args ->
                    try {
                        Log.d(TAG, "📥 收到 nearby:drivers 事件")
                        val data = args.firstOrNull() as? org.json.JSONArray
                        data?.let { array ->
                            Log.d(TAG, "司機數量: ${array.length()}")
                            val drivers = mutableListOf<NearbyDriverInfo>()
                            for (i in 0 until array.length()) {
                                val driverJson = array.getJSONObject(i)
                                val locationJson = driverJson.getJSONObject("location")
                                drivers.add(
                                    NearbyDriverInfo(
                                        driverId = driverJson.getString("driverId"),
                                        latitude = locationJson.getDouble("lat"),
                                        longitude = locationJson.getDouble("lng"),
                                        timestamp = driverJson.getLong("timestamp")
                                    )
                                )
                            }
                            Log.d(TAG, "✅ 已解析附近司機: ${drivers.size} 位")
                            _nearbyDrivers.value = drivers
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ 解析附近司機失敗", e)
                    }
                }

                // 監聽訂單更新（乘客端）
                on("order:update") { args ->
                    try {
                        Log.d(TAG, "📥 收到 order:update 事件")
                        val data = args.firstOrNull() as? JSONObject
                        Log.d(TAG, "訂單更新資料: $data")
                        data?.let {
                            val order = gson.fromJson(it.toString(), Order::class.java)
                            Log.d(TAG, "✅ 訂單更新: ${order.orderId} -> ${order.status}")
                            _passengerOrderUpdate.value = order
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ 解析訂單更新失敗", e)
                    }
                }

                // 監聽司機實時位置
                on("driver:location") { args ->
                    try {
                        val data = args.firstOrNull() as? JSONObject
                        data?.let {
                            val driverLocation = DriverLocationInfo(
                                driverId = it.getString("driverId"),
                                latitude = it.getDouble("lat"),
                                longitude = it.getDouble("lng"),
                                speed = it.optDouble("speed", 0.0).toFloat(),
                                bearing = it.optDouble("bearing", 0.0).toFloat(),
                                timestamp = it.optLong("timestamp", System.currentTimeMillis())
                            )
                            Log.d(TAG, "📥 收到司機位置: ${driverLocation.driverId}")
                            _driverLocation.value = driverLocation
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ 解析司機位置失敗", e)
                    }
                }
            }

            Log.d(TAG, "正在連接 WebSocket...")
            socket?.connect()
        } catch (e: Exception) {
            Log.e(TAG, "❌ 初始化乘客端 WebSocket 失敗", e)
        }
    }

    companion object {
        private const val TAG = "WebSocketManager"

        @Volatile
        private var instance: WebSocketManager? = null

        fun getInstance(): WebSocketManager {
            return instance ?: synchronized(this) {
                instance ?: WebSocketManager().also { instance = it }
            }
        }
    }
}

/**
 * 附近司機信息（WebSocket 推送）
 */
data class NearbyDriverInfo(
    val driverId: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long
)

/**
 * 司機實時位置信息
 */
data class DriverLocationInfo(
    val driverId: String,
    val latitude: Double,
    val longitude: Double,
    val speed: Float,
    val bearing: Float,
    val timestamp: Long
)
