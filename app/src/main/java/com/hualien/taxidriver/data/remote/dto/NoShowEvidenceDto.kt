package com.hualien.taxidriver.data.remote.dto

/**
 * 模組 4：no-show 拍照存證 — 上傳後 server 回應
 * 對應後端 POST /api/orders/:orderId/no-show-evidence
 */
data class NoShowEvidenceResponse(
    val success: Boolean,
    val evidenceId: Long? = null,
    val photoUrl: String? = null,
    val capturedAt: String? = null,
    val error: String? = null,
)
