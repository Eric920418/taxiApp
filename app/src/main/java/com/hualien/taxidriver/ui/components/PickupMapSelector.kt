package com.hualien.taxidriver.ui.components

import android.location.Geocoder
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.hualien.taxidriver.utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Uber 風格的上車點選擇器
 * 用戶拖曳地圖，中心固定標記代表上車點
 *
 * @param initialLocation 初始地圖位置（通常是用戶當前位置）
 * @param destinationName 目的地名稱（顯示在頂部）
 * @param onPickupConfirmed 用戶確認上車點時的回調
 * @param onCancel 用戶取消時的回調
 */
@Composable
fun PickupMapSelector(
    initialLocation: LatLng?,
    destinationName: String,
    onPickupConfirmed: (LatLng, String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 地圖初始位置
    val defaultLocation = initialLocation ?: LatLng(Constants.DEFAULT_LATITUDE, Constants.DEFAULT_LONGITUDE)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLocation, 16f)
    }

    // 中心點座標和地址
    var centerLocation by remember { mutableStateOf(defaultLocation) }
    var centerAddress by remember { mutableStateOf("正在獲取地址...") }
    var isLoadingAddress by remember { mutableStateOf(false) }

    // Geocoder
    val geocoder = remember { Geocoder(context, Locale.getDefault()) }

    // 當相機移動時更新中心點
    LaunchedEffect(cameraPositionState.isMoving) {
        if (!cameraPositionState.isMoving) {
            // 相機停止移動時，更新中心點座標並反向地理編碼
            val newCenter = cameraPositionState.position.target
            centerLocation = newCenter
            isLoadingAddress = true

            // 延遲一點再獲取地址（避免頻繁請求）
            delay(300)

            try {
                val address = withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(
                        newCenter.latitude,
                        newCenter.longitude,
                        1
                    )
                    addresses?.firstOrNull()?.getAddressLine(0) ?: "未知地址"
                }
                centerAddress = address
            } catch (e: Exception) {
                centerAddress = "無法獲取地址"
            } finally {
                isLoadingAddress = false
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 全螢幕地圖
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
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
                myLocationButtonEnabled = false,
                compassEnabled = false,
                mapToolbarEnabled = false
            )
        )

        // 頂部目的地信息 + 關閉按鈕
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(16.dp)
                .statusBarsPadding()
        ) {
            // 關閉按鈕
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .size(48.dp)
                        .shadow(4.dp, CircleShape)
                        .background(Color.White, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "取消",
                        tint = Color.Black
                    )
                }

                Spacer(modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 目的地卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color(0xFFF44336),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "目的地",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                        Text(
                            text = destinationName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // 中心固定標記（像 Uber 一樣）
        Box(
            modifier = Modifier.align(Alignment.Center),
            contentAlignment = Alignment.Center
        ) {
            // 陰影圓點（模擬標記陰影）
            Box(
                modifier = Modifier
                    .offset(y = 24.dp)
                    .size(12.dp)
                    .background(Color.Black.copy(alpha = 0.2f), CircleShape)
            )

            // 標記圖標
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = "上車點",
                tint = Color(0xFF4CAF50),
                modifier = Modifier
                    .size(48.dp)
                    .offset(y = (-12).dp)  // 讓標記底部對準中心
            )
        }

        // 我的位置按鈕
        FloatingActionButton(
            onClick = {
                initialLocation?.let { location ->
                    scope.launch {
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngZoom(location, 16f),
                            durationMs = 500
                        )
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
                .offset(y = 80.dp),
            containerColor = Color.White,
            contentColor = Color(0xFF4CAF50),
            shape = CircleShape,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MyLocation,
                contentDescription = "回到我的位置"
            )
        }

        // 底部確認卡片
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .navigationBarsPadding(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // 標題
                Text(
                    text = "選擇上車地點",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 地址顯示
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    if (isLoadingAddress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF4CAF50)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "正在獲取地址...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray
                        )
                    } else {
                        Text(
                            text = centerAddress,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 提示文字
                Text(
                    text = "拖曳地圖來調整上車位置",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 確認按鈕
                Button(
                    onClick = {
                        android.util.Log.d("PickupMapSelector", "========== 點擊「在這裡上車」==========")
                        android.util.Log.d("PickupMapSelector", "上車點座標: (${centerLocation.latitude}, ${centerLocation.longitude})")
                        android.util.Log.d("PickupMapSelector", "上車點地址: $centerAddress")
                        onPickupConfirmed(centerLocation, centerAddress)
                        android.util.Log.d("PickupMapSelector", "✅ 已調用 onPickupConfirmed")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    ),
                    enabled = !isLoadingAddress
                ) {
                    Text(
                        text = "在這裡上車",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
