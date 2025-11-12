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
import com.hualien.taxidriver.ui.screens.passenger.*
import com.hualien.taxidriver.utils.RoleManager

/**
 * 乘客端主導航結構
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassengerNavigation(
    passengerId: String,
    passengerName: String,
    roleManager: RoleManager,
    onSwitchToDriver: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                Screen.passengerBottomNavItems.forEach { screen ->
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
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.PassengerHome.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.PassengerHome.route) {
                PassengerHomeScreen()
            }
            composable(Screen.PassengerOrders.route) {
                PassengerOrdersScreen(passengerId = passengerId)
            }
            composable(Screen.PassengerProfile.route) {
                PassengerProfileScreen(
                    passengerId = passengerId,
                    passengerName = passengerName,
                    roleManager = roleManager,
                    onSwitchToDriver = onSwitchToDriver
                )
            }
        }
    }
}
