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

/**
 * App主導航結構
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigation(
    driverId: String,
    driverName: String,
    dataStoreManager: DataStoreManager,
    onLogout: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        bottomBar = {
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
                                // 避免重複建立同一個destination
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                // 避免同一個item被點擊多次產生多個實例
                                launchSingleTop = true
                                // 重新選擇前一個item時，恢復狀態
                                restoreState = true
                            }
                        }
                    )
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
                    driverName = driverName
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
                    onLogout = onLogout
                )
            }
        }
    }
}
