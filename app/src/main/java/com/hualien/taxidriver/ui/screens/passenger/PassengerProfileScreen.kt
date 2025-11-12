package com.hualien.taxidriver.ui.screens.passenger

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hualien.taxidriver.domain.model.UserRole
import com.hualien.taxidriver.utils.RoleManager
import kotlinx.coroutines.launch

/**
 * 乘客個人資料畫面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassengerProfileScreen(
    passengerId: String,
    passengerName: String,
    roleManager: RoleManager,
    onSwitchToDriver: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var showSwitchDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("個人資料") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(24.dp)
        ) {
            // 用戶資訊卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 頭像
                    Surface(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape),
                        color = Color(0xFF4CAF50)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "用戶頭像",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            text = passengerName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "乘客",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF4CAF50)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 功能選項
            ProfileOption(
                icon = Icons.Default.Person,
                title = "個人資料",
                subtitle = "查看和編輯個人資料",
                onClick = { /* TODO: 實作個人資料編輯 */ }
            )

            ProfileOption(
                icon = Icons.Default.Star,
                title = "我的評價",
                subtitle = "查看司機給您的評價",
                onClick = { /* TODO: 實作評價查看 */ }
            )

            ProfileOption(
                icon = Icons.Default.Settings,
                title = "設定",
                subtitle = "應用程式設定",
                onClick = { /* TODO: 實作設定頁面 */ }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // 切換到司機端
            ProfileOption(
                icon = Icons.Default.Star,
                title = "切換為司機",
                subtitle = "使用司機端功能",
                onClick = { showSwitchDialog = true },
                iconTint = Color(0xFF1976D2)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 登出按鈕
            OutlinedButton(
                onClick = { showLogoutDialog = true },
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

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "版本 1.0.0 | 乘客ID: $passengerId",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }

    // 切換角色確認對話框
    if (showSwitchDialog) {
        AlertDialog(
            onDismissRequest = { showSwitchDialog = false },
            title = { Text("切換為司機") },
            text = { Text("您確定要切換到司機模式嗎？\n切換後需要使用司機帳號登入。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            roleManager.switchRole(UserRole.DRIVER)
                            showSwitchDialog = false
                            onSwitchToDriver()
                        }
                    }
                ) {
                    Text("確定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSwitchDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 登出確認對話框
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("確認登出") },
            text = { Text("您確定要登出嗎？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            roleManager.logout()
                            showLogoutDialog = false
                        }
                    }
                ) {
                    Text("登出", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 個人資料選項組件
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileOption(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    iconTint: Color = MaterialTheme.colorScheme.primary
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        )
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
                tint = iconTint,
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
                imageVector = Icons.Default.ArrowForward,
                contentDescription = "前往",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
