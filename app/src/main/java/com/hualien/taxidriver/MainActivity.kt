package com.hualien.taxidriver

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hualien.taxidriver.data.remote.RetrofitClient
import com.hualien.taxidriver.data.repository.LandmarkSyncRepository
import com.hualien.taxidriver.domain.model.UserRole
import com.hualien.taxidriver.utils.FareCalculator
import com.hualien.taxidriver.navigation.MainNavigation
import com.hualien.taxidriver.navigation.PassengerNavigation
import com.hualien.taxidriver.ui.screens.auth.DriverPhoneLoginScreen
import com.hualien.taxidriver.ui.screens.auth.PassengerPhoneLoginScreen
import com.hualien.taxidriver.ui.screens.common.RoleSelectionScreen
import com.hualien.taxidriver.ui.theme.HualienTaxiDriverTheme
import com.hualien.taxidriver.utils.AuthManager
import com.hualien.taxidriver.utils.AuthState
import com.hualien.taxidriver.utils.DataStoreManager
import com.hualien.taxidriver.utils.PassengerNotificationHelper
import com.hualien.taxidriver.utils.RoleManager
import com.hualien.taxidriver.viewmodel.LoginUiState
import com.hualien.taxidriver.viewmodel.LoginViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {

    private lateinit var dataStoreManager: DataStoreManager
    private lateinit var roleManager: RoleManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化 RetrofitClient（必須在使用 API 之前調用）
        RetrofitClient.init(this)

        // 初始化管理器
        dataStoreManager = DataStoreManager.getInstance(this)
        roleManager = RoleManager(this)

        // 初始化認證管理器（必須晚於 DataStoreManager / RoleManager）
        // Firebase Auth 為唯一真實來源；本地 DataStore 降為營運資料快取
        AuthManager.init(this, dataStoreManager, roleManager)

        // 初始化乘客通知頻道（司機到達通知等）
        PassengerNotificationHelper.createNotificationChannels(this)

        // 初始化 token 緩存（避免 AuthInterceptor 使用 runBlocking）
        // reconcileAtStartup 修復上次 App 被殺或登出流程中斷的不一致狀態
        // 同時從 Server 載入費率配置
        lifecycleScope.launch {
            dataStoreManager.initializeTokenCache()
            AuthManager.getInstance().reconcileAtStartup()
            // 從 Server 載入費率配置（失敗時使用預設值）
            FareCalculator.loadConfigFromServer()
        }

        // 背景同步地標清單（失敗時沿用 hardcoded fallback，不阻斷啟動）
        LandmarkSyncRepository(RetrofitClient.passengerApiService).syncInBackground()

        enableEdgeToEdge()
        setContent {
            HualienTaxiDriverTheme {
                AppContent(dataStoreManager, roleManager)
            }
        }
    }
}

@Composable
fun AppContent(dataStoreManager: DataStoreManager, roleManager: RoleManager) {
    // Firebase Auth 為唯一真實來源
    val authState by AuthManager.getInstance().authState.collectAsState()

    // 角色選擇（與 auth state 正交，決定進司機端還是乘客端 UI）
    val currentRole by roleManager.currentRole.collectAsState(initial = null)

    // 營運資料快取（僅用於 UI 顯示，不參與已登入判斷）
    val driverId by dataStoreManager.driverId.collectAsState(initial = null)
    val driverName by dataStoreManager.driverName.collectAsState(initial = null)
    val passengerId by roleManager.userId.collectAsState(initial = null)
    val passengerName by roleManager.userName.collectAsState(initial = null)
    val passengerPhone by roleManager.userPhone.collectAsState(initial = null)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        when (val auth = authState) {
            is AuthState.Loading -> {
                // AuthStateListener 首次回呼前的過渡狀態，避免閃過登入畫面
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is AuthState.Unauthenticated -> when (currentRole) {
                null -> {
                    val scope = rememberCoroutineScope()
                    RoleSelectionScreen(
                        onRoleSelected = { role ->
                            scope.launch { roleManager.switchRole(role) }
                        }
                    )
                }
                UserRole.DRIVER -> DriverPhoneLoginScreen(
                    dataStoreManager = dataStoreManager,
                    roleManager = roleManager,
                    onLoginSuccess = {}
                )
                UserRole.PASSENGER -> PassengerPhoneLoginScreen(
                    roleManager = roleManager,
                    onLoginSuccess = {}
                )
            }

            is AuthState.Authenticated -> when (currentRole) {
                null -> {
                    // Firebase 已登入但未選角色（極少見，reconcile 後通常不會到這裡）
                    val scope = rememberCoroutineScope()
                    RoleSelectionScreen(
                        onRoleSelected = { role ->
                            scope.launch { roleManager.switchRole(role) }
                        }
                    )
                }
                UserRole.DRIVER -> {
                    if (driverId.isNullOrEmpty()) {
                        // Firebase 有 session 但本地司機快取缺失 → 走登入流程補資料
                        // （正常情況下 reconcileAtStartup 會攔截，這裡是防守）
                        DriverPhoneLoginScreen(
                            dataStoreManager = dataStoreManager,
                            roleManager = roleManager,
                            onLoginSuccess = {}
                        )
                    } else {
                        MainNavigation(
                            driverId = driverId ?: "",
                            driverName = driverName ?: "",
                            dataStoreManager = dataStoreManager,
                            roleManager = roleManager,
                            onLogout = {}
                        )
                    }
                }
                UserRole.PASSENGER -> {
                    if (passengerId.isNullOrEmpty()) {
                        PassengerPhoneLoginScreen(
                            roleManager = roleManager,
                            onLoginSuccess = {}
                        )
                    } else {
                        PassengerNavigation(
                            passengerId = passengerId ?: "",
                            passengerName = passengerName ?: "",
                            passengerPhone = passengerPhone ?: "",
                            roleManager = roleManager,
                            onSwitchToDriver = {}
                        )
                    }
                }
            }
        }
    }
}

