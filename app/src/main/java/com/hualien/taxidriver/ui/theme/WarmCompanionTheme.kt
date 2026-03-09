package com.hualien.taxidriver.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp

/**
 * 暖心陪伴風主題 - 專為語音優先的老年人介面設計
 *
 * 設計理念：
 * - 溫暖的橙色調，讓人感到親切
 * - 高對比度，適合視力較差的用戶
 * - 大字體，易於閱讀
 * - 柔和的圓角和陰影，給人安全感
 */

// ==================== 色彩系統 ====================

// 主色：溫暖珊瑚橙
val WarmCoral = Color(0xFFFF6B4A)
val WarmCoralLight = Color(0xFFFF8A70)
val WarmCoralDark = Color(0xFFE55530)

// 背景：柔和奶油色
val CreamWhite = Color(0xFFFFF8F0)
val CreamLight = Color(0xFFFFFCF8)
val CreamDark = Color(0xFFF5EDE0)

// 強調色：快樂黃
val HappyYellow = Color(0xFFFFD93D)
val HappyYellowLight = Color(0xFFFFE770)
val HappyYellowDark = Color(0xFFFFC107)

// 成功色：柔和綠
val SoftGreen = Color(0xFF4CAF50)
val SoftGreenLight = Color(0xFF81C784)
val SoftGreenDark = Color(0xFF388E3C)

// 錯誤色：柔和紅
val SoftRed = Color(0xFFE57373)
val SoftRedLight = Color(0xFFFFCDD2)
val SoftRedDark = Color(0xFFD32F2F)

// 文字色：深棕色（比純黑更溫暖）
val WarmBrown = Color(0xFF4A3728)
val WarmBrownLight = Color(0xFF6D5C4D)
val WarmBrownMuted = Color(0xFF8B7355)

// 陰影和分隔色
val ShadowColor = Color(0x1A000000)
val DividerColor = Color(0xFFE0D5C8)

// ==================== 角色狀態顏色 ====================

object CharacterColors {
    val idle = WarmCoral
    val listening = HappyYellow
    val thinking = Color(0xFF64B5F6)  // 柔和藍
    val speaking = SoftGreen
    val happy = HappyYellow
    val error = SoftRed
}

// ==================== 色彩方案 ====================

private val WarmLightColorScheme = lightColorScheme(
    primary = WarmCoral,
    onPrimary = Color.White,
    primaryContainer = WarmCoralLight,
    onPrimaryContainer = WarmBrown,

    secondary = HappyYellow,
    onSecondary = WarmBrown,
    secondaryContainer = HappyYellowLight,
    onSecondaryContainer = WarmBrown,

    tertiary = SoftGreen,
    onTertiary = Color.White,
    tertiaryContainer = SoftGreenLight,
    onTertiaryContainer = WarmBrown,

    background = CreamWhite,
    onBackground = WarmBrown,

    surface = CreamLight,
    onSurface = WarmBrown,
    surfaceVariant = CreamDark,
    onSurfaceVariant = WarmBrownLight,

    error = SoftRed,
    onError = Color.White,
    errorContainer = SoftRedLight,
    onErrorContainer = SoftRedDark,

    outline = DividerColor,
    outlineVariant = CreamDark
)

private val WarmDarkColorScheme = darkColorScheme(
    primary = WarmCoralLight,
    onPrimary = WarmBrown,
    primaryContainer = WarmCoralDark,
    onPrimaryContainer = CreamWhite,

    secondary = HappyYellowLight,
    onSecondary = WarmBrown,
    secondaryContainer = HappyYellowDark,
    onSecondaryContainer = CreamWhite,

    tertiary = SoftGreenLight,
    onTertiary = WarmBrown,
    tertiaryContainer = SoftGreenDark,
    onTertiaryContainer = CreamWhite,

    background = Color(0xFF2D2520),
    onBackground = CreamWhite,

    surface = Color(0xFF3D352D),
    onSurface = CreamWhite,
    surfaceVariant = Color(0xFF4D4540),
    onSurfaceVariant = CreamDark,

    error = SoftRedLight,
    onError = WarmBrown,
    errorContainer = SoftRedDark,
    onErrorContainer = CreamWhite,

    outline = Color(0xFF6D5C4D),
    outlineVariant = Color(0xFF4D4540)
)

// ==================== 老年人友善字體 ====================

object WarmTypography {
    // 超大標題（角色名稱、主要提示）
    val displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp
    )

    // 大標題（狀態文字）
    val displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp
    )

    // 中標題（司機信息等）
    val headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 38.sp,
        letterSpacing = 0.sp
    )

    // 標題（司機姓名等關鍵資訊）
    val titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.sp
    )

    // 按鈕文字
    val labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    )

    // 正文（提示語）
    val bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    )

    // 次要文字
    val bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp
    )

    // 輔助文字（地址等）
    val bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    )
}

// ==================== 尺寸常量 ====================

object WarmDimensions {
    // 按鈕最小高度（老年人友善：72dp 以上）
    const val buttonMinHeight = 80

    // 觸控區域最小尺寸
    const val touchTargetMin = 56

    // 圓角半徑
    const val cornerRadiusSmall = 16
    const val cornerRadiusMedium = 24
    const val cornerRadiusLarge = 32
    const val cornerRadiusXLarge = 48

    // 間距
    const val spacingSmall = 8
    const val spacingMedium = 16
    const val spacingLarge = 24
    const val spacingXLarge = 32
    const val spacingXXLarge = 48

    // 角色尺寸
    const val characterSizeSmall = 120
    const val characterSizeMedium = 200
    const val characterSizeLarge = 280

    // 語音按鈕尺寸
    const val voiceButtonSize = 120
    const val voiceButtonPadding = 24
}

// ==================== 主題 Composable ====================

/**
 * 系統字體縮放上限
 *
 * WarmCompanionTheme 已針對老年人設計超大字體（40sp 等），
 * 若再套用系統級的大字體設定會導致嚴重爆版。
 * 因此限制 fontScale 最大為 1.0，確保 UI 佈局穩定。
 */
private const val MAX_FONT_SCALE = 1.0f

@Composable
fun WarmCompanionTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        WarmDarkColorScheme
    } else {
        WarmLightColorScheme
    }

    // 限制系統字體縮放，防止老年機大字體設定導致 UI 爆版
    val currentDensity = LocalDensity.current
    val fontScaleLimited = Density(
        density = currentDensity.density,
        fontScale = minOf(currentDensity.fontScale, MAX_FONT_SCALE)
    )

    CompositionLocalProvider(LocalDensity provides fontScaleLimited) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
