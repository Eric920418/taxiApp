package com.hualien.taxidriver.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
                onClick = { /* TODO */ }
            )

            SettingItem(
                icon = Icons.Default.Place,
                title = "定位設定",
                onClick = { /* TODO */ }
            )

            SettingItem(
                icon = Icons.Default.Info,
                title = "關於",
                onClick = { /* TODO */ }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 登出按鈕
            OutlinedButton(
                onClick = {
                    coroutineScope.launch {
                        // 清除所有登錄數據
                        dataStoreManager.clearLoginData()
                        // 調用登出回調
                        onLogout()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    imageVector = Icons.Default.ExitToApp,
                    contentDescription = "登出"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("登出")
            }
        }
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
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = "進入",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
