package com.hualien.taxidriver.data.remote

import android.util.Log
import com.google.gson.Gson
import com.hualien.taxidriver.data.remote.dto.VoiceChatMessage
import com.hualien.taxidriver.domain.model.Order
import com.hualien.taxidriver.utils.Constants
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import kotlin.math.min
import kotlin.math.pow

/**
 * WebSocket管理器（Socket.io）
 * 優化版本：智能指數退避重連 + 連接狀態管理
 */
class WebSocketManager private constructor() {

    private var socket: Socket? = null
    private val gson = Gson()

    // 添加 Mutex 確保線程安全
    private val connectionMutex = Mutex()

    // 追踪連接狀態，避免重複連接
    private var isConnecting = false
    private var currentMode: ConnectionMode? = null
    private var lastConnectionMode: ConnectionMode? = null  // 記住上次模式，用於重連

    // ===== 智能重連相關 =====
    private var reconnectJob: Job? = null
    private var currentReconnectAttempt = 0
    private var lastUserId: String? = null  // 記住上次連接的用戶ID，用於自動重連

    // 重連狀態
    private val _reconnectState = MutableStateFlow<ReconnectState>(ReconnectState.IDLE)
    val reconnectState: StateFlow<ReconnectState> = _reconnectState.asStateFlow()

    // 連接狀態
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    // 訂單通知
    private val _orderOffer = MutableStateFlow<Order?>(null)
    val orderOffer: StateFlow<Order?> = _orderOffer.asStateFlow()

    // 訂單狀態更新
    private val _orderStatusUpdate = MutableStateFlow<Order?>(null)
    val orderStatusUpdate: StateFlow<Order?> = _orderStatusUpdate.asStateFlow()

    // 智能派單 V2：批次超時通知
    private val _batchTimeout = MutableStateFlow<BatchTimeoutInfo?>(null)
    val batchTimeout: StateFlow<BatchTimeoutInfo?> = _batchTimeout.asStateFlow()

    // 乘客端：附近司機列表
    private val _nearbyDrivers = MutableStateFlow<List<NearbyDriverInfo>>(emptyList())
    val nearbyDrivers: StateFlow<List<NearbyDriverInfo>> = _nearbyDrivers.asStateFlow()

    // 乘客端：訂單更新
    private val _passengerOrderUpdate = MutableStateFlow<Order?>(null)
    val passengerOrderUpdate: StateFlow<Order?> = _passengerOrderUpdate.asStateFlow()

    // 乘客端：司機實時位置
    private val _driverLocation = MutableStateFlow<DriverLocationInfo?>(null)
    val driverLocation: StateFlow<DriverLocationInfo?> = _driverLocation.asStateFlow()

    // 語音對講：收到的語音訊息
    private val _voiceChatMessage = MutableStateFlow<VoiceChatMessage?>(null)
    val voiceChatMessage: StateFlow<VoiceChatMessage?> = _voiceChatMessage.asStateFlow()

    // 電話叫車：催單通知
    private val _orderUrge = MutableStateFlow<OrderUrgeInfo?>(null)
    val orderUrge: StateFlow<OrderUrgeInfo?> = _orderUrge.asStateFlow()

    // Coroutine scope for reconnection
    private val reconnectScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * 連接到伺服器（司機端）
     * 優化版：使用 Mutex 防止競態條件，避免重複註冊事件
     */
    suspend fun connect(driverId: String) {
        connectionMutex.withLock {
            try {
                Log.d(TAG, "========== 司機端WebSocket初始化 ==========")
                Log.d(TAG, "司機ID: $driverId")
                Log.d(TAG, "WebSocket URL: ${Constants.WS_URL}")

                // 檢查是否已經在連接中或已連接
                if (isConnecting || (_isConnected.value && currentMode == ConnectionMode.DRIVER)) {
                    Log.w(TAG, "⚠️ WebSocket 已經在連接中或已連接，跳過重複連接")
                    return
                }

                isConnecting = true

                // 完全清理舊連接
                cleanupSocket()

                val options = IO.Options().apply {
                    // 禁用 Socket.io 內建重連，完全由自定義 scheduleReconnect() 管理
                    // 避免兩套重連機制打架導致秒斷循環
                    reconnection = false
                    timeout = 20000L  // 連接超時 20 秒
                }

                // 創建新的 socket 實例
                socket = IO.socket(Constants.WS_URL, options)

                // 分離事件註冊和連接，避免重複註冊
                setupDriverEventListeners(driverId)

                // 開始連接
                Log.d(TAG, "正在連接 WebSocket...")
                socket?.connect()

                currentMode = ConnectionMode.DRIVER
                lastConnectionMode = ConnectionMode.DRIVER
                isConnecting = false
            } catch (e: Exception) {
                Log.e(TAG, "❌ 初始化司機端 WebSocket 失敗", e)
                isConnecting = false
            }
        }
    }

