package com.hualien.taxidriver.ui.screens

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hualien.taxidriver.utils.AccessibilityManager
import com.hualien.taxidriver.viewmodel.HomeViewModel

/**
 * 整合的司機介面
 * 根據設定自動選擇適合的介面：
 * 1. 標準介面 - HomeScreen
 * 2. 中老年人介面 - SeniorFriendlyHomeScreen
 * 3. 智能一鍵介面 - SimplifiedDriverScreen
 */
@Composable
fun IntegratedDriverScreen(
    driverId: String,
    driverName: String,
    viewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    val accessibilityManager = remember { AccessibilityManager(context) }

    // 收集無障礙設定
    val isSeniorMode by accessibilityManager.isSeniorMode.collectAsState(initial = false)
    val isSimplifiedUI by accessibilityManager.isSimplifiedUI.collectAsState(initial = false)

    // 根據設定選擇合適的介面
    when {
        // 如果開啟簡化UI，使用智能一鍵介面
        isSimplifiedUI -> {
            SimplifiedDriverScreen(
                driverId = driverId,
                driverName = driverName,
                viewModel = viewModel
            )
        }

        // 如果開啟中老年人模式，使用優化介面
        isSeniorMode -> {
            SeniorFriendlyHomeScreen(
                driverId = driverId,
                driverName = driverName,
                viewModel = viewModel
            )
        }

        // 預設使用標準介面
        else -> {
            HomeScreen(
                driverId = driverId,
                driverName = driverName,
                viewModel = viewModel
            )
        }
    }
}

/**
 * 使用方式：
 *
 * 在 NavGraph.kt 中替換原本的 HomeScreen：
 *
 * composable(Screen.Home.route) {
 *     IntegratedDriverScreen(
 *         driverId = driverId,
 *         driverName = driverName
 *     )
 * }
 *
 * 這樣系統會根據使用者的無障礙設定，
 * 自動選擇最適合的介面呈現方式。
 */