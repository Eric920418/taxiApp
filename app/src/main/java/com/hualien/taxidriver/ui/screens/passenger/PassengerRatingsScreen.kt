package com.hualien.taxidriver.ui.screens.passenger

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hualien.taxidriver.data.remote.dto.PassengerRatingDto
import com.hualien.taxidriver.data.remote.dto.RatingSummaryDto
import com.hualien.taxidriver.ui.components.StarRating
import com.hualien.taxidriver.viewmodel.PassengerRatingsViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * 乘客評價詳情頁面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassengerRatingsScreen(
    passengerId: String,
    onNavigateBack: () -> Unit,
    viewModel: PassengerRatingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // 載入評價資料
    LaunchedEffect(passengerId) {
        viewModel.loadRatings(passengerId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的評價") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = uiState.error ?: "載入失敗",
                            color = MaterialTheme.colorScheme.error
                        )
                        Button(onClick = { viewModel.loadRatings(passengerId) }) {
                            Text("重試")
                        }
                    }
                }
            }

            uiState.ratings.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Text(
                            text = "尚未收到評價",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "完成更多行程後，司機會為您評價",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 評價摘要卡片
                    uiState.summary?.let { summary ->
                        item {
                            RatingSummaryCard(summary = summary)
                        }
                    }

                    // 評價分佈
                    uiState.summary?.let { summary ->
                        item {
                            RatingDistributionCard(summary = summary)
                        }
                    }

                    // 評價標題
                    item {
                        Text(
                            text = "所有評價 (${uiState.ratings.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    // 評價列表
                    items(uiState.ratings) { rating ->
                        RatingItemCard(rating = rating)
                    }
                }
            }
        }
    }
}

/**
 * 評價摘要卡片
 */
@Composable
private fun RatingSummaryCard(summary: RatingSummaryDto) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = String.format("%.1f", summary.averageRating),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.height(8.dp))

            StarRating(
                rating = summary.averageRating,
                starSize = 28
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${summary.totalRatings} 則評價",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * 評價分佈卡片
 */
@Composable
private fun RatingDistributionCard(summary: RatingSummaryDto) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "評價分佈",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            RatingBar(stars = 5, count = summary.fiveStars, total = summary.totalRatings)
            RatingBar(stars = 4, count = summary.fourStars, total = summary.totalRatings)
            RatingBar(stars = 3, count = summary.threeStars, total = summary.totalRatings)
            RatingBar(stars = 2, count = summary.twoStars, total = summary.totalRatings)
            RatingBar(stars = 1, count = summary.oneStar, total = summary.totalRatings)
        }
    }
}

/**
 * 評價分佈條
 */
@Composable
private fun RatingBar(stars: Int, count: Int, total: Int) {
    val percentage = if (total > 0) count.toFloat() / total else 0f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$stars",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(16.dp)
        )

        Icon(
            imageVector = Icons.Default.Star,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = Color(0xFFFFB300)
        )

        Spacer(modifier = Modifier.width(8.dp))

        LinearProgressIndicator(
            progress = { percentage },
            modifier = Modifier
                .weight(1f)
                .height(8.dp),
            color = Color(0xFFFFB300),
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(24.dp),
            textAlign = TextAlign.End
        )
    }
}

/**
 * 單一評價卡片
 */
@Composable
private fun RatingItemCard(rating: PassengerRatingDto) {
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 頂部：司機名稱和評分
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = rating.driverName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                StarRating(
                    rating = rating.rating.toFloat(),
                    starSize = 18
                )
            }

            // 評論內容
            if (!rating.comment.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "「${rating.comment}」",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 日期
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = dateFormat.format(Date(rating.createdAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}
