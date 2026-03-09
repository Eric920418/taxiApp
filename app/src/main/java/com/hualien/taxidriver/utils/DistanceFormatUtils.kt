package com.hualien.taxidriver.utils

import java.util.Locale

/**
 * 距離顯示格式化工具
 */
fun Double.formatKilometers(unit: String = "km", separator: String = " "): String {
    return "${String.format(Locale.US, "%.1f", this)}$separator$unit"
}
