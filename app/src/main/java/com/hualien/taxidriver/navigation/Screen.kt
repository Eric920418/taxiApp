package com.hualien.taxidriver.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 導航路由定義
 */
sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    // 司機端路由
    object Home : Screen("home", "主頁", Icons.Default.Home)
    object Orders : Screen("orders", "訂單", Icons.AutoMirrored.Filled.List)
    object Earnings : Screen("earnings", "收入", Icons.Default.DateRange)
    object Profile : Screen("profile", "我的", Icons.Default.Person)

    // 乘客端路由
    object PassengerHome : Screen("passenger_home", "叫車", Icons.Default.Home)
    object PassengerVoiceFirst : Screen("passenger_voice_first", "語音", Icons.Default.Mic)  // 語音優先介面
    object PassengerMapMode : Screen("passenger_map_mode", "地圖模式", Icons.Default.Map)    // 傳統地圖介面
    object PassengerOrders : Screen("passenger_orders", "訂單", Icons.AutoMirrored.Filled.List)
    object PassengerProfile : Screen("passenger_profile", "我的", Icons.Default.Person)
    object PassengerRatings : Screen("passenger_ratings", "我的評價", Icons.Default.Star)
    object PassengerSettings : Screen("passenger_settings", "設定", Icons.Default.Settings)

    // 司機端設定路由
    object AutoAcceptSettings : Screen("auto_accept_settings", "AI自動接單", Icons.Default.Star)
    object AccessibilitySettings : Screen("accessibility_settings", "無障礙設定", Icons.Default.Settings)

    // 通用路由
    object RoleSelection : Screen("role_selection", "角色選擇", Icons.Default.Settings)

    companion object {
        // 司機端底部導航
        val driverBottomNavItems: List<Screen> by lazy {
            listOf(Home, Orders, Earnings, Profile)
        }

        // 乘客端底部導航（語音模式為首頁）
        val passengerBottomNavItems: List<Screen> by lazy {
            listOf(PassengerVoiceFirst, PassengerOrders, PassengerProfile)
        }

        // 向後兼容
        val bottomNavItems: List<Screen> by lazy { driverBottomNavItems }
    }
}
