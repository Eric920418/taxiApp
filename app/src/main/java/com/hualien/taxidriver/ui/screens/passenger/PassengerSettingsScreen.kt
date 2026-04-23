package com.hualien.taxidriver.ui.screens.passenger

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.hualien.taxidriver.BuildConfig

/**
 * 乘客設定頁面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassengerSettingsScreen(
    passengerId: String,
    passengerName: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current

    // 對話框狀態
    var showNotificationDialog by remember { mutableStateOf(false) }
    var showLocationDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("設定") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // 通知設定區塊
            SettingsSection(title = "通知設定") {
                SettingsItem(
                    icon = Icons.Default.Notifications,
                    title = "推播通知",
                    subtitle = "訂單狀態、司機位置更新",
                    onClick = { showNotificationDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 隱私設定區塊
            SettingsSection(title = "隱私與定位") {
                SettingsItem(
                    icon = Icons.Default.LocationOn,
                    title = "定位權限",
                    subtitle = "用於顯示您的上車位置",
                    onClick = { showLocationDialog = true }
                )

                SettingsItem(
                    icon = Icons.Default.Lock,
                    title = "隱私政策",
                    subtitle = "了解我們如何保護您的資料",
                    onClick = { showPrivacyDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 關於區塊
            SettingsSection(title = "關於") {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "關於 GoGoCha",
                    subtitle = "版本 ${BuildConfig.VERSION_NAME}",
                    onClick = { showAboutDialog = true }
                )

                SettingsItem(
                    icon = Icons.AutoMirrored.Filled.Help,
                    title = "幫助與支援",
                    subtitle = "常見問題、聯絡客服",
                    onClick = {
                        // 可以跳轉到幫助頁面或撥打客服電話
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 版本資訊
            Text(
                text = "乘客ID: $passengerId",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }

    // 通知設定對話框
    if (showNotificationDialog) {
        NotificationSettingsDialogForPassenger(
            onDismiss = { showNotificationDialog = false },
            onOpenSettings = {
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
        LocationSettingsDialogForPassenger(
            onDismiss = { showLocationDialog = false },
            onOpenSettings = {
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                context.startActivity(intent)
                showLocationDialog = false
            }
        )
    }

    // 關於對話框
    if (showAboutDialog) {
        AboutDialogForPassenger(
            onDismiss = { showAboutDialog = false }
        )
    }

    // 隱私政策對話框
    if (showPrivacyDialog) {
        PrivacyPolicyDialog(
            onDismiss = { showPrivacyDialog = false }
        )
    }
}

/**
 * 設定區塊
 */
@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(vertical = 4.dp),
                content = content
            )
        }
    }
}

/**
 * 設定項目
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "前往",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 乘客端通知設定對話框
 */
@Composable
private fun NotificationSettingsDialogForPassenger(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current

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
                PermissionStatus(
                    title = "通知權限",
                    isGranted = hasNotificationPermission
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "乘客端通知類型：",
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("• 訂單確認 - 司機接單後通知")
                Text("• 司機到達 - 司機到達上車點時通知")
                Text("• 行程完成 - 行程結束時通知")
                Text("• 活動優惠 - 促銷和優惠資訊")

                if (!hasNotificationPermission) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "請開啟通知權限，以便接收訂單狀態更新。",
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
 * 乘客端定位設定對話框
 */
@Composable
private fun LocationSettingsDialogForPassenger(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current

    val hasFineLocation = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    val hasCoarseLocation = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("定位設定")
            }
        },
        text = {
            Column {
                PermissionStatus(
                    title = "精確定位",
                    isGranted = hasFineLocation
                )

                Spacer(modifier = Modifier.height(8.dp))

                PermissionStatus(
                    title = "一般定位",
                    isGranted = hasCoarseLocation
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "定位用途說明：",
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("• 自動定位您的上車位置")
                Text("• 在地圖上顯示您的位置")
                Text("• 計算與司機的距離")
                Text("• 預估司機到達時間")

                if (!hasFineLocation) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "建議開啟精確定位以獲得更準確的上車位置。",
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
 * 權限狀態顯示
 */
@Composable
private fun PermissionStatus(
    title: String,
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
            Text(
                text = title,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
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
 * 乘客端關於對話框
 */
@Composable
private fun AboutDialogForPassenger(
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
                Text("關於 GoGoCha")
            }
        },
        text = {
            Column {
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
                            text = "GoGoCha 乘客端",
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

                Text(
                    text = "乘客端功能：",
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("• 一鍵快速叫車")
                Text("• 即時追蹤司機位置")
                Text("• 預估到達時間")
                Text("• 行程記錄查詢")
                Text("• 司機評價系統")

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "© 2026 GoGoCha",
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
 * 隱私政策對話框
 */
@Composable
private fun PrivacyPolicyDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("隱私政策")
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "我們重視您的隱私",
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "資料收集與使用：",
                    fontWeight = FontWeight.Medium
                )
                Text("• 位置資料：僅在叫車時使用，用於定位上車點和追蹤行程")
                Text("• 聯絡資訊：手機號碼用於司機聯繫和訂單通知")
                Text("• 行程記錄：用於歷史查詢和服務改善")

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "資料保護：",
                    fontWeight = FontWeight.Medium
                )
                Text("• 所有資料傳輸皆經過加密")
                Text("• 位置資料在行程結束後不會即時追蹤")
                Text("• 您可以隨時要求刪除個人資料")

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "資料共享：",
                    fontWeight = FontWeight.Medium
                )
                Text("• 行程資訊僅與接單司機共享")
                Text("• 不會將資料販售給第三方")
                Text("• 必要時配合執法單位調查")

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "如有任何疑問，請聯繫客服。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("我知道了")
            }
        }
    )
}
