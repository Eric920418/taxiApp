package com.hualien.taxidriver.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * 電話審核 API DTOs
 */

// === Response ===

data class PhoneReviewListResponse(
    val success: Boolean,
    val calls: List<PhoneReviewCallDto>,
    val total: Int
)

data class PhoneReviewCountResponse(
    val success: Boolean,
    val count: Int
)

data class PhoneReviewActionResponse(
    val success: Boolean,
    val callId: String,
    val action: String,
    val message: String
)

data class PhoneReviewCallDto(
    val callId: String,
    val callerNumber: String,
    val durationSeconds: Int?,
    val recordingUrl: String?,
    val processingStatus: String,
    val transcript: String?,
    val parsedFields: PhoneReviewParsedFields?,
    val eventType: String?,
    val eventConfidence: Double?,
    val fieldConfidence: Double?,
    val createdAt: String?
)

data class PhoneReviewParsedFields(
    @SerializedName("pickup_address") val pickupAddress: String?,
    @SerializedName("destination_address") val destinationAddress: String?,
    @SerializedName("customer_name") val customerName: String?,
    @SerializedName("passenger_count") val passengerCount: Int?,
    @SerializedName("subsidy_type") val subsidyType: String?,
    @SerializedName("pet_present") val petPresent: String?,
    @SerializedName("pet_carrier") val petCarrier: String?,
    @SerializedName("special_notes") val specialNotes: String?,
    val confidence: Double?
)

// === Request ===

data class PhoneReviewRequest(
    val action: String,           // "APPROVED" or "REJECTED"
    val editedFields: Map<String, Any?>?,
    val note: String?
)
