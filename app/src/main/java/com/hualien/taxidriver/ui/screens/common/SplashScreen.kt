package com.hualien.taxidriver.ui.screens.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.hualien.taxidriver.R
import kotlinx.coroutines.delay

/**
 * GoGoCha 啟動封面
 *
 * 顯示時序：
 *   0ms        進入畫面（白底＋整張封面圖，alpha=1）
 *   HOLD_MS    開始 fade out（FADE_MS）
 *   HOLD+FADE  onFinished() — 切換到下游路由
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var startFade by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (startFade) 0f else 1f,
        animationSpec = tween(durationMillis = FADE_MS, easing = LinearEasing),
        label = "splashAlpha"
    )

    LaunchedEffect(Unit) {
        delay(HOLD_MS.toLong())
        startFade = true
        delay(FADE_MS.toLong())
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .alpha(alpha),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.splash_cover),
            contentDescription = "GoGoCha 啟動畫面",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}

private const val HOLD_MS = 1500
private const val FADE_MS = 350
