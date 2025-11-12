package com.hualien.taxidriver.data.remote.dto

import com.hualien.taxidriver.domain.model.Order

/**
 * 訂單列表回應
 */
data class OrderListResponse(
    val orders: List<Order>,
    val total: Int
)

/**
 * 接受訂單回應
 */
data class AcceptOrderResponse(
    val success: Boolean,
    val message: String,
    val order: Order
)

/**
 * 拒絕訂單回應
 */
data class RejectOrderResponse(
    val success: Boolean,
    val message: String
)
