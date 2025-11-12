package com.hualien.taxidriver.manager

import android.location.Location
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 智能訂單管理器
 * 自動處理訂單狀態轉換，減少手動操作
 */
class SmartOrderManager {

    // 訂單狀態
    private val _orderState = MutableStateFlow<SmartOrderState>(SmartOrderState.NoOrder)
    val orderState: StateFlow<SmartOrderState> = _orderState.asStateFlow()

    // 當前訂單
    private var currentOrder: com.hualien.taxidriver.domain.model.Order? = null

    // 司機當前位置
    private var currentLocation: Location? = null

    // 距離閾值（公尺）
    companion object {
        const val ARRIVAL_THRESHOLD = 50.0  // 50公尺內視為到達
        const val DESTINATION_THRESHOLD = 100.0  // 100公尺內視為到達目的地
        const val AUTO_TRANSITION_DELAY = 3000L  // 3秒後自動轉換
        const val SPEED_THRESHOLD = 5.0  // 時速5公里以下視為停車
    }

    /**
     * 設定新訂單
     */
    fun setOrder(order: com.hualien.taxidriver.domain.model.Order) {
        currentOrder = order
        updateOrderState()
    }

    /**
     * 更新司機位置
     * 自動偵測是否到達上車點或目的地
     */
    fun updateLocation(location: Location) {
        currentLocation = location
        checkAutoTransition(location)
    }

    /**
     * 一鍵執行下一步操作
     * 根據當前狀態自動判斷下一步
     */
    fun executeNextAction(): NextAction {
        return when (val state = _orderState.value) {
            is SmartOrderState.WaitingForAccept -> {
                acceptOrder()
                NextAction.Accepted
            }
            is SmartOrderState.NavigatingToPickup -> {
                if (state.nearPickup) {
                    markArrived()
                    NextAction.Arrived
                } else {
                    NextAction.NavigateToPickup
                }
            }
            is SmartOrderState.ArrivedAtPickup -> {
                startTrip()
                NextAction.TripStarted
            }
            is SmartOrderState.OnTrip -> {
                if (state.nearDestination) {
                    endTrip()
                    NextAction.TripEnded
                } else {
                    NextAction.ShowDestination
                }
            }
            is SmartOrderState.WaitingForPayment -> {
                NextAction.SubmitFare
            }
            SmartOrderState.NoOrder -> {
                NextAction.NoAction
            }
        }
    }

    /**
     * 檢查自動狀態轉換
     */
    private fun checkAutoTransition(location: Location) {
        val order = currentOrder ?: return
        val speed = location.speed * 3.6  // 轉換為 km/h

        when (_orderState.value) {
            is SmartOrderState.NavigatingToPickup -> {
                val distance = calculateDistance(
                    location.latitude,
                    location.longitude,
                    order.pickup.latitude,
                    order.pickup.longitude
                )

                // 自動偵測到達上車點
                if (distance <= ARRIVAL_THRESHOLD) {
                    _orderState.value = SmartOrderState.NavigatingToPickup(
                        order = order,
                        distanceToPickup = distance,
                        nearPickup = true,
                        autoTransitionCountdown = AUTO_TRANSITION_DELAY
                    )

                    // 如果車速很低，可能已經停車等乘客
                    if (speed < SPEED_THRESHOLD) {
                        scheduleAutoTransition {
                            markArrived()
                        }
                    }
                } else {
                    _orderState.value = SmartOrderState.NavigatingToPickup(
                        order = order,
                        distanceToPickup = distance,
                        nearPickup = false
                    )
                }
            }

            is SmartOrderState.OnTrip -> {
                val currentState = _orderState.value as SmartOrderState.OnTrip
                order.destination?.let { destination ->
                    val distance = calculateDistance(
                        location.latitude,
                        location.longitude,
                        destination.latitude,
                        destination.longitude
                    )

                    // 自動偵測到達目的地
                    if (distance <= DESTINATION_THRESHOLD && speed < SPEED_THRESHOLD) {
                        _orderState.value = SmartOrderState.OnTrip(
                            order = order,
                            distanceToDestination = distance,
                            nearDestination = true,
                            tripStartTime = currentState.tripStartTime,
                            tripDuration = System.currentTimeMillis() - currentState.tripStartTime
                        )

                        scheduleAutoTransition {
                            endTrip()
                        }
                    } else {
                        _orderState.value = SmartOrderState.OnTrip(
                            order = order,
                            distanceToDestination = distance,
                            nearDestination = false,
                            tripStartTime = currentState.tripStartTime,
                            tripDuration = System.currentTimeMillis() - currentState.tripStartTime
                        )
                    }
                }
            }

            else -> {}
        }
    }

