package com.hualien.taxidriver.navigation

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.hualien.taxidriver.ui.screens.passenger.*
import com.hualien.taxidriver.utils.RoleManager
import com.hualien.taxidriver.viewmodel.PassengerViewModel

private const val TAG = "PassengerNavGraph"

/**
 * 乘客端主導航結構
 *
 * 預設使用語音優先介面（VoiceFirstPassengerScreen）
 * 可切換到傳統地圖模式（PassengerHomeScreen）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassengerNavigation(
    passengerId: String,
    passengerName: String,
    passengerPhone: String,
    roleManager: RoleManager,
    onSwitchToDriver: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // 在 NavGraph 層級創建共享的 ViewModel，確保語音模式和地圖模式共享訂單狀態
    val sharedViewModel: PassengerViewModel = viewModel()

    // 判斷是否在語音模式（語音模式不需要底部導航，地圖模式需要）
    val isFullScreenMode = currentDestination?.route == Screen.PassengerVoiceFirst.route

    Log.d(TAG, "當前路由: ${currentDestination?.route}, 是否全螢幕: $isFullScreenMode")

    Scaffold(
        bottomBar = {
            // 語音模式和地圖模式不顯示底部導航
            if (!isFullScreenMode) {
                NavigationBar {
                    Screen.passengerBottomNavItems.filterNotNull().forEach { screen ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.route == screen.route
                        } == true

                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = screen.icon,
                                    contentDescription = screen.title
                                )
                            },
                            label = { Text(screen.title) },
                            selected = selected,
                            onClick = {
                                Log.d(TAG, "點擊底部導航: ${screen.title} (${screen.route})")
                                // 如果點擊的是語音模式，直接返回起始頁面
                                if (screen.route == Screen.PassengerVoiceFirst.route) {
                                    // 清空返回堆疊，直接回到語音首頁
                                    navController.popBackStack(
                                        route = Screen.PassengerVoiceFirst.route,
                                        inclusive = false
                                    )
                                } else {
                                    // 其他頁面使用標準導航
                                    navController.navigate(screen.route) {
                                        popUpTo(Screen.PassengerVoiceFirst.route) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                                Log.d(TAG, "導航完成")
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            // 預設進入語音優先介面
            startDestination = Screen.PassengerVoiceFirst.route,
            modifier = if (isFullScreenMode) Modifier else Modifier.padding(innerPadding)
        ) {
            // 語音優先介面（新的預設首頁）
            composable(
                route = Screen.PassengerVoiceFirst.route
            ) {
                Log.d(TAG, "🟢 VoiceFirst 畫面正在渲染")
                VoiceFirstPassengerScreen(
                    passengerId = passengerId,
                    passengerName = passengerName,
                    passengerPhone = passengerPhone,
                    viewModel = sharedViewModel,
                    onNavigateToMap = {
                        Log.d(TAG, "執行 onNavigateToMap - 導航到 ${Screen.PassengerMapMode.route}")
                        navController.navigate(Screen.PassengerMapMode.route)
                        Log.d(TAG, "導航完成")
                    },
                    onNavigateToSettings = {
                        navController.navigate(Screen.PassengerSettings.route)
                    }
                )
            }

            // 傳統地圖模式
            composable(
                route = Screen.PassengerMapMode.route
            ) {
                Log.d(TAG, "🗺️ MapMode 畫面正在渲染")
                PassengerHomeScreen(
                    passengerId = passengerId,
                    passengerName = passengerName,
                    passengerPhone = passengerPhone,
                    viewModel = sharedViewModel
                )
            }

            // 舊的首頁路由（向後兼容）
            composable(Screen.PassengerHome.route) {
                // 重定向到語音優先介面
                VoiceFirstPassengerScreen(
                    passengerId = passengerId,
                    passengerName = passengerName,
                    passengerPhone = passengerPhone,
                    viewModel = sharedViewModel,
                    onNavigateToMap = {
                        navController.navigate(Screen.PassengerMapMode.route)
                    },
                    onNavigateToSettings = {
                        navController.navigate(Screen.PassengerSettings.route)
                    }
                )
            }

            composable(Screen.PassengerOrders.route) {
                PassengerOrdersScreen(passengerId = passengerId)
            }

            composable(Screen.PassengerProfile.route) {
                PassengerProfileScreen(
                    passengerId = passengerId,
                    passengerName = passengerName,
                    roleManager = roleManager,
                    onSwitchToDriver = onSwitchToDriver,
                    onNavigateToRatings = {
                        navController.navigate(Screen.PassengerRatings.route)
                    },
                    onNavigateToSettings = {
                        navController.navigate(Screen.PassengerSettings.route)
                    }
                )
            }

            composable(Screen.PassengerRatings.route) {
                PassengerRatingsScreen(
                    passengerId = passengerId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.PassengerSettings.route) {
                PassengerSettingsScreen(
                    passengerId = passengerId,
                    passengerName = passengerName,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
