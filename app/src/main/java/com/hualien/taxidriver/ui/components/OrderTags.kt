package com.hualien.taxidriver.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hualien.taxidriver.domain.model.Order

// ========== 顏色定義 ==========

private val PhoneOrange = Color(0xFFFF8C00)
private val AppBlue = Color(0xFF2196F3)
private val LineGreen = Color(0xFF00C300)
private val SeniorPurple = Color(0xFF9C27B0)
private val LoveRed = Color(0xFFE53935)
private val PendingYellow = Color(0xFFFFA000)
private val PetGreen = Color(0xFF4CAF50)
private val PetOrange = Color(0xFFFF9800)
private val PetGray = Color(0xFF9E9E9E)

// ========== 基礎標籤 ==========

@Composable
fun OrderTag(
    text: String,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    textColor: Color = Color.White,
    fontSize: Int = 12
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(backgroundColor)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

// ========== 來源標籤 ==========

@Composable
fun SourceTag(source: String?, modifier: Modifier = Modifier, fontSize: Int = 12) {
    when (source) {
        "PHONE" -> OrderTag(
            text = "電話",
            backgroundColor = PhoneOrange,
            modifier = modifier,
            fontSize = fontSize
        )
        "LINE" -> OrderTag(
            text = "LINE",
            backgroundColor = LineGreen,
            modifier = modifier,
            fontSize = fontSize
        )
        // APP 來源不顯示標籤（預設來源）
        else -> {}
    }
}

// ========== 補貼標籤 ==========

@Composable
fun SubsidyTag(subsidyType: String?, modifier: Modifier = Modifier, fontSize: Int = 12) {
    when (subsidyType) {
        "SENIOR_CARD" -> OrderTag(
            text = "敬老卡",
            backgroundColor = SeniorPurple,
            modifier = modifier,
            fontSize = fontSize
        )
        "LOVE_CARD" -> OrderTag(
            text = "愛心卡",
            backgroundColor = LoveRed,
            modifier = modifier,
            fontSize = fontSize
        )
        "PENDING" -> OrderTag(
            text = "卡片待確認",
            backgroundColor = PendingYellow,
            modifier = modifier,
            fontSize = fontSize
        )
        // NONE 不顯示
        else -> {}
    }
}

// ========== 寵物標籤 ==========

@Composable
fun PetTag(
    petPresent: String?,
    petCarrier: String?,
    source: String? = null,
    modifier: Modifier = Modifier,
    fontSize: Int = 12
) {
    when {
        petPresent == "YES" && petCarrier == "YES" -> OrderTag(
            text = "寵物(有籠)",
            backgroundColor = PetGreen,
            modifier = modifier,
            fontSize = fontSize
        )
        petPresent == "YES" && petCarrier == "NO" -> OrderTag(
            text = "寵物(無籠)",
            backgroundColor = PetOrange,
            modifier = modifier,
            fontSize = fontSize
        )
        petPresent == "YES" -> OrderTag(
            text = "有寵物",
            backgroundColor = PendingYellow,
            modifier = modifier,
            fontSize = fontSize
        )
        petPresent == "UNKNOWN" && source == "PHONE" -> OrderTag(
            text = "寵物?",
            backgroundColor = PetGray,
            modifier = modifier,
            fontSize = fontSize
        )
        // NO 或 APP 預設不顯示
        else -> {}
    }
}

// ========== 組合標籤列 ==========

@Composable
fun OrderTagRow(
    order: Order,
    modifier: Modifier = Modifier,
    fontSize: Int = 12
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SourceTag(source = order.source, fontSize = fontSize)
        SubsidyTag(subsidyType = order.subsidyType, fontSize = fontSize)
        PetTag(
            petPresent = order.petPresent,
            petCarrier = order.petCarrier,
            source = order.source,
            fontSize = fontSize
        )
    }
}

/**
 * 大字體版本的標籤列（SimplifiedDriverScreen 用）
 */
@Composable
fun OrderTagRowLarge(
    order: Order,
    modifier: Modifier = Modifier
) {
    OrderTagRow(
        order = order,
        modifier = modifier,
        fontSize = 16
    )
}
