package com.hualien.taxidriver.ui.screens.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hualien.taxidriver.domain.model.UserRole

/**
 * 角色選擇畫面 — splash 風格（v1.4.6 改版）
 *
 * 主按鈕「登錄」進司機端，右下角車子 FAB 切乘客端。
 * 內部仍走原本 RoleManager 路由：DRIVER → MainNavigation（含 Firebase SMS 驗證），
 * PASSENGER → PassengerNavigation。
 */
@Composable
fun RoleSelectionScreen(
    onRoleSelected: (UserRole) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFEFEFEF))
    ) {
        // 上半部：白色「下凸圓弧」底，置中 LOGO + 標語
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.62f)
                .background(Color.White, shape = BottomArcShape(arcHeight = 64.dp))
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp)
                    .padding(bottom = 64.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "GoGoCha",
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF111111),
                )
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = "有溫度的操作服務",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF666666),
                    letterSpacing = 4.sp,
                )
            }
        }

        // 下半部：黃色「登錄」按鈕 + 說明文字
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 28.dp, vertical = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Button(
                onClick = { onRoleSelected(UserRole.DRIVER) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFB800),
                    contentColor = Color.White,
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "登 錄",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(28.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "點擊開始使用",
                fontSize = 14.sp,
                color = Color(0xFF888888),
                letterSpacing = 1.sp,
            )
        }

        // 右下角浮動車子 icon → 切換到乘客模式
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 24.dp)
                .size(56.dp)
                .shadow(elevation = 6.dp, shape = CircleShape)
                .background(Color.White, shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(
                onClick = { onRoleSelected(UserRole.PASSENGER) },
                modifier = Modifier.fillMaxSize(),
            ) {
                Icon(
                    imageVector = Icons.Default.DirectionsCar,
                    contentDescription = "乘客模式",
                    tint = Color(0xFF333333),
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

/**
 * 自訂 Shape — 矩形底邊有「向下凸」的圓弧
 *
 * 用 quadraticTo 畫一條二次貝茲曲線：
 *   - 起點：(width, height - arcHeight)
 *   - 控制點：(width/2, height + arcHeight)  ← 把曲線「拉到」矩形底部以下，造成凸出視覺
 *   - 終點：(0, height - arcHeight)
 *
 * 視覺等同上半圓形跟矩形的聯集，但僅用單一 Path 描述。
 */
private class BottomArcShape(private val arcHeight: Dp) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val arcPx = with(density) { arcHeight.toPx() }
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height - arcPx)
            quadraticTo(
                size.width / 2f, size.height + arcPx,
                0f, size.height - arcPx,
            )
            close()
        }
        return Outline.Generic(path)
    }
}