/**
 * 司機登入畫面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverLoginScreen(
    dataStoreManager: DataStoreManager,
    roleManager: RoleManager,
    onLoginSuccess: () -> Unit = {}
) {
    // 創建 LoginViewModel 並傳入 DataStoreManager
    val viewModel: LoginViewModel = remember {
        LoginViewModel(dataStoreManager)
    }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 處理系統返回鍵
    BackHandler {
        scope.launch {
            roleManager.logout()
        }
    }

    // 處理登入狀態
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is LoginUiState.Success -> {
                // 同步更新 RoleManager
                roleManager.setCurrentRole(
                    role = UserRole.DRIVER,
                    userId = state.response.driverId,
                    userName = state.response.name,
                    phone = state.response.phone
                )

                Toast.makeText(
                    context,
                    "登入成功！歡迎 ${state.response.name}",
                    Toast.LENGTH_LONG
                ).show()
                // 導航到主畫面
                onLoginSuccess()
            }
            is LoginUiState.Error -> {
                Toast.makeText(
                    context,
                    state.message,
                    Toast.LENGTH_LONG
                ).show()
                viewModel.resetState()
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
                            scope.launch {
                                roleManager.logout()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回選擇角色"
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
            text = "🚕 GoGoCha",
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

        // 手機號碼輸入
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("手機號碼") },
            placeholder = { Text("0912345678") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = uiState !is LoginUiState.Loading
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 密碼輸入
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("密碼") },
            placeholder = { Text("請輸入密碼") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            enabled = uiState !is LoginUiState.Loading
        )

        Spacer(modifier = Modifier.height(32.dp))

        // 登入按鈕
        Button(
            onClick = {
                viewModel.login(phone, password)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = phone.isNotBlank() &&
                     password.isNotBlank() &&
                     uiState !is LoginUiState.Loading
        ) {
            if (uiState is LoginUiState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("登入", style = MaterialTheme.typography.titleMedium)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Server狀態指示
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
                    text = "測試帳號",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "手機：0912345678 或 0987654321",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "密碼：123456",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "伺服器：${BuildConfig.SERVER_URL}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 快速填入按鈕（測試用）
        OutlinedButton(
            onClick = {
                phone = "0912345678"
                password = "123456"
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("快速填入測試帳號")
        }
        }
    }
}

/**
 * 乘客登入畫面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassengerLoginScreen(
    roleManager: RoleManager,
    onLoginSuccess: () -> Unit = {}
) {
    var phone by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 處理系統返回鍵
    BackHandler {
        scope.launch {
            roleManager.logout()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("") },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                roleManager.logout()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回選擇角色"
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
            text = "🚕 GoGoCha",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "乘客端",
            style = MaterialTheme.typography.titleMedium,
            color = Color(0xFF4CAF50)
        )

        Spacer(modifier = Modifier.height(48.dp))

        // 手機號碼輸入
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("手機號碼") },
            placeholder = { Text("輸入手機號碼即可登入") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Phone,
                    contentDescription = "手機號碼",
                    tint = Color(0xFF4CAF50)
                )
            }
        )

        Spacer(modifier = Modifier.height(32.dp))

        // 登入按鈕
        Button(
            onClick = {
                isLoading = true
                scope.launch {
                    try {
                        // TODO: 實作真實的乘客登入 API
                        // 目前使用模擬登入
                        kotlinx.coroutines.delay(1000)

                        // 模擬登入成功
                        roleManager.setCurrentRole(
                            role = UserRole.PASSENGER,
                            userId = "passenger_${phone.takeLast(4)}",
                            userName = "乘客 ${phone.takeLast(4)}",
                            phone = phone
                        )

                        Toast.makeText(
                            context,
                            "登入成功！歡迎使用乘客端",
                            Toast.LENGTH_LONG
                        ).show()

                        onLoginSuccess()
                    } catch (e: Exception) {
                        Toast.makeText(
                            context,
                            "登入失敗: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    } finally {
                        isLoading = false
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = phone.isNotBlank() && !isLoading,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4CAF50)
            )
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("登入", style = MaterialTheme.typography.titleMedium)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 提示卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "快速登入",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "輸入手機號碼即可快速登入叫車，無需註冊",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 快速填入按鈕
        OutlinedButton(
            onClick = {
                phone = "0911111111"
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("快速填入測試手機號")
        }
        }
    }
}
