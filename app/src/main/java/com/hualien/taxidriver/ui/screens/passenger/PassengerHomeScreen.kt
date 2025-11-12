package com.hualien.taxidriver.ui.screens.passenger

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.location.Geocoder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.hualien.taxidriver.R
import com.hualien.taxidriver.ui.components.PlaceSelectionDialog
import com.hualien.taxidriver.utils.Constants
import com.hualien.taxidriver.utils.AddressUtils
import com.hualien.taxidriver.utils.GeocodingUtils
import com.hualien.taxidriver.viewmodel.PassengerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * 乘客端主頁
 * 顯示地圖、附近司機、叫車功能
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassengerHomeScreen(
    passengerId: String = "passenger_demo",
    passengerName: String = "測試乘客",
    passengerPhone: String = "0911111111"
) {
    val context = LocalContext.current
    val viewModel: PassengerViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    // Snackbar 狀態
    val snackbarHostState = remember { SnackbarHostState() }

    // 地圖狀態
    val hualienLocation = LatLng(Constants.DEFAULT_LATITUDE, Constants.DEFAULT_LONGITUDE)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(hualienLocation, 14f)
    }

    // 選擇模式：pickup 或 destination
    var selectionMode by remember { mutableStateOf<String?>(null) }

    // 地點選擇對話框狀態
    var showPlaceDialog by remember { mutableStateOf(false) }
    var dialogMode by remember { mutableStateOf<String?>(null) } // "pickup" or "destination"

    // 訂單完成對話框狀態
    var showCompletedDialog by remember { mutableStateOf(false) }
    var completedOrder by remember { mutableStateOf<com.hualien.taxidriver.domain.model.Order?>(null) }

    // 初始化附近司機和 WebSocket 連接
    LaunchedEffect(Unit) {
        android.util.Log.d("PassengerHomeScreen", "========== PassengerHomeScreen 初始化 ==========")
        android.util.Log.d("PassengerHomeScreen", "乘客ID: $passengerId")

        android.util.Log.d("PassengerHomeScreen", "連接 WebSocket...")
        viewModel.connectWebSocket(passengerId)

        android.util.Log.d("PassengerHomeScreen", "更新附近司機...")
        viewModel.updateNearbyDrivers()

        // 定期刷新附近司機列表（每30秒）
        while (true) {
            kotlinx.coroutines.delay(30000L) // 30秒
            android.util.Log.d("PassengerHomeScreen", "⏰ 定期刷新附近司機...")
            viewModel.updateNearbyDrivers()
        }
    }

    // 監聽錯誤並顯示 Snackbar
    LaunchedEffect(uiState.error) {
        uiState.error?.let { errorMessage ->
            snackbarHostState.showSnackbar(
                message = errorMessage,
                duration = SnackbarDuration.Short
            )
            viewModel.clearError()
        }
    }

    // 監聽訂單狀態變化並顯示提示
    LaunchedEffect(uiState.orderStatus) {
        when (uiState.orderStatus) {
            com.hualien.taxidriver.viewmodel.OrderStatus.WAITING -> {
                snackbarHostState.showSnackbar(
                    message = "叫車請求已發送，等待司機接單...",
                    duration = SnackbarDuration.Short
                )
            }
            com.hualien.taxidriver.viewmodel.OrderStatus.ACCEPTED -> {
                snackbarHostState.showSnackbar(
                    message = "司機已接單！",
                    duration = SnackbarDuration.Short
                )
            }
            com.hualien.taxidriver.viewmodel.OrderStatus.SETTLING -> {
                snackbarHostState.showSnackbar(
                    message = "行程已結束，請確認車資",
                    duration = SnackbarDuration.Short
                )
            }
            com.hualien.taxidriver.viewmodel.OrderStatus.COMPLETED -> {
                // 訂單完成時，保存訂單信息並顯示完成對話框
                uiState.currentOrder?.let { order ->
                    completedOrder = order
                    showCompletedDialog = true
                }
            }
            com.hualien.taxidriver.viewmodel.OrderStatus.CANCELLED -> {
                snackbarHostState.showSnackbar(
                    message = "訂單已取消",
                    duration = SnackbarDuration.Short
                )
            }
            else -> {}
        }
    }

    // 位置權限
    var hasLocationPermission by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
    }

    // 請求權限
    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    // 啟動混合定位服務並自動定位到用戶位置
    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            viewModel.startLocationUpdates()
        }
    }

    // 當獲取到用戶位置時，自動移動相機（只執行一次）
    var hasAutoLocated by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.currentLocation) {
        val location = uiState.currentLocation
        if (!hasAutoLocated && location != null) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(location, 15f),
                durationMs = 1500
            )
            hasAutoLocated = true
        }
    }

    // 清理資源
    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopLocationUpdates()
        }
    }

    // 底部抽屜狀態（直接完全展開，不需要用戶手動拉）
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
    var showBottomSheet by remember { mutableStateOf(false) }

    // Geocoder for reverse geocoding
    val geocoder = remember { Geocoder(context, Locale.getDefault()) }

    // 處理地圖點擊事件
    val onMapClick: (LatLng) -> Unit = { location ->
        scope.launch {
            // 反向地理編碼獲取地址
            val address = try {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                    addresses?.firstOrNull()?.getAddressLine(0) ?: "未知地址"
                }
            } catch (e: Exception) {
                "未知地址"
            }

            when (selectionMode) {
                "pickup" -> {
                    viewModel.setPickupLocation(location, address)
                    selectionMode = null
                }
                "destination" -> {
                    viewModel.setDestinationLocation(location, address)
                    selectionMode = null
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Snackbar Host
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp)
        )

        // Google Maps
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = hasLocationPermission,
                mapStyleOptions = try {
                    com.google.android.gms.maps.model.MapStyleOptions.loadRawResourceStyle(
                        context,
                        com.hualien.taxidriver.R.raw.map_style
                    )
                } catch (e: Exception) {
                    null
                }
            ),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = true,
                compassEnabled = false,
                mapToolbarEnabled = false
            ),
            onMapClick = onMapClick
        ) {
            // 計程車圖標（使用 remember 避免重複創建）
            val taxiIcon = remember {
                vectorToBitmap(context, R.drawable.ic_taxi)
            }

            // 顯示附近司機（優先顯示包含 ETA 的）
            if (uiState.driversWithETA.isNotEmpty()) {
                uiState.driversWithETA.forEach { driverWithETA ->
                    // 使用 key 讓 Compose 追蹤 Marker 身份，避免重新創建
                    key(driverWithETA.driver.driverId) {
                        Marker(
                            state = MarkerState(position = driverWithETA.driver.location),
                            title = driverWithETA.driver.driverName,
                            snippet = "預估 ${driverWithETA.etaText} 到達 · ${driverWithETA.distanceText}",
                            icon = taxiIcon
                        )
                    }
                }
            } else {
                // 如果沒有 ETA 資訊，顯示基本司機資訊
                uiState.nearbyDrivers.forEach { driver ->
                    // 使用 key 讓 Compose 追蹤 Marker 身份，避免重新創建
                    key(driver.driverId) {
                        Marker(
                            state = MarkerState(position = driver.location),
                            title = driver.driverName,
                            snippet = "評分: ${driver.rating}",
                            icon = taxiIcon
                        )
                    }
                }
            }

            // 顯示上車點標記
            uiState.pickupLocation?.let { location ->
                Marker(
                    state = MarkerState(position = location),
                    title = "上車點",
                    snippet = uiState.pickupAddress,
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                )
            }

            // 顯示目的地標記
            uiState.destinationLocation?.let { location ->
                Marker(
                    state = MarkerState(position = location),
                    title = "目的地",
                    snippet = uiState.destinationAddress,
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                )
            }

            // 顯示路線（Polyline）
            uiState.routeInfo?.let { route ->
                Polyline(
                    points = route.polylinePoints,
                    color = MaterialTheme.colorScheme.primary,
                    width = 10f
                )
            }
        }

        // 底部地址選擇抽屜
        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                sheetState = sheetState,
                containerColor = Color.White,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            ) {
                AddressSelectionSheet(
                    pickupAddress = uiState.pickupAddress,
                    destinationAddress = uiState.destinationAddress,
                    nearbyDriverCount = uiState.nearbyDrivers.size,
                    isLoading = uiState.isLoading,
                    selectionMode = selectionMode,
                    routeInfo = uiState.routeInfo,
                    fareEstimate = uiState.fareEstimate,
                    isCalculatingRoute = uiState.isCalculatingRoute,
                    driversWithETA = uiState.driversWithETA,
                    isCalculatingETA = uiState.isCalculatingETA,
                    onPickupClick = {
                        dialogMode = "pickup"
                        showPlaceDialog = true
                        showBottomSheet = false
                    },
                    onDestinationClick = {
                        dialogMode = "destination"
                        showPlaceDialog = true
                        showBottomSheet = false
                    },
                    onCallTaxi = {
                        android.util.Log.d("PassengerHomeScreen", "========== 按下立即叫車按鈕 ==========")
                        android.util.Log.d("PassengerHomeScreen", "準備調用 requestTaxi...")

                        viewModel.requestTaxi(
                            passengerId = passengerId,
                            passengerName = passengerName,
                            passengerPhone = passengerPhone
                        )

                        android.util.Log.d("PassengerHomeScreen", "已調用 requestTaxi，關閉底部面板")
                        showBottomSheet = false
                    }
                )
            }
        }

        // 顯示選擇模式提示
        if (selectionMode != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp),
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(24.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (selectionMode == "pickup") "點擊地圖選擇上車點" else "點擊地圖選擇目的地",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            selectionMode = null
                            showBottomSheet = true
                        },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "取消",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // 訂單狀態卡片（完成狀態不顯示，改為顯示對話框）
        uiState.currentOrder?.let { currentOrder ->
            if (uiState.orderStatus != com.hualien.taxidriver.viewmodel.OrderStatus.COMPLETED) {
                OrderStatusCard(
                    order = currentOrder,
                    orderStatus = uiState.orderStatus,
                    onCancelOrder = {
                        viewModel.cancelOrder(passengerId)
                    },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 80.dp, start = 16.dp, end = 16.dp)
                )
            }
        }

        // Uber 風格的頂部搜尋框
        if (uiState.currentOrder == null) {
            Surface(
                onClick = {
                    // 點擊搜尋框：直接打開目的地選擇
                    dialogMode = "destination"
                    showPlaceDialog = true
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp),
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 8.dp,
                color = Color.White
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (uiState.destinationAddress != null) {
                            uiState.destinationAddress
                        } else {
                            "要去哪裡？"
                        },
                        style = if (uiState.destinationAddress != null) {
                            MaterialTheme.typography.bodyLarge
                        } else {
                            MaterialTheme.typography.titleMedium
                        },
                        fontWeight = if (uiState.destinationAddress != null) {
                            FontWeight.Normal
                        } else {
                            FontWeight.Bold
                        },
                        color = if (uiState.destinationAddress != null) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }

        // 右下角按鈕組（確認叫車 + 定位）
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 確認叫車按鈕（只在有上車點且沒有訂單時顯示）
            if (uiState.pickupLocation != null && uiState.currentOrder == null) {
                FloatingActionButton(
                    onClick = {
                        showBottomSheet = true
                    },
                    containerColor = Color(0xFF4CAF50),
                    contentColor = Color.White
                ) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = "確認叫車"
                    )
                }
            }

            // 定位按鈕
            FloatingActionButton(
                onClick = {
                    uiState.currentLocation?.let { location ->
                        scope.launch {
                            cameraPositionState.animate(
                                CameraUpdateFactory.newLatLngZoom(location, 16f),
                                durationMs = 1000
                            )
                        }
                    } ?: run {
                        scope.launch {
                            snackbarHostState.showSnackbar("正在獲取您的位置...")
                        }
                    }
                },
                containerColor = Color.White,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "定位到我的位置"
                )
            }
        }
    }

    // 地點選擇對話框
    if (showPlaceDialog && dialogMode != null) {
        PlaceSelectionDialog(
            title = if (dialogMode == "pickup") "選擇上車點" else "選擇目的地",
            currentLocation = uiState.currentLocation ?: hualienLocation,
            onPlaceSelected = { latLng, address ->
                // 使用搜尋選擇的地點
                when (dialogMode) {
                    "pickup" -> {
                        viewModel.setPickupLocation(latLng, address)
                        showPlaceDialog = false
                        dialogMode = null
                        // 選好上車點後，打開確認面板
                        showBottomSheet = true
                    }
                    "destination" -> {
                        viewModel.setDestinationLocation(latLng, address)
                        // 關閉對話框，LaunchedEffect 會自動觸發上車點選擇
                        showPlaceDialog = false
                        dialogMode = null
                    }
                }
            },
            onMapSelectionRequest = {
                // 切換到地圖選擇模式
                selectionMode = dialogMode
                showPlaceDialog = false
                dialogMode = null
            },
            onDismiss = {
                showPlaceDialog = false
                dialogMode = null
            }
        )
    }

    // 上車點快速選擇對話框（選完目的地後自動顯示）
    var showPickupQuickSelect by remember { mutableStateOf(false) }

    // 監聽目的地設定完成，自動詢問上車點
    LaunchedEffect(uiState.destinationLocation, uiState.pickupLocation) {
        if (uiState.destinationLocation != null && uiState.pickupLocation == null && !showPlaceDialog) {
            kotlinx.coroutines.delay(300)
            showPickupQuickSelect = true
        }
    }

    if (showPickupQuickSelect && uiState.currentLocation != null) {
        AlertDialog(
            onDismissRequest = { showPickupQuickSelect = false },
            title = { Text("選擇上車地點") },
            text = { Text("您想在哪裡上車？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        // 使用當前位置
                        uiState.currentLocation?.let { location ->
                            scope.launch {
                                val address = GeocodingUtils.getAddressFromLocation(
                                    context,
                                    location
                                )
                                viewModel.setPickupLocation(location, address)
                                showPickupQuickSelect = false
                                showBottomSheet = true
                            }
                        }
                    }
                ) {
                    Text("使用當前位置", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPickupQuickSelect = false
                        dialogMode = "pickup"
                        showPlaceDialog = true
                    }
                ) {
                    Text("選擇其他地點")
                }
            }
        )
    }

    // 訂單完成對話框
    if (showCompletedDialog && completedOrder != null) {
        AlertDialog(
            onDismissRequest = { /* 不允許點擊外部關閉 */ },
            title = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "行程已完成",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    // 車資信息
                    completedOrder?.fare?.let { fare ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFF5F5F5)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("車資", style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        "NT$ ${fare.meterAmount}",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF4CAF50)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // 行程摘要
                    Text(
                        "行程摘要",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "上車地點",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                            Text(
                                completedOrder?.pickup?.address ?: "",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    completedOrder?.destination?.let { dest ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Icon(
                                imageVector = Icons.Default.Place,
                                contentDescription = null,
                                tint = Color(0xFFFF5722),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "目的地",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                                Text(
                                    text = dest.address ?: "",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "感謝您的搭乘！",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCompletedDialog = false
                        completedOrder = null
                        viewModel.clearOrder()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("繼續叫車", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

/**
 * 地址選擇抽屜組件
 */
@Composable
fun AddressSelectionSheet(
    pickupAddress: String,
    destinationAddress: String,
    nearbyDriverCount: Int,
    isLoading: Boolean,
    selectionMode: String?,
    routeInfo: com.hualien.taxidriver.service.DirectionsResult?,
    fareEstimate: com.hualien.taxidriver.utils.FareResult?,
    isCalculatingRoute: Boolean,
    driversWithETA: List<com.hualien.taxidriver.service.DriverWithETA>,
    isCalculatingETA: Boolean,
    onPickupClick: () -> Unit,
    onDestinationClick: () -> Unit,
    onCallTaxi: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Text(
            text = "選擇地點",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 上車點選擇卡片
        Card(
            onClick = onPickupClick,
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (pickupAddress.isEmpty())
                    MaterialTheme.colorScheme.surfaceVariant
                else
                    Color(0xFF4CAF50).copy(alpha = 0.1f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = "上車點",
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "上車點",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (pickupAddress.isEmpty()) {
                            "點擊在地圖上選擇"
                        } else {
                            AddressUtils.shortenAddress(pickupAddress)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (pickupAddress.isEmpty())
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        else
                            MaterialTheme.colorScheme.onSurface,
                        maxLines = 2
                    )
                }
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "編輯",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 目的地選擇卡片
        Card(
            onClick = onDestinationClick,
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (destinationAddress.isEmpty())
                    MaterialTheme.colorScheme.surfaceVariant
                else
                    Color(0xFFF44336).copy(alpha = 0.1f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "目的地",
                    tint = Color(0xFFF44336),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "目的地",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (destinationAddress.isEmpty()) {
                            "點擊在地圖上選擇（可選）"
                        } else {
                            AddressUtils.shortenAddress(destinationAddress)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (destinationAddress.isEmpty())
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        else
                            MaterialTheme.colorScheme.onSurface,
                        maxLines = 2
                    )
                }
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "編輯",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // 路線資訊卡片（當有路線資料時顯示）
        if (routeInfo != null && fareEstimate != null) {
            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "行程資訊",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "距離",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                            Text(
                                text = routeInfo.distanceText,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        Column {
                            Text(
                                text = "時間",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                            Text(
                                text = routeInfo.durationText,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        Column {
                            Text(
                                text = "預估車資",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                            Text(
                                text = "NT$ ${fareEstimate.totalFare}",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (fareEstimate.isNightTime) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "※ 包含夜間加成 NT$ ${fareEstimate.nightSurcharge}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }

        // 路線計算中的載入提示
        if (isCalculatingRoute) {
            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "正在計算路線...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 叫車按鈕
        Button(
            onClick = onCallTaxi,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = pickupAddress.isNotEmpty() && !isLoading,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4CAF50)
            )
        ) {
            if (isLoading) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 3.dp
                    )
                    Text(
                        text = "發送中...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Default.Phone,
                    contentDescription = "叫車"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "立即叫車",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 提示文字和空狀態
        if (nearbyDriverCount > 0) {
            Column(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "附近有 $nearbyDriverCount 位司機在線",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Medium
                    )
                }

                // 顯示最近司機的 ETA
                if (driversWithETA.isNotEmpty() && !isCalculatingETA) {
                    val nearestDriver = driversWithETA.first()
                    Text(
                        text = "最近司機預估 ${nearestDriver.etaText} 到達（${nearestDriver.distanceText}）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                } else if (isCalculatingETA) {
                    Text(
                        text = "正在計算司機距離...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "目前附近沒有司機",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "請稍後再試",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Light
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * 訂單狀態卡片
 */
@Composable
fun OrderStatusCard(
    order: com.hualien.taxidriver.domain.model.Order,
    orderStatus: com.hualien.taxidriver.viewmodel.OrderStatus,
    onCancelOrder: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showCancelDialog by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 狀態標題
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (orderStatus) {
                        com.hualien.taxidriver.viewmodel.OrderStatus.WAITING -> "等待司機接單..."
                        com.hualien.taxidriver.viewmodel.OrderStatus.ACCEPTED -> "司機已接單"
                        com.hualien.taxidriver.viewmodel.OrderStatus.DRIVER_ARRIVING -> "司機前往中"
                        com.hualien.taxidriver.viewmodel.OrderStatus.ARRIVED -> "司機已到達"
                        com.hualien.taxidriver.viewmodel.OrderStatus.ON_TRIP -> "行程中"
                        com.hualien.taxidriver.viewmodel.OrderStatus.SETTLING -> "結算中"
                        com.hualien.taxidriver.viewmodel.OrderStatus.COMPLETED -> "已完成"
                        else -> "訂單處理中"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // 取消按鈕（只在等待接單時顯示）
                if (orderStatus == com.hualien.taxidriver.viewmodel.OrderStatus.WAITING) {
                    TextButton(onClick = { showCancelDialog = true }) {
                        Text("取消", color = Color(0xFFF44336))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 進度指示器
            OrderProgressIndicator(orderStatus = orderStatus)

            Spacer(modifier = Modifier.height(16.dp))

            // 訂單詳情
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 訂單編號
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "訂單 ${order.orderId.takeLast(8)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 上車點
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "上車點",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = AddressUtils.shortenAddress(order.pickup.address ?: "未知地址", 40),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2
                        )
                    }
                }

                // 目的地（如果有）
                order.destination?.let { dest ->
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = Color(0xFFF44336),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "目的地",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = AddressUtils.shortenAddress(dest.address ?: "未知地址", 40),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2
                            )
                        }
                    }
                }

                // 司機信息（如果已接單）
                if (orderStatus != com.hualien.taxidriver.viewmodel.OrderStatus.WAITING &&
                    order.driverName != null) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = order.driverName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            order.driverPhone?.let { phone ->
                                Text(
                                    text = phone,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 取消確認對話框
    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("取消訂單") },
            text = { Text("確定要取消這筆訂單嗎？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onCancelOrder()
                        showCancelDialog = false
                    }
                ) {
                    Text("確定", color = Color(0xFFF44336))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 訂單進度指示器
 */
@Composable
fun OrderProgressIndicator(
    orderStatus: com.hualien.taxidriver.viewmodel.OrderStatus,
    modifier: Modifier = Modifier
) {
    val steps = listOf(
        "等待接單",
        "司機接單",
        "司機到達",
        "行程中",
        "已完成"
    )

    val currentStep = when (orderStatus) {
        com.hualien.taxidriver.viewmodel.OrderStatus.WAITING -> 0
        com.hualien.taxidriver.viewmodel.OrderStatus.ACCEPTED,
        com.hualien.taxidriver.viewmodel.OrderStatus.DRIVER_ARRIVING -> 1
        com.hualien.taxidriver.viewmodel.OrderStatus.ARRIVED -> 2
        com.hualien.taxidriver.viewmodel.OrderStatus.ON_TRIP -> 3
        com.hualien.taxidriver.viewmodel.OrderStatus.SETTLING,
        com.hualien.taxidriver.viewmodel.OrderStatus.COMPLETED -> 4
        else -> 0
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { index, step ->
            // 步驟點
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(if (index <= currentStep) 32.dp else 24.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (index <= currentStep)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(
                        modifier = Modifier.size(if (index <= currentStep) 32.dp else 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (index < currentStep) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        } else if (index == currentStep) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }
            }

            // 連接線（除了最後一個）
            if (index < steps.lastIndex) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(2.dp)
                        .background(
                            if (index < currentStep)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        )
                )
            }
        }
    }
}

/**
 * 將 Vector Drawable 轉換為 BitmapDescriptor（用於地圖標記）
 */
private fun vectorToBitmap(context: Context, vectorResId: Int): BitmapDescriptor {
    val vectorDrawable = ContextCompat.getDrawable(context, vectorResId)
        ?: return BitmapDescriptorFactory.defaultMarker()
    
    val bitmap = Bitmap.createBitmap(
        vectorDrawable.intrinsicWidth,
        vectorDrawable.intrinsicHeight,
        Bitmap.Config.ARGB_8888
    )
    
    val canvas = Canvas(bitmap)
    vectorDrawable.setBounds(0, 0, canvas.width, canvas.height)
    vectorDrawable.draw(canvas)
    
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}
