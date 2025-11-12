package com.hualien.taxidriver.ui.screens.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.hualien.taxidriver.service.DirectionsResult
import com.hualien.taxidriver.service.NavigationStep

/**
 * 導航畫面
 * 顯示逐步導航指引、地圖、剩餘距離和時間
 *
 * @param routeInfo 路線資訊
 * @param currentLocation 當前位置（即時更新）
 * @param onBack 返回按鈕回調
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationScreen(
    routeInfo: DirectionsResult,
    currentLocation: LatLng? = null,
    onBack: () -> Unit
) {
    // 當前導航步驟索引
    var currentStepIndex by remember { mutableStateOf(0) }

    // 地圖相機位置（跟隨當前位置或第一個步驟）
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            currentLocation ?: routeInfo.steps.firstOrNull()?.startLocation ?: routeInfo.polylinePoints.first(),
            16f
        )
    }

    // 計算剩餘距離和時間
    val remainingDistance = routeInfo.steps
        .drop(currentStepIndex)
        .sumOf { it.distanceMeters }
    val remainingDuration = routeInfo.steps
        .drop(currentStepIndex)
        .sumOf { it.durationSeconds }

    val remainingDistanceText = if (remainingDistance >= 1000) {
        "${String.format("%.1f", remainingDistance / 1000.0)} 公里"
    } else {
        "$remainingDistance 公尺"
    }

    val remainingDurationText = if (remainingDuration >= 3600) {
        "${remainingDuration / 3600} 小時 ${(remainingDuration % 3600) / 60} 分鐘"
    } else if (remainingDuration >= 60) {
        "${remainingDuration / 60} 分鐘"
    } else {
        "$remainingDuration 秒"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "前往目的地",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = routeInfo.endAddress,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 地圖區域（上半部）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties = MapProperties(
                        isMyLocationEnabled = currentLocation != null
                    ),
                    uiSettings = MapUiSettings(
                        zoomControlsEnabled = false,
                        myLocationButtonEnabled = true,
                        compassEnabled = true
                    )
                ) {
                    // 繪製完整路線
                    Polyline(
                        points = routeInfo.polylinePoints,
                        color = Color(0xFF4CAF50),
                        width = 12f
                    )

                    // 起點標記
                    Marker(
                        state = MarkerState(position = routeInfo.steps.first().startLocation),
                        title = "起點",
                        snippet = routeInfo.startAddress
                    )

                    // 終點標記
                    Marker(
                        state = MarkerState(position = routeInfo.steps.last().endLocation),
                        title = "目的地",
                        snippet = routeInfo.endAddress
                    )
                }

                // 剩餘距離和時間資訊（浮動卡片）
                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = remainingDistanceText,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "剩餘距離",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        VerticalDivider(modifier = Modifier.height(40.dp))

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = remainingDurationText,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "預估時間",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 當前導航指示（大卡片）
            if (currentStepIndex < routeInfo.steps.size) {
                val currentStep = routeInfo.steps[currentStepIndex]

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // 方向圖標
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primary,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowForward,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = currentStep.instruction,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = "${currentStep.distanceText} · ${currentStep.durationText}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }

                        // 下一步按鈕
                        if (currentStepIndex < routeInfo.steps.size - 1) {
                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = { currentStepIndex++ },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.ArrowForward, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("下一步")
                            }
                        } else {
                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = onBack,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF4CAF50)
                                )
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("已到達目的地")
                            }
                        }
                    }
                }
            }

            // 所有導航步驟列表（下半部，可滾動）
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.8f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "導航步驟",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "${currentStepIndex + 1} / ${routeInfo.steps.size}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(routeInfo.steps) { index, step ->
                            NavigationStepItem(
                                step = step,
                                stepNumber = index + 1,
                                isCurrentStep = index == currentStepIndex,
                                isPastStep = index < currentStepIndex
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 單個導航步驟項目
 */
@Composable
private fun NavigationStepItem(
    step: NavigationStep,
    stepNumber: Int,
    isCurrentStep: Boolean,
    isPastStep: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isCurrentStep -> MaterialTheme.colorScheme.primaryContainer
                isPastStep -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 步驟編號
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        when {
                            isCurrentStep -> MaterialTheme.colorScheme.primary
                            isPastStep -> Color.Gray
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isPastStep) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Text(
                        text = stepNumber.toString(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = step.instruction,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isCurrentStep) FontWeight.Bold else FontWeight.Normal,
                    color = if (isPastStep)
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    else
                        MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${step.distanceText} · ${step.durationText}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isCurrentStep) {
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
