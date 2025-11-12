package com.hualien.taxidriver.navigation

import androidx.compose.material.icons.Icons
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
    object Orders : Screen("orders", "訂單", Icons.Default.List)
    object Earnings : Screen("earnings", "收入", Icons.Default.DateRange)
    object Profile : Screen("profile", "我的", Icons.Default.Person)

    // 乘客端路由
    object PassengerHome : Screen("passenger_home", "叫車", Icons.Default.Home)
    object PassengerOrders : Screen("passenger_orders", "訂單", Icons.Default.List)
    object PassengerProfile : Screen("passenger_profile", "我的", Icons.Default.Person)

    // 通用路由
    object RoleSelection : Screen("role_selection", "角色選擇", Icons.Default.Settings)

    companion object {
        // 司機端底部導航
        val driverBottomNavItems = listOf(Home, Orders, Earnings, Profile)

        // 乘客端底部導航
        val passengerBottomNavItems = listOf(PassengerHome, PassengerOrders, PassengerProfile)

        // 向後兼容
        val bottomNavItems = driverBottomNavItems
    }
}
