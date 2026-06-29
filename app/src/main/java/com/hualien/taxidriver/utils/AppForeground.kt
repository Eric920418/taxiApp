package com.hualien.taxidriver.utils

/**
 * 極簡前景旗標：MainActivity onResume/onPause 維護。
 *
 * 用途：FCM / LocationService 收到新訂單時判斷 App 是否在前景 —— 前景則交給 in-app 卡片，
 * 不重複彈全螢幕通知；背景（含被殺，預設 false）才彈。Volatile 即可，不需精準同步。
 */
object AppForeground {
    @Volatile
    var isForeground: Boolean = false
}
