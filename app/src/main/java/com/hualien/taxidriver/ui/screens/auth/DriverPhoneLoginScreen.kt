package com.hualien.taxidriver.ui.screens.auth

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hualien.taxidriver.domain.model.UserRole
import com.hualien.taxidriver.utils.DataStoreManager
import com.hualien.taxidriver.utils.RoleManager
import com.hualien.taxidriver.viewmodel.*
import kotlinx.coroutines.launch

/**
 * 司機端簡訊登入畫面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverPhoneLoginScreen(
    dataStoreManager: DataStoreManager,
    roleManager: RoleManager,
    onLoginSuccess: () -> Unit = {}
) {
    val viewModel: PhoneAuthViewModel = viewModel()
    val phoneAuthState by viewModel.phoneAuthState.collectAsState()
    val backendAuthState by viewModel.backendAuthState.collectAsState()

    var phone by remember { mutableStateOf("") }
    var verificationCode by remember { mutableStateOf("") }
    var showCodeInput by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 處理系統返回鍵
    BackHandler {
        scope.launch {
            roleManager.logout()
        }
    }

    // 處理 Firebase Phone Auth 狀態
    LaunchedEffect(phoneAuthState) {
        when (val state = phoneAuthState) {
            is PhoneAuthState.CodeSent -> {
                showCodeInput = true
                Toast.makeText(
                    context,
                    "驗證碼已發送到 $phone",
                    Toast.LENGTH_LONG
                ).show()
            }
            is PhoneAuthState.VerificationSuccess -> {
                // Firebase 驗證成功，開始向後端驗證
                viewModel.verifyWithBackendDriver(state.firebaseUid, state.phone)
            }
            is PhoneAuthState.Error -> {
                Toast.makeText(
                    context,
                    state.message,
                    Toast.LENGTH_LONG
                ).show()
            }
            else -> {}
        }
    }

    // 處理後端驗證狀態
    LaunchedEffect(backendAuthState) {
        when (val state = backendAuthState) {
            is BackendAuthState.DriverSuccess -> {
                // 保存登錄信息到 DataStore
                dataStoreManager.saveLoginData(
                    token = state.token,
                    driverId = state.driverId,
                    name = state.name,
                    phone = state.phone,
                    plate = state.plate
                )

                // 同步更新 RoleManager
                roleManager.setCurrentRole(
                    role = UserRole.DRIVER,
                    userId = state.driverId,
                    userName = state.name,
                    phone = state.phone
                )

                Toast.makeText(
                    context,
                    "登入成功！歡迎 ${state.name}",
                    Toast.LENGTH_LONG
                ).show()

                onLoginSuccess()
            }
            is BackendAuthState.Error -> {
                Toast.makeText(
                    context,
                    state.message,
                    Toast.LENGTH_LONG
                ).show()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("") },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (showCodeInput) {
                                showCodeInput = false
                                verificationCode = ""
                                viewModel.resetState()
                            } else {
                                scope.launch {
                                    roleManager.logout()
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo/標題
            Text(
                text = "🚕 花蓮計程車",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "司機端",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary
            )

            Spacer(modifier = Modifier.height(48.dp))

            if (!showCodeInput) {
                // 步驟 1: 輸入手機號碼
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("手機號碼") },
                    placeholder = { Text("0912345678") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = phoneAuthState !is PhoneAuthState.SendingCode
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        (context as? androidx.activity.ComponentActivity)?.let { activity ->
                            viewModel.sendVerificationCode(phone, activity)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = phone.isNotBlank() && phoneAuthState !is PhoneAuthState.SendingCode
                ) {
                    if (phoneAuthState is PhoneAuthState.SendingCode) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("發送驗證碼", style = MaterialTheme.typography.titleMedium)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 提示卡片
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "簡訊驗證登入",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "請輸入註冊的手機號碼，系統將發送驗證碼簡訊",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "測試號碼：0912345678, 0987654321",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // 步驟 2: 輸入驗證碼
                Text(
                    text = "驗證碼已發送至",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = phone,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = verificationCode,
                    onValueChange = { verificationCode = it },
                    label = { Text("驗證碼") },
                    placeholder = { Text("請輸入 6 位數驗證碼") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = phoneAuthState !is PhoneAuthState.Verifying &&
                            backendAuthState !is BackendAuthState.Loading
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        viewModel.verifyCode(verificationCode)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = verificationCode.length == 6 &&
                            phoneAuthState !is PhoneAuthState.Verifying &&
                            backendAuthState !is BackendAuthState.Loading
                ) {
                    if (phoneAuthState is PhoneAuthState.Verifying ||
                        backendAuthState is BackendAuthState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("驗證並登入", style = MaterialTheme.typography.titleMedium)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(
                    onClick = {
                        (context as? androidx.activity.ComponentActivity)?.let { activity ->
                            viewModel.sendVerificationCode(phone, activity)
                        }
                    }
                ) {
                    Text("重新發送驗證碼")
                }
            }
        }
    }
}
