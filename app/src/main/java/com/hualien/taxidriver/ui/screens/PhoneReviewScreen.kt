package com.hualien.taxidriver.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hualien.taxidriver.data.remote.dto.PhoneReviewCallDto
import com.hualien.taxidriver.viewmodel.PhoneReviewViewModel

private val WarningOrange = Color(0xFFFF9800)
private val ApproveGreen = Color(0xFF4CAF50)
private val RejectRed = Color(0xFFF44336)
private val CardBg = Color(0xFFFFF8E1)
private val ScreenBg = Color(0xFFECEFF1)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneReviewScreen(
    driverId: String,
    viewModel: PhoneReviewViewModel = viewModel(),
    onBackClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // 選中的電話記錄（展開審核）
    var selectedCall by remember { mutableStateOf<PhoneReviewCallDto?>(null) }

    // 審核表單欄位
    var pickupAddress by remember { mutableStateOf("") }
    var destinationAddress by remember { mutableStateOf("") }
    var customerName by remember { mutableStateOf("") }
    var subsidyType by remember { mutableStateOf("NONE") }
    var reviewNote by remember { mutableStateOf("") }

    LaunchedEffect(driverId) {
        viewModel.loadReviews(driverId)
    }

    // 顯示訊息
    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            selectedCall = null
            viewModel.clearMessages()
        }
    }
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("電話客服審核", fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadReviews(driverId) }) {
                        Icon(Icons.Default.Refresh, "重新整理")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1976D2),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(ScreenBg)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.calls.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = ApproveGreen,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "目前沒有需要審核的電話",
                            fontSize = 18.sp,
                            color = Color(0xFF757575)
                        )
                    }
                }
                selectedCall != null -> {
                    // 審核詳情
                    ReviewDetailView(
                        call = selectedCall!!,
                        pickupAddress = pickupAddress,
                        destinationAddress = destinationAddress,
                        customerName = customerName,
                        subsidyType = subsidyType,
                        reviewNote = reviewNote,
                        isSubmitting = uiState.isSubmitting,
                        onPickupChange = { pickupAddress = it },
                        onDestinationChange = { destinationAddress = it },
                        onCustomerNameChange = { customerName = it },
                        onSubsidyTypeChange = { subsidyType = it },
                        onNoteChange = { reviewNote = it },
                        onApprove = {
                            val edited = mutableMapOf<String, Any?>(
                                "pickup_address" to pickupAddress,
                                "destination_address" to destinationAddress.ifBlank { null },
                                "customer_name" to customerName.ifBlank { null },
                                "subsidy_type" to subsidyType
                            )
                            viewModel.submitReview(
                                driverId = driverId,
                                callId = selectedCall!!.callId,
                                action = "APPROVED",
                                editedFields = edited,
                                note = reviewNote.ifBlank { null }
                            )
                        },
                        onReject = {
                            viewModel.submitReview(
                                driverId = driverId,
                                callId = selectedCall!!.callId,
                                action = "REJECTED",
                                note = reviewNote.ifBlank { null }
                            )
                        },
                        onBack = { selectedCall = null }
                    )
                }
                else -> {
                    // 列表
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Text(
                                "待審核：${uiState.calls.size} 通",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = WarningOrange
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        items(uiState.calls, key = { it.callId }) { call ->
                            ReviewCallCard(
                                call = call,
                                onClick = {
                                    selectedCall = call
                                    // 預填表單
                                    pickupAddress = call.parsedFields?.pickupAddress ?: ""
                                    destinationAddress = call.parsedFields?.destinationAddress ?: ""
                                    customerName = call.parsedFields?.customerName ?: ""
                                    subsidyType = call.parsedFields?.subsidyType ?: "NONE"
                                    reviewNote = ""
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewCallCard(
    call: PhoneReviewCallDto,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 標題行：電話號碼 + 時間
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Phone,
                        contentDescription = null,
                        tint = WarningOrange,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        call.callerNumber,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
                Text(
                    "${call.durationSeconds ?: 0}秒",
                    fontSize = 14.sp,
                    color = Color(0xFF757575)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 逐字稿
            if (!call.transcript.isNullOrBlank()) {
                Text(
                    "\"${call.transcript}\"",
                    fontSize = 15.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = Color(0xFF424242)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // GPT 解析結果
            if (call.parsedFields != null) {
                val pf = call.parsedFields
                Row {
                    if (!pf.pickupAddress.isNullOrBlank()) {
                        AssistChip(
                            onClick = {},
                            label = { Text("上車: ${pf.pickupAddress}", fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(16.dp))
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    if (!pf.destinationAddress.isNullOrBlank()) {
                        AssistChip(
                            onClick = {},
                            label = { Text("到: ${pf.destinationAddress}", fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.Flag, null, modifier = Modifier.size(16.dp))
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 信心度
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("信心度: ", fontSize = 13.sp, color = Color(0xFF757575))
                val conf = call.eventConfidence ?: call.fieldConfidence ?: 0.0
                val confPercent = (conf * 100).toInt()
                val confColor = when {
                    conf >= 0.7 -> ApproveGreen
                    conf >= 0.5 -> WarningOrange
                    else -> RejectRed
                }
                Text(
                    "${confPercent}%",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = confColor
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "點擊審核 →",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1976D2)
                )
            }
        }
    }
}

@Composable
private fun ReviewDetailView(
    call: PhoneReviewCallDto,
    pickupAddress: String,
    destinationAddress: String,
    customerName: String,
    subsidyType: String,
    reviewNote: String,
    isSubmitting: Boolean,
    onPickupChange: (String) -> Unit,
    onDestinationChange: (String) -> Unit,
    onCustomerNameChange: (String) -> Unit,
    onSubsidyTypeChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onBack: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 返回按鈕
        item {
            TextButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("返回列表")
            }
        }

        // 來電資訊
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("來電資訊", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    InfoRow("電話號碼", call.callerNumber)
                    InfoRow("通話時長", "${call.durationSeconds ?: 0} 秒")
                    InfoRow("事件類型", when(call.eventType) {
                        "NEW_ORDER" -> "新訂單"
                        "URGE" -> "催單"
                        "CANCEL" -> "取消"
                        "CHANGE" -> "變更"
                        else -> call.eventType ?: "未知"
                    })
                }
            }
        }

        // 逐字稿
        if (!call.transcript.isNullOrBlank()) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("客人說了什麼", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            call.transcript,
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                            color = Color(0xFF212121)
                        )
                    }
                }
            }
        }

        // 編輯欄位
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("訂單資訊（可修改）", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = pickupAddress,
                        onValueChange = onPickupChange,
                        label = { Text("上車地點 *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = destinationAddress,
                        onValueChange = onDestinationChange,
                        label = { Text("目的地（選填）") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = customerName,
                        onValueChange = onCustomerNameChange,
                        label = { Text("乘客姓名（選填）") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // 補貼類型
                    Text("補貼類型", fontSize = 14.sp, color = Color(0xFF757575))
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "NONE" to "無",
                            "SENIOR_CARD" to "敬老卡",
                            "LOVE_CARD" to "愛心卡"
                        ).forEach { (value, label) ->
                            FilterChip(
                                selected = subsidyType == value,
                                onClick = { onSubsidyTypeChange(value) },
                                label = { Text(label, fontSize = 14.sp) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = reviewNote,
                        onValueChange = onNoteChange,
                        label = { Text("備註（選填）") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                }
            }
        }

        // 操作按鈕
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 拒絕
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    enabled = !isSubmitting,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RejectRed),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                        brush = androidx.compose.ui.graphics.SolidColor(RejectRed)
                    )
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Close, null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("拒絕", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // 核准
                Button(
                    onClick = {
                        if (pickupAddress.isBlank()) return@Button
                        onApprove()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    enabled = !isSubmitting && pickupAddress.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = ApproveGreen)
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Icon(Icons.Default.Check, null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("核准建單", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            label,
            fontSize = 14.sp,
            color = Color(0xFF757575),
            modifier = Modifier.width(80.dp)
        )
        Text(value, fontSize = 14.sp, color = Color(0xFF212121))
    }
}