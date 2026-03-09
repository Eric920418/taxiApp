package com.hualien.taxidriver.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = TaxiYellowDark,
    secondary = TrustBlueDark,
    tertiary = InfoBlue,
    background = BackgroundDark,
    surface = Color(0xFF1E1E1E),
    error = ErrorRed,
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
)

private val LightColorScheme = lightColorScheme(
    primary = TrustBlue,
    secondary = TaxiYellow,
    tertiary = InfoBlue,
    background = BackgroundLight,
    surface = Color.White,
    error = ErrorRed,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
)

/**
 * 系統字體縮放上限
 *
 * 本 App 已針對老年人設計大字體（WarmTypography），
 * 若再套用系統級的大字體設定會導致嚴重爆版。
 * 因此限制 fontScale 最大為 1.0，確保 UI 佈局穩定。
 */
private const val MAX_FONT_SCALE = 1.0f

@Composable
fun HualienTaxiDriverTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
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
            typography = Typography,
            content = content
        )
    }
}
