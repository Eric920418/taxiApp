package com.hualien.taxidriver.ui.screens.passenger

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.hualien.taxidriver.data.remote.WebSocketManager
import com.hualien.taxidriver.domain.model.UserRole
import com.hualien.taxidriver.utils.RoleManager
import com.hualien.taxidriver.viewmodel.PassengerProfileViewModel
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
    onSwitchToDriver: () -> Unit,
    onNavigateToRatings: (() -> Unit)? = null,
    onNavigateToSettings: (() -> Unit)? = null
) {
    val viewModel: PassengerProfileViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showSwitchDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }

    // 初始化加載資料
    LaunchedEffect(passengerId) {
        viewModel.loadProfile(passengerId)
    }

    // 處理更新成功
    LaunchedEffect(uiState.updateSuccess) {
        if (uiState.updateSuccess) {
            snackbarHostState.showSnackbar("資料已更新")
            viewModel.clearUpdateSuccess()
        }
    }

    // 使用從 API 獲取的資料或傳入的預設值
    val displayName = uiState.profile?.name ?: passengerName
    val displayPhone = uiState.profile?.phone ?: ""
    val displayEmail = uiState.profile?.email ?: ""
    val rating = uiState.profile?.rating ?: 5.0f
    val totalTrips = uiState.profile?.totalTrips ?: 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("個人資料") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = displayName,
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

                        // 編輯按鈕
                        IconButton(onClick = { showEditDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "編輯",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color(0xFF4CAF50).copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(16.dp))

                    // 統計資訊
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatColumn(
                            value = String.format("%.1f", rating),
                            label = "評分",
                            icon = Icons.Default.Star
                        )
                        StatColumn(
                            value = "$totalTrips",
                            label = "行程數",
                            icon = Icons.AutoMirrored.Filled.List
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 聯絡資訊
            if (displayPhone.isNotEmpty()) {
                InfoRow(
                    icon = Icons.Default.Phone,
                    label = "手機號碼",
                    value = displayPhone
                )
            }
            if (displayEmail.isNotEmpty()) {
                InfoRow(
                    icon = Icons.Default.Email,
                    label = "電子郵件",
                    value = displayEmail
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 功能選項
            ProfileOption(
                icon = Icons.Default.Person,
                title = "編輯個人資料",
                subtitle = "修改姓名和聯絡資訊",
                onClick = { showEditDialog = true }
            )

            ProfileOption(
                icon = Icons.Default.Star,
                title = "我的評價",
                subtitle = "評分 ${String.format("%.1f", rating)} / 5.0",
                onClick = {
                    if (onNavigateToRatings != null) {
                        onNavigateToRatings()
                    } else {
                        scope.launch {
                            snackbarHostState.showSnackbar("評價詳情功能即將推出")
                        }
                    }
                }
            )

            ProfileOption(
                icon = Icons.Default.Settings,
                title = "設定",
                subtitle = "通知、無障礙設定",
                onClick = {
                    if (onNavigateToSettings != null) {
                        onNavigateToSettings()
                    } else {
                        scope.launch {
                            snackbarHostState.showSnackbar("設定頁面即將推出")
                        }
                    }
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // 切換到司機端
            ProfileOption(
                icon = Icons.Default.DirectionsCar,
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
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = "登出"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("登出")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "版本 1.2.3 | 乘客ID: $passengerId",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }

    // 編輯個人資料對話框
    if (showEditDialog) {
        EditProfileDialog(
            currentName = displayName,
            currentEmail = displayEmail,
            isUpdating = uiState.isUpdating,
            updateError = uiState.updateError,
            onDismiss = {
                showEditDialog = false
                viewModel.clearError()
            },
            onSave = { newName, newEmail ->
                viewModel.updateProfile(passengerId, newName, newEmail)
            },
            onSaveSuccess = {
                showEditDialog = false
            }
        )

        // 當更新成功時關閉對話框
        LaunchedEffect(uiState.updateSuccess) {
            if (uiState.updateSuccess) {
                showEditDialog = false
            }
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
                            // 1. 取消 WebSocket 重連並斷開連接
                            val webSocketManager = WebSocketManager.getInstance()
                            webSocketManager.cancelReconnect()
                            webSocketManager.disconnect()

                            // 2. 登出 Firebase Auth
                            FirebaseAuth.getInstance().signOut()

                            // 3. 清除 RoleManager 資料（回到角色選擇畫面）
                            roleManager.logout()
                        }
                        showLogoutDialog = false
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
 * 編輯個人資料對話框
 */
@Composable
fun EditProfileDialog(
    currentName: String,
    currentEmail: String,
    isUpdating: Boolean,
    updateError: String?,
    onDismiss: () -> Unit,
    onSave: (name: String, email: String) -> Unit,
    onSaveSuccess: () -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    var email by remember { mutableStateOf(currentEmail) }

    AlertDialog(
        onDismissRequest = { if (!isUpdating) onDismiss() },
        title = { Text("編輯個人資料") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("姓名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isUpdating
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("電子郵件（選填）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isUpdating
                )

                if (updateError != null) {
                    Text(
                        text = updateError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, email) },
                enabled = !isUpdating && name.isNotBlank()
            ) {
                if (isUpdating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("儲存")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isUpdating
            ) {
                Text("取消")
            }
        }
    )
}

/**
 * 統計欄位
 */
@Composable
fun StatColumn(
    value: String,
    label: String,
    icon: ImageVector
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color(0xFF4CAF50),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 資訊列
 */
@Composable
fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium
            )
        }
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
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "前往",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
