package com.hualien.taxidriver.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.LatLng
import com.hualien.taxidriver.service.PlacePrediction
import com.hualien.taxidriver.service.PlacesApiService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 地址自動完成搜尋框組件
 *
 * @param label 輸入框標籤（例如："上車點"、"目的地"）
 * @param placeholder 輸入框提示文字
 * @param currentLocation 當前位置（用於優先顯示附近結果）
 * @param onPlaceSelected 選擇地點後的回調
 * @param modifier Modifier
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceSearchBar(
    label: String,
    placeholder: String = "請輸入地址或地點名稱",
    currentLocation: LatLng? = null,
    onPlaceSelected: (PlacePrediction) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val placesService = remember { PlacesApiService(context) }
    val coroutineScope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var predictions by remember { mutableStateOf<List<PlacePrediction>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var showSuggestions by remember { mutableStateOf(false) }

    // 當查詢文字改變時，延遲搜尋（避免過度呼叫 API）
    LaunchedEffect(query) {
        if (query.isBlank()) {
            predictions = emptyList()
            showSuggestions = false
            return@LaunchedEffect
        }

        // 延遲 500ms 再搜尋（使用者可能還在輸入）
        delay(500)

        isSearching = true
        coroutineScope.launch {
            try {
                val result = placesService.searchPlaces(query, currentLocation)
                result.onSuccess { list ->
                    predictions = list
                    showSuggestions = list.isNotEmpty()
                }
                result.onFailure {
                    predictions = emptyList()
                    showSuggestions = false
                }
            } finally {
                isSearching = false
            }
        }
    }

    Column(modifier = modifier) {
        // 搜尋輸入框
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "搜尋"
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = {
                        query = ""
                        predictions = emptyList()
                        showSuggestions = false
                    }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "清除"
                        )
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors()
        )

        // 搜尋中的載入指示
        if (isSearching) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            )
        }

        // 搜尋建議列表
        if (showSuggestions && predictions.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .padding(top = 8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                LazyColumn {
                    items(predictions) { prediction ->
                        PlaceSuggestionItem(
                            prediction = prediction,
                            onClick = {
                                query = prediction.primaryText
                                showSuggestions = false
                                onPlaceSelected(prediction)
                            }
                        )
                        if (prediction != predictions.last()) {
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

/**
 * 單個地點建議項目
 */
@Composable
private fun PlaceSuggestionItem(
    prediction: PlacePrediction,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 地點圖標
        Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        // 地點資訊
        Column(modifier = Modifier.weight(1f)) {
            // 主要文字（地點名稱）
            Text(
                text = prediction.primaryText,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )

            // 次要文字（詳細地址）
            if (prediction.secondaryText.isNotEmpty()) {
                Text(
                    text = prediction.secondaryText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
