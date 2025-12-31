package com.hualien.taxidriver.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.hualien.taxidriver.BuildConfig
import com.hualien.taxidriver.service.FcmTokenManager
import com.hualien.taxidriver.utils.DataStoreManager
import kotlinx.coroutines.launch

/**
 * 個人資料/設定畫面
 */
@Composable
fun ProfileScreen(
    driverId: String,
    driverName: String,
    dataStoreManager: DataStoreManager,
    onLogout: () -> Unit = {}
) {
    // 獲取司機的其他信息
    val driverPhone by dataStoreManager.driverPhone.collectAsState(initial = "")
    val driverPlate by dataStoreManager.driverPlate.collectAsState(initial = "")
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // 對話框狀態
    var showNotificationDialog by remember { mutableStateOf(false) }
    var showLocationDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showLogoutConfirmDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // 頂部個人資訊卡片
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "👤",
                    style = MaterialTheme.typography.displayMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = driverName,
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "司機 ID：$driverId",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                if (!driverPlate.isNullOrEmpty()) {
                    Text(
                        text = "車牌：$driverPlate",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                if (!driverPhone.isNullOrEmpty()) {
                    Text(
                        text = "電話：$driverPhone",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // 設定選項列表
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "設定",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            SettingItem(
                icon = Icons.Default.Notifications,
                title = "通知設定",
                onClick = { showNotificationDialog = true }
            )

            SettingItem(
                icon = Icons.Default.Place,
                title = "定位設定",
                onClick = { showLocationDialog = true }
            )

            SettingItem(
                icon = Icons.Default.Info,
                title = "關於",
                onClick = { showAboutDialog = true }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 登出按鈕
            OutlinedButton(
                onClick = { showLogoutConfirmDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = "登出"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("登出")
            }
        }
    }

    // 通知設定對話框
    if (showNotificationDialog) {
        NotificationSettingsDialog(
            onDismiss = { showNotificationDialog = false },
            onOpenSettings = {
                // 打開系統通知設定
                val intent = Intent().apply {
                    action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
                context.startActivity(intent)
                showNotificationDialog = false
            }
        )
    }

    // 定位設定對話框
    if (showLocationDialog) {
        LocationSettingsDialog(
            onDismiss = { showLocationDialog = false },
            onOpenSettings = {
                // 打開系統定位設定
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                context.startActivity(intent)
                showLocationDialog = false
            }
        )
    }

    // 關於對話框
    if (showAboutDialog) {
        AboutDialog(
            onDismiss = { showAboutDialog = false }
        )
    }

    // 登出確認對話框
    if (showLogoutConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirmDialog = false },
            title = { Text("確認登出") },
            text = { Text("您確定要登出嗎？登出後需要重新登入才能使用。") },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            // 清除伺服器上的 FCM Token
                            FcmTokenManager.clearTokenOnLogout(context, driverId)
                            // 清除所有登錄數據
                            dataStoreManager.clearLoginData()
                            // 調用登出回調
                            onLogout()
                        }
                        showLogoutConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("確定登出")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirmDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 通知設定對話框
 */
@Composable
private fun NotificationSettingsDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current

    // 檢查通知權限狀態
    val hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("通知設定")
            }
        },
        text = {
            Column {
                // 通知權限狀態
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (hasNotificationPermission)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (hasNotificationPermission)
                                Icons.Default.Check else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (hasNotificationPermission)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "通知權限",
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (hasNotificationPermission) "已開啟" else "未開啟",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "通知類型說明：",
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("• 新訂單通知 - 高優先級，會震動提醒")
                Text("• 訂單狀態更新 - 一般優先級")
                Text("• 系統消息 - 低優先級")

                if (!hasNotificationPermission) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "請前往設定開啟通知權限，以便接收新訂單提醒。",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onOpenSettings) {
                Text("前往系統設定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("關閉")
            }
        }
    )
}

/**
 * 定位設定對話框
 */
@Composable
private fun LocationSettingsDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current

    // 檢查定位權限
    val hasFineLocation = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    val hasCoarseLocation = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    val hasBackgroundLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("定位設定")
            }
        },
        text = {
            Column {
                // 精確定位
                PermissionStatusRow(
                    title = "精確定位",
                    description = "GPS 高精度定位",
                    isGranted = hasFineLocation
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 粗略定位
                PermissionStatusRow(
                    title = "一般定位",
                    description = "WiFi/基站定位",
                    isGranted = hasCoarseLocation
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Spacer(modifier = Modifier.height(8.dp))

                    // 背景定位
                    PermissionStatusRow(
                        title = "背景定位",
                        description = "應用在背景時持續定位",
                        isGranted = hasBackgroundLocation
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "定位說明：",
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("• 精確定位用於準確報告您的位置給乘客")
                Text("• 背景定位用於在行程中持續追蹤位置")
                Text("• 定位數據僅用於派單和行程追蹤")

                if (!hasFineLocation || !hasBackgroundLocation) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "建議開啟所有定位權限以獲得最佳體驗。",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onOpenSettings) {
                Text("前往系統設定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("關閉")
            }
        }
    )
}

/**
 * 權限狀態行
 */
@Composable
private fun PermissionStatusRow(
    title: String,
    description: String,
    isGranted: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isGranted) Icons.Default.Check else Icons.Default.Close,
                contentDescription = null,
                tint = if (isGranted)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = if (isGranted) "已開啟" else "未開啟",
                style = MaterialTheme.typography.bodySmall,
                color = if (isGranted)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.error
            )
        }
    }
}

/**
 * 關於對話框
 */
@Composable
private fun AboutDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("關於花蓮計程車")
            }
        },
        text = {
            Column {
                // 應用名稱和圖標
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "🚕",
                            style = MaterialTheme.typography.displayMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "花蓮計程車司機端",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "版本 ${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 應用資訊
                InfoRow("版本號碼", BuildConfig.VERSION_NAME)
                InfoRow("建置版本", BuildConfig.VERSION_CODE.toString())
                InfoRow("最低 Android 版本", "Android 8.0 (API 26)")

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "功能特色：",
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("• 即時訂單推播通知")
                Text("• GPS 智能定位追蹤")
                Text("• 一鍵導航到上車點")
                Text("• 行程收益即時統計")
                Text("• 中老年人友善介面")

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "© 2024 花蓮計程車服務",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("關閉")
            }
        }
    )
}

/**
 * 資訊行
 */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun SettingItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "進入",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
