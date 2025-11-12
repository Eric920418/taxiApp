package com.hualien.taxidriver.utils

/**
 * 地址格式化工具
 */
object AddressUtils {

    /**
     * 縮短地址顯示
     * 例如：台灣花蓮縣花蓮市中山路1號 → 花蓮市中山路1號
     */
    fun shortenAddress(address: String, maxLength: Int = 30): String {
        if (address.length <= maxLength) {
            return address
        }

        // 移除 "台灣" 前綴
        var shortened = address
            .removePrefix("台灣")
            .removePrefix("臺灣")
            .trim()

        // 移除縣市重複
        shortened = shortened
            .replace("花蓮縣花蓮市", "花蓮市")
            .replace("花蓮縣", "")

        // 如果還是太長，截斷
        if (shortened.length > maxLength) {
            shortened = shortened.take(maxLength - 3) + "..."
        }

        return shortened
    }

    /**
     * 從地址提取主要部分（用於簡短顯示）
     */
    fun getMainPart(address: String): String {
        // 嘗試提取街道和門牌號
        val parts = address.split("縣", "市", "區", "鄉", "鎮")
        return if (parts.size > 1) {
            parts.last().trim()
        } else {
            shortenAddress(address, 20)
        }
    }

    /**
     * 格式化距離顯示
     */
    fun formatDistance(meters: Int): String {
        return when {
            meters < 1000 -> "${meters}公尺"
            else -> "%.1f公里".format(meters / 1000.0)
        }
    }

    /**
     * 格式化預估時間
     */
    fun formatETA(minutes: Int): String {
        return when {
            minutes < 1 -> "少於1分鐘"
            minutes < 60 -> "${minutes}分鐘"
            else -> "${minutes / 60}小時${minutes % 60}分鐘"
        }
    }

    /**
     * 檢查地址是否有效
     */
    fun isValidAddress(address: String): Boolean {
        return address.isNotBlank() &&
               address != "未知地址" &&
               address.length >= 5
    }
}
