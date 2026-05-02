package com.hualien.taxidriver.ui.screens.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hualien.taxidriver.domain.model.UserRole

/**
 * 登入歡迎頁（Image #3 樣式）
 *
 * 流程位置：RoleSelectionScreen → 此畫面 → DriverPhoneLoginScreen / PassengerPhoneLoginScreen
 *
 * @param role 目前角色，決定右下角頭像 icon 與按鈕顏色
 * @param onContinue 點「登錄」進入手機 OTP 流程
 * @param onSwitchRole 點右下角頭像切換角色（例如司機 → 乘客）
 * @param onBack 系統返回鍵回到 RoleSelectionScreen
 */
@Composable
fun WelcomeLoginScreen(
    role: UserRole,
    onContinue: () -> Unit,
    onSwitchRole: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler { onBack() }

    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFFFAFAFA), Color(0xFFEEEEEE))
    )
    val buttonBrush = Brush.horizontalGradient(
        colors = listOf(Color(0xFFFFC107), Color(0xFFFFB300))
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        // 上半弧形白色卡片：放 logo + slogan
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(bottomStart = 240.dp, bottomEnd = 240.dp),
            shadowElevation = 1.dp,
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(420.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "GoGoCha",
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF1A1A1A),
                    letterSpacing = (-1).sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "有溫度的操作服務",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF555555),
                    letterSpacing = 4.sp
                )
            }
        }

        // 下半部：登錄按鈕 + 提示文字
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 32.dp)
                .padding(bottom = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom
        ) {
            // 黃色「登錄」按鈕
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(20.dp),
                        ambientColor = Color(0xFFFFB300),
                        spotColor = Color(0xFFFFB300)
                    )
                    .clip(RoundedCornerShape(20.dp))
                    .background(buttonBrush)
                    .clickable(onClick = onContinue)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "登 錄",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A1A1A),
                    letterSpacing = 6.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "點擊開始使用",
                fontSize = 14.sp,
                color = Color(0xFF888888),
                letterSpacing = 2.sp
            )
        }

        // 右下角角色頭像（點擊切換角色）
        Surface(
            shape = CircleShape,
            color = Color.White,
            shadowElevation = 6.dp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(end = 24.dp, bottom = 24.dp)
                .size(64.dp)
                .clip(CircleShape)
                .clickable(onClick = onSwitchRole)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = when (role) {
                        UserRole.DRIVER -> Icons.Filled.DirectionsCar
                        UserRole.PASSENGER -> Icons.Filled.Person
                    },
                    contentDescription = when (role) {
                        UserRole.DRIVER -> "切換到乘客端"
                        UserRole.PASSENGER -> "切換到司機端"
                    },
                    tint = Color(0xFF333333),
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}
