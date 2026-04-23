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
 * - **不跨 App 重啟**：純記憶體（Phase C MVP），App 被殺累計丟。Phase C+1 視需要加 DataStore。
 *
 * 速度閾值：5 km/h ≈ 1.39 m/s（業界標準，避免 GPS 漂移誤觸發）。
 *
 * 線程安全：所有方法在主執行緒呼叫（ViewModel collect speedFlow 在 main scope），
 *           不需要額外 sync。若未來改成多執行緒呼叫，需加 @Synchronized 或 Mutex。
 */
class SlowTrafficTimer(
    private val speedThresholdMps: Float = 5f / 3.6f
) {
    private var active: Boolean = false
    private var lowSpeedStartedAtMs: Long? = null
    private var totalIdleMs: Long = 0

    /**
     * 開始計時（行程進入 ON_TRIP 時呼叫）
     * 重設累計值與狀態。
     */
    fun start() {
        active = true
        lowSpeedStartedAtMs = null
        totalIdleMs = 0
    }

    /**
     * 停止計時（行程進入 SETTLING 時呼叫）
     * 結算時還在低速 → 把當下時段也算進去。
     */
    fun stop(nowMs: Long = System.currentTimeMillis()) {
        if (!active) return
        flushLowSpeed(nowMs)
        active = false
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
}
