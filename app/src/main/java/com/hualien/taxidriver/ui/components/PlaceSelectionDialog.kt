package com.hualien.taxidriver.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.LatLng
import com.hualien.taxidriver.service.PlaceDetails
import com.hualien.taxidriver.service.PlacePrediction
import com.hualien.taxidriver.service.PlacesApiService
import com.hualien.taxidriver.utils.GeocodingUtils
import kotlinx.coroutines.launch

/**
 * 地點選擇對話框
 * 支援地址搜尋自動完成和地圖點選兩種方式
 *
 * @param title 對話框標題（例如："選擇上車點"）
 * @param currentLocation 當前位置（用於優先顯示附近結果）
 * @param onPlaceSelected 選擇地點後的回調（傳回 LatLng 和 地址字串）
 * @param onMapSelectionRequest 請求在地圖上選擇的回調
 * @param onDismiss 關閉對話框的回調
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceSelectionDialog(
    title: String,
    currentLocation: LatLng? = null,
    onPlaceSelected: (LatLng, String) -> Unit,
    onMapSelectionRequest: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val placesService = remember { PlacesApiService(context) }
    val coroutineScope = rememberCoroutineScope()

    var isLoadingDetails by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // 標題列
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "關閉"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 地址搜尋框
                PlaceSearchBar(
                    label = "搜尋地址",
                    placeholder = "輸入地點名稱或地址",
                    currentLocation = currentLocation,
                    onPlaceSelected = { prediction ->
                        // 獲取地點詳細資訊（包含座標）
                        coroutineScope.launch {
                            isLoadingDetails = true
                            try {
                                val result = placesService.getPlaceDetails(prediction.placeId)
                                result.onSuccess { details ->
                                    details.latLng?.let { latLng ->
                                        // 取得詳細地址
                                        val fullAddress = details.address.ifEmpty {
                                            prediction.fullText
                                        }
                                        onPlaceSelected(latLng, fullAddress)
                                        onDismiss()
                                    }
                                }
                            } finally {
                                isLoadingDetails = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // 載入詳細資訊時的提示
                if (isLoadingDetails) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "載入地點資訊...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 分隔線和提示
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f))
                    Text(
                        text = "或",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 在地圖上選擇按鈕
                OutlinedButton(
                    onClick = {
                        onMapSelectionRequest()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("在地圖上選擇")
                }
            }
        }
    }
}
