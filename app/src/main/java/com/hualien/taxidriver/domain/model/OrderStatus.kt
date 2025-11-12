package com.hualien.taxidriver.domain.model

/**
 * 訂單狀態
 */
enum class OrderStatus {
    IDLE,        // 閒置
    WAITING,     // 等待中
    OFFERED,     // 已派單
    ACCEPTED,    // 已接單
    ARRIVED,     // 已到達
    ON_TRIP,     // 行程中
    SETTLING,    // 結算中
    DONE,        // 已完成
    CANCELLED;   // 已取消

    companion object {
        fun fromString(value: String): OrderStatus {
            return entries.find { it.name == value.uppercase() } ?: WAITING
        }
    }

    /**
     * 取得中文描述
     */
    fun getDisplayName(): String = when (this) {
        IDLE -> "閒置"
        WAITING -> "等待中"
        OFFERED -> "已派單"
        ACCEPTED -> "已接單"
        ARRIVED -> "已到達"
        ON_TRIP -> "行程中"
        SETTLING -> "結算中"
        DONE -> "已完成"
        CANCELLED -> "已取消"
    }

    /**
     * 取得顏色
     */
    fun getColor(): androidx.compose.ui.graphics.Color = when (this) {
        IDLE -> androidx.compose.ui.graphics.Color(0xFF9E9E9E) // Gray
        WAITING -> androidx.compose.ui.graphics.Color(0xFFFF9800) // Orange
        OFFERED -> androidx.compose.ui.graphics.Color(0xFF2196F3) // Blue
        ACCEPTED -> androidx.compose.ui.graphics.Color(0xFF4CAF50) // Green
        ARRIVED -> androidx.compose.ui.graphics.Color(0xFF9C27B0) // Purple
        ON_TRIP -> androidx.compose.ui.graphics.Color(0xFF00BCD4) // Cyan
        SETTLING -> androidx.compose.ui.graphics.Color(0xFFFFEB3B) // Yellow
        DONE -> androidx.compose.ui.graphics.Color(0xFF8BC34A) // Light Green
        CANCELLED -> androidx.compose.ui.graphics.Color(0xFFF44336) // Red
    }
}
