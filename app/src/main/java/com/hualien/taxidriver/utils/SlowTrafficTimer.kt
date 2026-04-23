package com.hualien.taxidriver.utils

/**
 * 低速計時器（駐車費）
 *
 * 行程中車輛速度 < 速度閾值時累計秒數，到結算時把累計值帶給 FareDialog。
 *
 * 設計選擇：
 * - **State machine 而非 sample 累加**：記錄「進入低速狀態的時間戳」，離開低速時計算 elapsed。
 *   比「兩次 sample 都低速 → 加上 sample 間隔」精確 — 不會被 sample 頻率變動影響。
 * - **active flag**：ViewModel 一直訂閱速度 flow，timer 自己決定行程內才累計（start/stop 控制）。
 *   避免 ViewModel 管 collect job lifecycle，少一層 bug source。
 * - **Persist callback（Phase C+1a）**：timer 仍純 Kotlin（無 Android 依賴），透過 callback
 *   讓 ViewModel 寫 DataStore。callback 非 suspend → ViewModel 端 launch coroutine 處理。
 * - **orderId binding**：累計值按 orderId 索引，避免不同訂單混淆。
 *
 * 速度閾值：5 km/h ≈ 1.39 m/s（業界標準，避免 GPS 漂移誤觸發）。
 *
 * 線程安全：所有方法在主執行緒呼叫（ViewModel collect speedFlow 在 main scope），
 *           不需要額外 sync。若未來改成多執行緒呼叫，需加 @Synchronized 或 Mutex。
 */
class SlowTrafficTimer(
    private val speedThresholdMps: Float = 5f / 3.6f,
    private val persistThrottleMs: Long = 5000L,
) {
    /**
     * 持久化 callback。每次累計值更新且距上次 persist 超過 throttle 時呼叫。
     * 接收 (orderId, currentTotalSeconds)。null 表示不持久化（向下兼容測試 + lazy init）。
     *
     * 為什麼 var：HomeViewModel 在 ctor 時還沒拿到 Context（DataStoreManager 需要），
     * 真正需要持久化時才從 UI 層注入。Single-listener pattern 同 View.setOnClickListener。
     */
    var onPersist: ((orderId: String, seconds: Int) -> Unit)? = null

    private var active: Boolean = false
    private var lowSpeedStartedAtMs: Long? = null
    private var totalIdleMs: Long = 0
    private var orderId: String? = null
    private var lastPersistAtMs: Long = 0

    /**
     * 開始計時（行程進入 ON_TRIP 時呼叫）
     *
     * @param orderId 行程訂單 ID — 用於持久化索引
     * @param restoredSeconds 從 DataStore 恢復的初始累計（App 被殺重啟情境）。
     *                        新行程傳 0；恢復行程傳之前存的秒數。
     */
    fun start(orderId: String, restoredSeconds: Int = 0) {
        this.orderId = orderId
        active = true
        lowSpeedStartedAtMs = null
        totalIdleMs = restoredSeconds * 1000L
        lastPersistAtMs = 0
    }

    /**
     * 停止計時（行程進入 SETTLING 時呼叫）
     * 結算時還在低速 → 把當下時段也算進去。
     * 立即觸發一次 persist（不受 throttle 限制），確保最終值寫入。
     */
    fun stop(nowMs: Long = System.currentTimeMillis()) {
        if (!active) return
        flushLowSpeed(nowMs)
        active = false
        // 強制 persist 最終值（forceeven if within throttle window）
        val id = orderId
        if (id != null) onPersist?.invoke(id, snapshotSeconds(nowMs))
    }

    /**
     * 餵入一個速度 sample（每次 LocationService.onLocationResult 呼叫）
     * 非 active 時直接忽略，方便 ViewModel 在整個 service 生命週期都訂閱。
     */
    fun onSpeedUpdate(speedMps: Float, nowMs: Long = System.currentTimeMillis()) {
        if (!active) return
        val isLow = speedMps < speedThresholdMps
        when {
            isLow && lowSpeedStartedAtMs == null -> {
                lowSpeedStartedAtMs = nowMs
            }
            !isLow && lowSpeedStartedAtMs != null -> {
                flushLowSpeed(nowMs)
            }
        }
        maybePersist(nowMs)
    }

    /**
     * 取得當下累計低速秒數（含進行中時段）。
     * stop() 之後也能呼叫，回傳最終結果。
     */
    fun snapshotSeconds(nowMs: Long = System.currentTimeMillis()): Int {
        val live = lowSpeedStartedAtMs?.let { nowMs - it } ?: 0L
        return ((totalIdleMs + live) / 1000).toInt()
    }

    private fun flushLowSpeed(nowMs: Long) {
        lowSpeedStartedAtMs?.let { startedAt ->
            totalIdleMs += nowMs - startedAt
            lowSpeedStartedAtMs = null
        }
    }

    /**
     * Throttled persist：每 persistThrottleMs (預設 5 秒) 最多寫一次 DataStore。
     * 避免高頻 GPS update 造成 DataStore 寫爆（disk wear + 主執行緒 jank）。
     */
    private fun maybePersist(nowMs: Long) {
        val id = orderId ?: return
        val callback = onPersist ?: return
        if (nowMs - lastPersistAtMs < persistThrottleMs) return
        callback(id, snapshotSeconds(nowMs))
        lastPersistAtMs = nowMs
    }
}
