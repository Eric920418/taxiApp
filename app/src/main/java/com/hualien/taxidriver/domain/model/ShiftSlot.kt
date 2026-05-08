package com.hualien.taxidriver.domain.model

import com.google.gson.annotations.SerializedName

/**
 * 司機班次設定（mirror server-side ShiftSlot）
 *
 * 後端 schema: drivers.shifts JSONB = [{shift_type, start, end, is_active}]
 * shift_type: MORNING / AFTERNOON / EVENING / NIGHT
 * start / end: HH:MM 格式（Asia/Taipei 時區）
 * is_active: 是否啟用此時段
 */
data class ShiftSlot(
    @SerializedName("shift_type")
    val shiftType: String = "",

    @SerializedName("start")
    val start: String = "",

    @SerializedName("end")
    val end: String = "",

    @SerializedName("is_active")
    val isActive: Boolean = false,
)
