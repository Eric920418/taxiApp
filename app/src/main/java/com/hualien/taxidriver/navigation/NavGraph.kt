package com.hualien.taxidriver.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.hualien.taxidriver.ui.screens.*
import com.hualien.taxidriver.utils.DataStoreManager
import com.hualien.taxidriver.utils.RoleManager

/**
 * App主導航結構
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigation(
    driverId: String,
    driverName: String,
    dataStoreManager: DataStoreManager,
    roleManager: RoleManager,
    onLogout: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        bottomBar = {
            // v1.3.0：司機端底部導航完全移除，全部功能改由首頁 8 按鈕網格進入
            if (Screen.bottomNavItems.isNotEmpty()) {
                NavigationBar {
                    Screen.bottomNavItems.forEach { screen ->
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
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    driverId = driverId,
                    driverName = driverName,
                    onNavigateToOrders = {
                        navController.navigate(Screen.Orders.route)
                    },
                    onNavigateToEarnings = {
                        navController.navigate(Screen.Earnings.route)
                    },
                    onNavigateToProfile = {
                        navController.navigate(Screen.Profile.route)
                    },
                    onNavigateToPhoneReview = {
                        navController.navigate(Screen.PhoneReview.route)
                    },
                    onLogout = onLogout,
                )
            }
            composable(Screen.Orders.route) {
                OrdersScreen(driverId = driverId)
            }
            composable(Screen.Earnings.route) {
                EarningsScreen(driverId = driverId)
            }
            composable(Screen.Profile.route) {
                ProfileScreen(
                    driverId = driverId,
                    driverName = driverName,
                    dataStoreManager = dataStoreManager,
                    roleManager = roleManager,
                    onLogout = onLogout,
                    onNavigateToAutoAccept = {
                        navController.navigate(Screen.AutoAcceptSettings.route)
                    },
                    onNavigateToAccessibility = {
                        navController.navigate(Screen.AccessibilitySettings.route)
                    }
                )
            }

            // 自動接單設定
            composable(Screen.AutoAcceptSettings.route) {
                AutoAcceptSettingsScreen(
                    driverId = driverId,
                    onBackClick = { navController.popBackStack() }
                )
            }

            // 無障礙設定
            composable(Screen.AccessibilitySettings.route) {
                AccessibilitySettingsScreen(
                    onBackClick = { navController.popBackStack() }
                )
            }

            // 電話客服審核
            composable(Screen.PhoneReview.route) {
                PhoneReviewScreen(
                    driverId = driverId,
                    onBackClick = { navController.popBackStack() }
                )
            }
        }
    }
}
