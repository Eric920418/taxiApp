package com.hualien.taxidriver.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hualien.taxidriver.utils.AccessibilityManager
import kotlinx.coroutines.launch

/**
 * 無障礙設定畫面
 * 提供中老年人友善模式的設定選項
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessibilitySettingsScreen(
    onBackClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val accessibilityManager = remember { AccessibilityManager(context) }
    val scope = rememberCoroutineScope()

    // 收集設定狀態
    val isSeniorMode by accessibilityManager.isSeniorMode.collectAsState(initial = false)
    val textScale by accessibilityManager.textScale.collectAsState(initial = 1.0f)
    val isHighContrast by accessibilityManager.isHighContrast.collectAsState(initial = false)
    val isSimplifiedUI by accessibilityManager.isSimplifiedUI.collectAsState(initial = false)
    val isVoiceFeedback by accessibilityManager.isVoiceFeedbackEnabled.collectAsState(initial = false)
    val isConfirmActions by accessibilityManager.isConfirmActionsEnabled.collectAsState(initial = false)
    val isLargeButtons by accessibilityManager.isLargeButtonsEnabled.collectAsState(initial = false)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "無障礙設定",
                        fontSize = if (isSeniorMode) 28.sp else 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.size(if (isSeniorMode) 64.dp else 48.dp)
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "返回",
                            modifier = Modifier.size(if (isSeniorMode) 36.dp else 24.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 快速切換卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSeniorMode)
                        Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "設定",
                        modifier = Modifier.size(64.dp),
                        tint = if (isSeniorMode) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = if (isSeniorMode) "中老年人模式已開啟" else "標準模式",
                        fontSize = if (isSeniorMode) 24.sp else 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (isSeniorMode)
                            "已優化字體大小、按鈕尺寸和操作流程"
                        else
                            "點擊下方按鈕開啟中老年人友善模式",
                        fontSize = if (isSeniorMode) 18.sp else 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            scope.launch {
                                accessibilityManager.toggleSeniorMode()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (isSeniorMode) 72.dp else 56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSeniorMode)
                                Color(0xFFF44336) else Color(0xFF4CAF50)
                        )
                    ) {
                        Text(
                            text = if (isSeniorMode) "關閉中老年人模式" else "開啟中老年人模式",
                            fontSize = if (isSeniorMode) 22.sp else 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 詳細設定區域
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "詳細設定",
                        fontSize = if (isSeniorMode) 24.sp else 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // 簡化介面
                    SettingSwitch(
                        title = "簡化介面",
                        description = "隱藏複雜功能，只顯示核心操作",
                        icon = Icons.Default.Face,
                        checked = isSimplifiedUI,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                accessibilityManager.setSimplifiedUI(enabled)
                            }
                        },
                        isLarge = isSeniorMode
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // 高對比度
                    SettingSwitch(
                        title = "高對比度",
                        description = "增強顏色對比，讓文字更清晰",
                        icon = Icons.Default.Info,
                        checked = isHighContrast,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                accessibilityManager.setHighContrast(enabled)
                            }
                        },
                        isLarge = isSeniorMode
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // 操作確認
                    SettingSwitch(
                        title = "操作確認",
                        description = "重要操作需要二次確認",
                        icon = Icons.Default.Warning,
                        checked = isConfirmActions,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                accessibilityManager.setConfirmActions(enabled)
                            }
                        },
                        isLarge = isSeniorMode
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // 大按鈕
                    SettingSwitch(
                        title = "大按鈕",
                        description = "放大所有按鈕，方便點擊",
                        icon = Icons.Default.CheckCircle,
                        checked = isLargeButtons,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                accessibilityManager.setLargeButtons(enabled)
                            }
                        },
                        isLarge = isSeniorMode
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // 文字縮放
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = "文字大小",
                                modifier = Modifier.size(if (isSeniorMode) 32.dp else 24.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "文字大小",
                                    fontSize = if (isSeniorMode) 20.sp else 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "目前：${(textScale * 100).toInt()}%",
                                    fontSize = if (isSeniorMode) 16.sp else 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            TextSizeButton(
                                text = "小",
                                scale = 0.8f,
                                currentScale = textScale,
                                onClick = {
                                    scope.launch {
                                        accessibilityManager.setTextScale(0.8f)
                                    }
                                },
                                isLarge = isSeniorMode
                            )
                            TextSizeButton(
                                text = "標準",
                                scale = 1.0f,
                                currentScale = textScale,
                                onClick = {
                                    scope.launch {
                                        accessibilityManager.setTextScale(1.0f)
                                    }
                                },
                                isLarge = isSeniorMode
                            )
                            TextSizeButton(
                                text = "大",
                                scale = 1.3f,
                                currentScale = textScale,
                                onClick = {
                                    scope.launch {
                                        accessibilityManager.setTextScale(1.3f)
                                    }
                                },
                                isLarge = isSeniorMode
                            )
                            TextSizeButton(
                                text = "特大",
                                scale = 1.5f,
                                currentScale = textScale,
                                onClick = {
                                    scope.launch {
                                        accessibilityManager.setTextScale(1.5f)
                                    }
                                },
                                isLarge = isSeniorMode
                            )
                        }
                    }
                }
            }

            // 使用說明
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFFF8E1)
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "提示",
                            modifier = Modifier.size(if (isSeniorMode) 32.dp else 24.dp),
                            tint = Color(0xFFFF9800)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "使用提示",
                            fontSize = if (isSeniorMode) 22.sp else 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = buildString {
                            appendLine("• 開啟中老年人模式會自動調整介面")
                            appendLine("• 字體會放大到更容易閱讀的大小")
                            appendLine("• 按鈕會變大，更容易點擊")
                            appendLine("• 重要操作會要求二次確認，避免誤操作")
                            append("• 您可以隨時切換回標準模式")
                        },
                        fontSize = if (isSeniorMode) 18.sp else 14.sp,
                        lineHeight = if (isSeniorMode) 28.sp else 22.sp
                    )
                }
            }
        }
    }
}

/**
 * 設定開關項目
 */
@Composable
fun SettingSwitch(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isLarge: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (isLarge) 8.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(if (isLarge) 32.dp else 24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    fontSize = if (isLarge) 20.sp else 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    fontSize = if (isLarge) 16.sp else 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.scale(if (isLarge) 1.3f else 1.0f)
        )
    }
}

/**
 * 文字大小按鈕
 */
@Composable
fun TextSizeButton(
    text: String,
    scale: Float,
    currentScale: Float,
    onClick: () -> Unit,
    isLarge: Boolean = false
) {
    val isSelected = kotlin.math.abs(currentScale - scale) < 0.01f

    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .height(if (isLarge) 56.dp else 48.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primary else Color.Transparent,
            contentColor = if (isSelected)
                Color.White else MaterialTheme.colorScheme.primary
        )
    ) {
        Text(
            text = text,
            fontSize = if (isLarge) 18.sp else 14.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}