    /**
     * 設置司機端事件監聽器（獨立方法，避免重複註冊）
     * 優化版：添加智能重連機制
     */
    private fun setupDriverEventListeners(driverId: String) {
        // 保存用戶ID用於重連
        lastUserId = driverId

        socket?.apply {
            // 先移除所有舊的監聽器
            off()

            on(Socket.EVENT_CONNECT) {
                Log.d(TAG, "✅ 司機端 WebSocket 已連接")
                _isConnected.value = true
                _reconnectState.value = ReconnectState.IDLE
                currentReconnectAttempt = 0  // 重置重連計數

                val onlineData = JSONObject().apply {
                    put("driverId", driverId)
                }
                Log.d(TAG, "📤 發送 driver:online 事件: $onlineData")
                emit("driver:online", onlineData)
            }

            on(Socket.EVENT_DISCONNECT) { args ->
                val reason = args.firstOrNull()?.toString() ?: "unknown"
                Log.w(TAG, "❌ 司機端 WebSocket 已斷開")
                Log.w(TAG, "========== 斷線診斷 ==========")
                Log.w(TAG, "斷線原因: $reason")
                Log.w(TAG, "分類: ${classifyDisconnectReason(reason)}")
                Log.w(TAG, "當前模式: $currentMode")
                Log.w(TAG, "================================")
                _isConnected.value = false

                // 僅在非手動斷開（io client disconnect）時重連
                if (currentMode == ConnectionMode.DRIVER && reason != "io client disconnect") {
                    scheduleReconnect()
                }
            }

            on(Socket.EVENT_CONNECT_ERROR) { args ->
                Log.e(TAG, "❌ 司機端 WebSocket 連接錯誤")
                Log.e(TAG, "錯誤詳情: ${args.firstOrNull()}")
                _isConnected.value = false

                // 觸發智能重連
                if (currentMode == ConnectionMode.DRIVER) {
                    scheduleReconnect()
                }
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
                        // 距離和預估時間資訊
                        order.distanceToPickup?.let { d ->
                            Log.d(TAG, "到客人: ${d}km, 約${order.etaToPickup}分鐘")
                        }
                        order.tripDistance?.let { d ->
                            Log.d(TAG, "行程: ${d}km, 約${order.estimatedTripDuration}分鐘")
                        }
                        // 預估車資
                        Log.d(TAG, "預估車資: ${order.estimatedFare ?: "null"}")
                        Log.d(TAG, "目的地: ${order.destination?.address ?: "未設定"}")

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

            // 監聽訂單被其他司機接走
            on("order:taken") { args ->
                try {
                    Log.d(TAG, "📥 收到 order:taken 事件（訂單已被其他司機接走）")
                    val data = args.firstOrNull() as? JSONObject
                    Log.d(TAG, "訂單被接走資料: $data")

                    data?.let {
                        val orderId = it.getString("orderId")
                        val message = it.optString("message", "此訂單已被其他司機接走")
                        Log.d(TAG, "⚠️ 訂單 $orderId 已被接走: $message")

                        // 清除當前訂單 offer（如果是同一訂單）
                        _orderOffer.value?.let { currentOrder ->
                            if (currentOrder.orderId == orderId) {
                                Log.d(TAG, "🗑️ 清除訂單 offer: $orderId")
                                _orderOffer.value = null
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ 解析 order:taken 失敗", e)
                }
            }

            // 智能派單 V2：監聯批次超時
            on("order:batch-timeout") { args ->
                try {
                    Log.d(TAG, "📥 收到 order:batch-timeout 事件")
                    val data = args.firstOrNull() as? JSONObject
                    Log.d(TAG, "批次超時資料: $data")

                    data?.let {
                        val timeoutInfo = BatchTimeoutInfo(
                            orderId = it.getString("orderId"),
                            message = it.optString("message", "回應時間已過")
                        )
                        Log.d(TAG, "⏰ 訂單 ${timeoutInfo.orderId} 已超時: ${timeoutInfo.message}")

                        // 清除當前訂單 offer（該訂單已轉給下一批司機）
                        _orderOffer.value?.let { currentOrder ->
                            if (currentOrder.orderId == timeoutInfo.orderId) {
                                _orderOffer.value = null
                            }
                        }

                        _batchTimeout.value = timeoutInfo
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ 解析批次超時失敗", e)
                }
            }

            // 電話叫車：監聽催單通知
            on("order:urge") { args ->
                try {
                    Log.d(TAG, "📥 收到 order:urge 事件（催單）")
                    val data = args.firstOrNull() as? JSONObject
                    Log.d(TAG, "催單資料: $data")

                    data?.let {
                        val urgeInfo = OrderUrgeInfo(
                            orderId = it.getString("orderId"),
                            message = it.optString("message", "乘客催促您盡快前往"),
                            callerNumber = it.optString("callerNumber", null),
                            urgencyLevel = it.optInt("urgencyLevel", 1)
                        )
                        Log.d(TAG, "⚡ 催單: 訂單 ${urgeInfo.orderId}, 等級: ${urgeInfo.urgencyLevel}")
                        _orderUrge.value = urgeInfo
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ 解析催單通知失敗", e)
                }
            }

            // 語音對講：監聽乘客發來的語音訊息
            on("voice:message") { args ->
                try {
                    Log.d(TAG, "📥 收到 voice:message 事件（司機端）")
                    val data = args.firstOrNull() as? JSONObject
                    Log.d(TAG, "語音訊息資料: $data")

                    data?.let {
                        val message = VoiceChatMessage(
                            messageId = it.optString("messageId", java.util.UUID.randomUUID().toString()),
                            orderId = it.getString("orderId"),
                            senderId = it.getString("senderId"),
                            senderType = it.getString("senderType"),
                            senderName = it.optString("senderName", "乘客"),
                            messageText = it.getString("messageText"),
                            timestamp = it.optLong("timestamp", System.currentTimeMillis())
                        )
                        Log.d(TAG, "✅ 收到語音訊息: ${message.senderName}說「${message.messageText}」")
                        _voiceChatMessage.value = message
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ 解析語音訊息失敗", e)
                }
            }
        }
    }

    /**
     * 更新司機定位
     * @param orderId 當司機處於 ON_TRIP 狀態時傳入訂單ID，用於後台軌跡追蹤
     */
    fun updateLocation(
        driverId: String,
        latitude: Double,
        longitude: Double,
        speed: Float = 0f,
        bearing: Float = 0f,
        orderId: String? = null
    ) {
        socket?.emit("driver:location", JSONObject().apply {
            put("driverId", driverId)
            put("lat", latitude)
            put("lng", longitude)
            put("speed", speed)
            put("bearing", bearing)
            orderId?.let { put("orderId", it) }
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

    // ===== 語音對講相關方法 =====

    /**
     * 發送語音訊息
     * @param orderId 訂單ID
     * @param senderId 發送者ID（司機ID 或 乘客ID）
     * @param senderName 發送者名稱
     * @param senderType 發送者類型（"driver" 或 "passenger"）
     * @param messageText 訊息文字（語音轉錄後的文字）
     */
    fun sendVoiceMessage(
        orderId: String,
        senderId: String,
        senderName: String,
        senderType: String,
        messageText: String
    ) {
        val messageId = java.util.UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        Log.d(TAG, "========== 發送語音訊息 ==========")
        Log.d(TAG, "訂單ID: $orderId")
        Log.d(TAG, "發送者: $senderName ($senderType)")
        Log.d(TAG, "訊息內容: $messageText")

        socket?.emit("voice:message", JSONObject().apply {
            put("messageId", messageId)
            put("orderId", orderId)
            put("senderId", senderId)
            put("senderName", senderName)
            put("senderType", senderType)
            put("messageText", messageText)
            put("timestamp", timestamp)
        })

        Log.d(TAG, "✅ voice:message 事件已發送")
    }

    /**
     * 清除語音訊息（處理完畢後調用）
     */
    fun clearVoiceChatMessage() {
        _voiceChatMessage.value = null
    }

    /**
     * 清除催單通知（處理完畢後調用）
     */
    fun clearOrderUrge() {
        _orderUrge.value = null
    }

    /**
     * 完全清理 Socket 連接
     */
    private fun cleanupSocket() {
        socket?.let { s ->
            Log.d(TAG, "清理 Socket 連接...")
            s.disconnect()
            s.off()  // 移除所有事件監聽器
        }
        socket = null
        currentMode = null

        // 【重要】清除所有 StateFlow 數據，防止舊訂單殘留
        Log.d(TAG, "清理所有 StateFlow 數據...")
        _orderOffer.value = null
        _orderStatusUpdate.value = null
        _batchTimeout.value = null
        _passengerOrderUpdate.value = null
        _driverLocation.value = null
        _voiceChatMessage.value = null
        _orderUrge.value = null
    }

    /**
     * 斷開連接
     */
    suspend fun disconnect() {
        connectionMutex.withLock {
            cleanupSocket()
            _isConnected.value = false
            isConnecting = false
            Log.d(TAG, "WebSocket disconnected manually")
        }
    }

    /**
     * 清除訂單通知
     */
    fun clearOrderOffer() {
        _orderOffer.value = null
    }

    /**
     * 清除批次超時通知
     */
    fun clearBatchTimeout() {
        _batchTimeout.value = null
    }

    /**
     * 乘客端連接到伺服器
     * 優化版：使用 Mutex 防止競態條件，避免重複註冊事件
     */
    suspend fun connectAsPassenger(passengerId: String) {
        connectionMutex.withLock {
            try {
                Log.d(TAG, "========== 乘客端WebSocket初始化 ==========")
                Log.d(TAG, "乘客ID: $passengerId")
                Log.d(TAG, "WebSocket URL: ${Constants.WS_URL}")

                // 檢查是否已經在連接中或已連接
                if (isConnecting || (_isConnected.value && currentMode == ConnectionMode.PASSENGER)) {
                    Log.w(TAG, "⚠️ WebSocket 已經在連接中或已連接，跳過重複連接")
                    return
                }

                isConnecting = true

                // 完全清理舊連接
                cleanupSocket()

                val options = IO.Options().apply {
                    // 禁用 Socket.io 內建重連，完全由自定義 scheduleReconnect() 管理
                    reconnection = false
                    timeout = 20000L  // 連接超時 20 秒
                }

                // 創建新的 socket 實例
                socket = IO.socket(Constants.WS_URL, options)

                // 分離事件註冊和連接，避免重複註冊
                setupPassengerEventListeners(passengerId)

                // 開始連接
                Log.d(TAG, "正在連接 WebSocket...")
                socket?.connect()

                currentMode = ConnectionMode.PASSENGER
                lastConnectionMode = ConnectionMode.PASSENGER
                isConnecting = false
            } catch (e: Exception) {
                Log.e(TAG, "❌ 初始化乘客端 WebSocket 失敗", e)
                isConnecting = false
            }
        }
    }

    /**
     * 設置乘客端事件監聽器（獨立方法，避免重複註冊）
     * 優化版：添加智能重連機制
     */
    private fun setupPassengerEventListeners(passengerId: String) {
        // 保存用戶ID用於重連
        lastUserId = passengerId

        socket?.apply {
            // 先移除所有舊的監聽器
            off()

            on(Socket.EVENT_CONNECT) {
                Log.d(TAG, "✅ 乘客端 WebSocket 已連接")
                _isConnected.value = true
                _reconnectState.value = ReconnectState.IDLE
                currentReconnectAttempt = 0  // 重置重連計數

                val onlineData = JSONObject().apply {
                    put("passengerId", passengerId)
                }
                Log.d(TAG, "📤 發送 passenger:online 事件: $onlineData")
                emit("passenger:online", onlineData)
            }

            on(Socket.EVENT_DISCONNECT) { args ->
                val reason = args.firstOrNull()?.toString() ?: "unknown"
                Log.w(TAG, "❌ 乘客端 WebSocket 已斷開")
                Log.w(TAG, "========== 斷線診斷 ==========")
                Log.w(TAG, "斷線原因: $reason")
                Log.w(TAG, "分類: ${classifyDisconnectReason(reason)}")
                Log.w(TAG, "當前模式: $currentMode")
                Log.w(TAG, "================================")
                _isConnected.value = false

                // 僅在非手動斷開時重連
                if (currentMode == ConnectionMode.PASSENGER && reason != "io client disconnect") {
                    scheduleReconnect()
                }
            }

            on(Socket.EVENT_CONNECT_ERROR) { args ->
                Log.e(TAG, "❌ 乘客端 WebSocket 連接錯誤")
                Log.e(TAG, "錯誤詳情: ${args.firstOrNull()}")
                _isConnected.value = false

                // 觸發智能重連
                if (currentMode == ConnectionMode.PASSENGER) {
                    scheduleReconnect()
                }
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

            // 監聽司機實時位置（含 ETA 資訊）
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
                            timestamp = it.optLong("timestamp", System.currentTimeMillis()),
                            orderId = it.optString("orderId", null),
                            distanceToPickup = if (it.has("distanceToPickup")) it.getInt("distanceToPickup") else null,
                            etaMinutes = if (it.has("etaMinutes")) it.getInt("etaMinutes") else null
                        )
                        Log.d(TAG, "📥 收到司機位置: ${driverLocation.driverId}, 距離: ${driverLocation.distanceToPickup}m, ETA: ${driverLocation.etaMinutes}分")
                        _driverLocation.value = driverLocation
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ 解析司機位置失敗", e)
                }
            }

            // 語音對講：監聽司機發來的語音訊息
            on("voice:message") { args ->
                try {
                    Log.d(TAG, "📥 收到 voice:message 事件（乘客端）")
                    val data = args.firstOrNull() as? JSONObject
                    Log.d(TAG, "語音訊息資料: $data")

                    data?.let {
                        val message = VoiceChatMessage(
                            messageId = it.optString("messageId", java.util.UUID.randomUUID().toString()),
                            orderId = it.getString("orderId"),
                            senderId = it.getString("senderId"),
                            senderType = it.getString("senderType"),
                            senderName = it.optString("senderName", "司機"),
                            messageText = it.getString("messageText"),
                            timestamp = it.optLong("timestamp", System.currentTimeMillis())
                        )
                        Log.d(TAG, "✅ 收到語音訊息: ${message.senderName}說「${message.messageText}」")
                        _voiceChatMessage.value = message
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ 解析語音訊息失敗", e)
                }
            }
        }
    }

    // ===== 智能重連相關方法 =====

    /**
     * 智能重連調度器
     * 使用指數退避算法計算重連延遲
     */
    private fun scheduleReconnect() {
        // 取消之前的重連任務
        reconnectJob?.cancel()

        // 無限重連：永不放棄，只有手動 disconnect 才停止
        // 前 20 次快速重連（指數退避最大 60 秒），之後降頻為每 2 分鐘一次
        val delay = if (currentReconnectAttempt < FAST_PHASE_ATTEMPTS) {
            calculateReconnectDelay(currentReconnectAttempt)
        } else {
            SLOW_RECONNECT_DELAY  // 2 分鐘慢速重連
        }
        currentReconnectAttempt++

        Log.d(TAG, "========== 智能重連調度 ==========")
        Log.d(TAG, "重連次數: $currentReconnectAttempt (${if (currentReconnectAttempt < FAST_PHASE_ATTEMPTS) "快速" else "慢速"}模式)")
        Log.d(TAG, "延遲時間: ${delay}ms")
        Log.d(TAG, "連接模式: $currentMode")

        _reconnectState.value = ReconnectState.WAITING(delay, currentReconnectAttempt)

        reconnectJob = reconnectScope.launch {
            try {
                delay(delay)

                if (isActive) {
                    _reconnectState.value = ReconnectState.RECONNECTING(currentReconnectAttempt)
                    performReconnect()
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "重連任務被取消")
            }
        }
    }

    /**
     * 計算指數退避延遲時間
     * 公式：min(BASE_DELAY * 2^attempt, MAX_DELAY) + 隨機抖動
     */
    private fun calculateReconnectDelay(attempt: Int): Long {
        val exponentialDelay = BASE_RECONNECT_DELAY * 2.0.pow(attempt.toDouble()).toLong()
        val cappedDelay = min(exponentialDelay, MAX_RECONNECT_DELAY)

        // 添加隨機抖動（0-20%），避免所有客戶端同時重連
        val jitter = (cappedDelay * 0.2 * Math.random()).toLong()

        return cappedDelay + jitter
    }

    /**
     * 執行重連
     */
    private suspend fun performReconnect() {
        val userId = lastUserId
        // 在 cleanupSocket 之前保存 mode（cleanupSocket 會清除 currentMode）
        val mode = currentMode ?: lastConnectionMode

        if (userId == null || mode == null) {
            Log.e(TAG, "❌ 無法重連：缺少用戶ID或連接模式")
            _reconnectState.value = ReconnectState.FAILED
            return
        }

        Log.d(TAG, "🔄 執行重連... (用戶: $userId, 模式: $mode)")

        // 先清理舊連接
        cleanupSocket()
        isConnecting = false

        try {
            when (mode) {
                ConnectionMode.DRIVER -> connect(userId)
                ConnectionMode.PASSENGER -> connectAsPassenger(userId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 重連失敗", e)
            // 重連失敗，繼續調度下一次重連
            currentMode = mode  // 恢復 mode 以便下次重連
            scheduleReconnect()
        }
    }

    /**
     * 取消重連
     */
    fun cancelReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        currentReconnectAttempt = 0
        _reconnectState.value = ReconnectState.IDLE
        Log.d(TAG, "✅ 重連已取消")
    }

    /**
     * 手動觸發重連
     */
    fun manualReconnect() {
        val userId = lastUserId
        val mode = currentMode

        if (userId == null || mode == null) {
            Log.e(TAG, "❌ 無法手動重連：請先連接一次")
            return
        }

        // 重置重連計數並立即重連
        currentReconnectAttempt = 0
        _reconnectState.value = ReconnectState.RECONNECTING(1)

        reconnectScope.launch {
            performReconnect()
        }
    }

    /**
     * 分類斷線原因，協助診斷
     */
    private fun classifyDisconnectReason(reason: String): String {
        return when {
            reason.contains("io server disconnect") -> "伺服器主動斷開（可能是認證問題或伺服器重啟）"
            reason.contains("io client disconnect") -> "客戶端主動斷開（正常登出或 disconnect() 調用）"
            reason.contains("ping timeout") -> "心跳超時（網路不穩或伺服器無回應）"
            reason.contains("transport close") -> "傳輸層關閉（網路中斷或切換 WiFi/行動網路）"
            reason.contains("transport error") -> "傳輸層錯誤（網路異常）"
            else -> "未知原因: $reason"
        }
    }

    companion object {
        private const val TAG = "WebSocketManager"

        // 智能重連常數
        private const val BASE_RECONNECT_DELAY = 1000L    // 基礎延遲 1 秒
        private const val MAX_RECONNECT_DELAY = 60000L    // 最大延遲 60 秒
        private const val SLOW_RECONNECT_DELAY = 120000L  // 慢���重連 2 分鐘（5 分鐘後降頻）
        private const val FAST_PHASE_ATTEMPTS = 20        // 前 20 次快���重連（約 5 分鐘內）

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
 * 司機實時位置信息（含 ETA 資訊）
 */
data class DriverLocationInfo(
    val driverId: String,
    val latitude: Double,
    val longitude: Double,
    val speed: Float,
    val bearing: Float,
    val timestamp: Long,
    val orderId: String? = null,
    val distanceToPickup: Int? = null,  // 到上車點距離（公尺）
    val etaMinutes: Int? = null         // 預估到達時間（分鐘）
)

/**
 * 連接模式
 */
private enum class ConnectionMode {
    DRIVER,
    PASSENGER
}

/**
 * 重連狀態
 */
sealed class ReconnectState {
    /** 空閒/已連接 */
    object IDLE : ReconnectState()

    /** 等待重連中 */
    data class WAITING(
        val delayMs: Long,          // 等待時間（毫秒）
        val attemptNumber: Int      // 重連次數
    ) : ReconnectState()

    /** 正在重連 */
    data class RECONNECTING(
        val attemptNumber: Int      // 重連次數
    ) : ReconnectState()

    /** 重連失敗（已達最大次數） */
    object FAILED : ReconnectState()
}

/**
 * 智能派單 V2：批次超時資訊
 */
data class BatchTimeoutInfo(
    val orderId: String,
    val message: String
)

/**
 * 電話叫車：催單資訊
 */
data class OrderUrgeInfo(
    val orderId: String,
    val message: String,
    val callerNumber: String? = null,
    val urgencyLevel: Int = 1  // 1=普通催單, 2=緊急催單
)
