package com.hualien.taxidriver

import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.hualien.taxidriver.data.remote.RetrofitClient
import com.hualien.taxidriver.data.repository.OrderRepository
import com.hualien.taxidriver.domain.model.Order
import com.hualien.taxidriver.domain.model.OrderStatus
import com.hualien.taxidriver.domain.model.PaymentType
import com.hualien.taxidriver.ui.theme.HualienTaxiDriverTheme
import com.hualien.taxidriver.utils.DataStoreManager
import com.hualien.taxidriver.utils.IncomingOrderNotifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 全螢幕接單頁（根治版背景接單）。
 *
 * 由 FCM（背景/被殺）或 LocationService（背景但進程活著）的 full-screen intent 通知拉起。
 * 鎖屏/螢幕關時直接覆蓋顯示，司機可直接接受/拒絕；接受成功 → 跳 MainActivity 進行程；
 * 單已被接走/逾時 → 顯示提示後自動關閉。
 */
class IncomingOrderActivity : ComponentActivity() {

    // lazy：等 onCreate 的 RetrofitClient.init 跑完才建（否則 OkHttp 會在沒 auth interceptor 下被建出 → 接單 401）
    private val repo by lazy { OrderRepository() }
    private var ringtone: Ringtone? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 被殺後由 FCM 拉起時，進程是全新的 → 補初始化 API + token 快取（否則接單會 401）
        RetrofitClient.init(this)
        lifecycleScope.launch {
            runCatching { DataStoreManager.getInstance(applicationContext).initializeTokenCache() }
        }

        showOverLockScreen()

        val orderId = intent?.getStringExtra(IncomingOrderNotifier.EXTRA_ORDER_ID).orEmpty()
        if (orderId.isBlank()) { finish(); return }
        IncomingOrderNotifier.cancel(this, orderId)
        startRingtone()

        val initialName = intent?.getStringExtra(IncomingOrderNotifier.EXTRA_PASSENGER_NAME)
        val initialPickup = intent?.getStringExtra(IncomingOrderNotifier.EXTRA_PICKUP)

        setContent {
            HualienTaxiDriverTheme {
                IncomingOrderScreen(
                    orderId = orderId,
                    initialName = initialName,
                    initialPickup = initialPickup,
                    repo = repo,
                    dataStore = DataStoreManager.getInstance(this),
                    onAccepted = { goToMainActivity(orderId) },
                    onDismiss = { stopRingtone(); finish() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        // 同一 Activity 在顯示中又被新單拉起 → 以新 orderId 重建內容
        intent?.getStringExtra(IncomingOrderNotifier.EXTRA_ORDER_ID)
            ?.takeIf { it.isNotBlank() }
            ?.let { recreate() }
    }

    private fun goToMainActivity(orderId: String) {
        stopRingtone()
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("orderId", orderId)
        })
        finish()
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun startRingtone() {
        runCatching {
            ringtone = RingtoneManager
                .getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
                ?.also { it.play() }
        }
    }

    private fun stopRingtone() {
        runCatching { ringtone?.stop() }
        ringtone = null
    }

    override fun onDestroy() {
        stopRingtone()
        super.onDestroy()
    }
}

private const val DECISION_SECONDS = 30

@Composable
private fun IncomingOrderScreen(
    orderId: String,
    initialName: String?,
    initialPickup: String?,
    repo: OrderRepository,
    dataStore: DataStoreManager,
    onAccepted: () -> Unit,
    onDismiss: () -> Unit,
) {
    var order by remember { mutableStateOf<Order?>(null) }
    var loading by remember { mutableStateOf(true) }
    var working by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var remaining by remember { mutableStateOf(DECISION_SECONDS) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(orderId) {
        repo.getOrderById(orderId)
            .onSuccess { o ->
                if (o.status == OrderStatus.OFFERED || o.status == OrderStatus.WAITING) {
                    order = o
                } else {
                    message = "這張單已被接走或已結束"
                }
                loading = false
            }
            .onFailure {
                message = "讀取訂單失敗：${it.message}"
                loading = false
            }
    }

    // 已被接走/逾時 → 短暫顯示後自動關閉
    LaunchedEffect(message) {
        if (message != null) {
            delay(2500)
            onDismiss()
        }
    }

    // 30 秒倒數，沒接就自動關閉（後端自有 batch timeout，這裡只是不卡住畫面）
    LaunchedEffect(order) {
        if (order != null && message == null) {
            while (remaining > 0 && !working) {
                delay(1000)
                remaining -= 1
            }
            if (remaining <= 0 && !working) onDismiss()
        }
    }

    fun accept() {
        if (working) return
        working = true
        scope.launch {
            val driverId = dataStore.getDriverId()
            val driverName = dataStore.driverName.first() ?: ""
            if (driverId.isNullOrBlank()) {
                message = "尚未登入，無法接單"
                working = false
                return@launch
            }
            repo.acceptOrder(orderId, driverId, driverName)
                .onSuccess { onAccepted() }
                .onFailure {
                    message = "接單失敗：${it.message}（可能已被接走）"
                    working = false
                }
        }
    }

    fun reject() {
        if (working) return
        working = true
        scope.launch {
            val driverId = dataStore.getDriverId() ?: ""
            runCatching { repo.rejectOrder(orderId, driverId, "司機忙碌") }
            onDismiss()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF101626)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(28.dp))
            Text("🚕 新訂單", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(20.dp))

            when {
                loading -> CircularProgressIndicator(color = Color.White)

                message != null -> Text(
                    message!!,
                    fontSize = 22.sp,
                    color = Color(0xFFFFCDD2),
                    fontWeight = FontWeight.Bold,
                )

                order != null -> {
                    val o = order!!
                    InfoRow("上車", o.pickup.address ?: initialPickup ?: "—")
                    o.destination?.address?.let { InfoRow("目的地", it) }
                    o.estimatedFare?.let { InfoRow("預估車資", "NT$ $it") }
                    o.distanceToPickup?.let { d ->
                        val eta = o.etaToPickup?.let { "（約 $it 分）" } ?: ""
                        InfoRow("距上車", "$d km$eta")
                    }
                    InfoRow("乘客", o.passengerName)
                    InfoRow("付款", paymentLabel(o.paymentType))

                    Spacer(Modifier.height(28.dp))
                    Button(
                        onClick = { accept() },
                        enabled = !working,
                        modifier = Modifier.fillMaxWidth().height(76.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                    ) {
                        Text(
                            if (working) "處理中…" else "接受（${remaining}s）",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    OutlinedButton(
                        onClick = { reject() },
                        enabled = !working,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        Text("拒絕", fontSize = 20.sp, color = Color.White)
                    }
                }
            }
        }
    }
}

private fun paymentLabel(p: PaymentType): String = when (p.name) {
    "CASH" -> "現金"
    else -> "刷卡"
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Text(label, fontSize = 18.sp, color = Color(0xFFB0BEC5), modifier = Modifier.width(86.dp))
        Text(value, fontSize = 20.sp, color = Color.White, fontWeight = FontWeight.Medium)
    }
}