    /**
     * 計算兩點距離（Haversine公式）
     */
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0  // 地球半徑（公尺）
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }

    /**
     * 排程自動轉換
     */
    private fun scheduleAutoTransition(action: () -> Unit) {
        // 這裡應該使用協程延遲執行
        // 實際實作時需要注入 CoroutineScope
        action()
    }

    /**
     * 接受訂單
     */
    private fun acceptOrder() {
        currentOrder?.let { order ->
            _orderState.value = SmartOrderState.NavigatingToPickup(
                order = order,
                distanceToPickup = 0.0,
                nearPickup = false
            )
        }
    }

    /**
     * 標記已到達
     */
    private fun markArrived() {
        currentOrder?.let { order ->
            _orderState.value = SmartOrderState.ArrivedAtPickup(
                order = order,
                waitingTime = 0L
            )
        }
    }

    /**
     * 開始行程
     */
    private fun startTrip() {
        currentOrder?.let { order ->
            _orderState.value = SmartOrderState.OnTrip(
                order = order,
                distanceToDestination = 0.0,
                nearDestination = false,
                tripStartTime = System.currentTimeMillis(),
                tripDuration = 0L
            )
        }
    }

    /**
     * 結束行程
     */
    private fun endTrip() {
        currentOrder?.let { order ->
            _orderState.value = SmartOrderState.WaitingForPayment(order)
        }
    }

    /**
     * 完成訂單
     */
    fun completeOrder() {
        currentOrder = null
        _orderState.value = SmartOrderState.NoOrder
    }

    /**
     * 更新訂單狀態
     */
    private fun updateOrderState() {
        currentOrder?.let { order ->
            _orderState.value = when (order.status) {
                com.hualien.taxidriver.domain.model.OrderStatus.OFFERED ->
                    SmartOrderState.WaitingForAccept(order)
                com.hualien.taxidriver.domain.model.OrderStatus.ACCEPTED ->
                    SmartOrderState.NavigatingToPickup(order, 0.0, false)
                com.hualien.taxidriver.domain.model.OrderStatus.ARRIVED ->
                    SmartOrderState.ArrivedAtPickup(order, 0L)
                com.hualien.taxidriver.domain.model.OrderStatus.ON_TRIP ->
                    SmartOrderState.OnTrip(order, 0.0, false, System.currentTimeMillis(), 0L)
                com.hualien.taxidriver.domain.model.OrderStatus.SETTLING ->
                    SmartOrderState.WaitingForPayment(order)
                else -> SmartOrderState.NoOrder
            }
        }
    }
}

/**
 * 智能訂單狀態
 */
sealed class SmartOrderState {
    object NoOrder : SmartOrderState()

    data class WaitingForAccept(
        val order: com.hualien.taxidriver.domain.model.Order
    ) : SmartOrderState()

    data class NavigatingToPickup(
        val order: com.hualien.taxidriver.domain.model.Order,
        val distanceToPickup: Double,
        val nearPickup: Boolean,
        val autoTransitionCountdown: Long = 0
    ) : SmartOrderState()

    data class ArrivedAtPickup(
        val order: com.hualien.taxidriver.domain.model.Order,
        val waitingTime: Long
    ) : SmartOrderState()

    data class OnTrip(
        val order: com.hualien.taxidriver.domain.model.Order,
        val distanceToDestination: Double,
        val nearDestination: Boolean,
        val tripStartTime: Long,
        val tripDuration: Long
    ) : SmartOrderState()

    data class WaitingForPayment(
        val order: com.hualien.taxidriver.domain.model.Order
    ) : SmartOrderState()
}

/**
 * 下一步操作
 */
enum class NextAction {
    NoAction,
    Accepted,
    NavigateToPickup,
    Arrived,
    TripStarted,
    ShowDestination,
    TripEnded,
    SubmitFare
}