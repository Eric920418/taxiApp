package com.hualien.taxidriver.util

import com.hualien.taxidriver.domain.model.ShiftSlot
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 班次檢查 helper（mirror server-side src/services/ShiftChecker.ts）
 *
 * 設計約定：
 *  - shifts 為空 → 視為 24/7 在班（向後相容，沒設排班的司機行為不變）
 *  - 跨日班次（end < start，例 22:00-06:00）拆兩段比對
 *  - 時區用 Asia/Taipei
 *  - 任一 active 的 shift 命中即視為在班
 *
 * 重要：此 logic 必須跟 server 完全一致 —
 * server 派單時會用同樣規則過濾，client 端只是顯示用，不能誤判。
 */
object ShiftChecker {

    private val TAIPEI = ZoneId.of("Asia/Taipei")

    /**
     * 檢查現在是否在班次時間。shifts 空 → 視為 24/7。
     */
    fun isOnShift(shifts: List<ShiftSlot>?, now: ZonedDateTime = ZonedDateTime.now(TAIPEI)): Boolean {
        if (shifts == null || shifts.isEmpty()) return true
        val active = shifts.filter { it.isActive }
        if (active.isEmpty()) return false  // 全停用 → 不在班

        val nowMin = nowMinutes(now)
        return active.any { isMinuteInRange(nowMin, hhmmToMin(it.start), hhmmToMin(it.end)) }
    }

    /**
     * 取得「離下班還剩多少分鐘」。不在班 → null。
     * 多段 active shift 中時，回最早結束那段。
     */
    fun minutesUntilShiftEnd(shifts: List<ShiftSlot>?, now: ZonedDateTime = ZonedDateTime.now(TAIPEI)): Int? {
        if (shifts == null || shifts.isEmpty()) return null
        val active = shifts.filter { it.isActive }
        if (active.isEmpty()) return null

        val nowMin = nowMinutes(now)
        var minRemaining: Int? = null
        for (s in active) {
            val startMin = hhmmToMin(s.start)
            val endMin = hhmmToMin(s.end)
            if (!isMinuteInRange(nowMin, startMin, endMin)) continue

            val remaining = if (startMin <= endMin) {
                endMin - nowMin
            } else {
                // 跨日 case
                if (nowMin >= startMin) (1440 - nowMin) + endMin
                else endMin - nowMin
            }
            if (minRemaining == null || remaining < minRemaining) {
                minRemaining = remaining
            }
        }
        return minRemaining
    }

    /**
     * 取得下個班次開始時間（用於不在班時顯示「下班直到 XX:XX」）。
     * 若全部 active shift 都已過、明天的最早一段或同段是答案。
     * shifts 空 → null
     */
    fun minutesUntilNextShiftStart(shifts: List<ShiftSlot>?, now: ZonedDateTime = ZonedDateTime.now(TAIPEI)): Int? {
        if (shifts == null || shifts.isEmpty()) return null
        val active = shifts.filter { it.isActive }
        if (active.isEmpty()) return null

        val nowMin = nowMinutes(now)
        var minWait: Int? = null
        for (s in active) {
            val startMin = hhmmToMin(s.start)
            val wait = if (startMin >= nowMin) startMin - nowMin else (1440 - nowMin) + startMin
            if (minWait == null || wait < minWait) minWait = wait
        }
        return minWait
    }

    private fun nowMinutes(now: ZonedDateTime): Int {
        val t: LocalTime = now.toLocalTime()
        return t.hour * 60 + t.minute
    }

    private fun hhmmToMin(hhmm: String): Int {
        if (hhmm.length < 4 || !hhmm.contains(":")) return 0
        val parts = hhmm.split(":")
        val h = parts[0].toIntOrNull() ?: 0
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return h * 60 + m
    }

    private fun isMinuteInRange(nowMin: Int, startMin: Int, endMin: Int): Boolean {
        if (startMin == endMin) return true  // 退化：start==end 視為整天
        return if (startMin < endMin) {
            nowMin in startMin..endMin
        } else {
            // 跨日：22:00-06:00
            nowMin >= startMin || nowMin <= endMin
        }
    }
}
