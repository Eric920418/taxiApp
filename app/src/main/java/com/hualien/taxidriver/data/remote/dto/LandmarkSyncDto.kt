package com.hualien.taxidriver.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * 對應 Server GET /api/landmarks/sync 的回應結構
 */
data class LandmarkSyncResponse(
    val success: Boolean,
    val version: String?,
    val landmarks: List<RemoteLandmarkDto> = emptyList(),
    @SerializedName("deleted_names")
    val deletedNames: List<String> = emptyList(),
    val count: Int = 0,
    val error: String? = null,
    val stack: String? = null
)

data class RemoteLandmarkDto(
    val id: Int,
    val name: String,
    val lat: Double,
    val lng: Double,
    val address: String,
    val category: String,
    val district: String,
    val priority: Int,
    val aliases: List<String> = emptyList(),
    @SerializedName("taigi_aliases")
    val taigiAliases: List<String> = emptyList(),
    @SerializedName("dropoff_lat")
    val dropoffLat: Double? = null,
    @SerializedName("dropoff_lng")
    val dropoffLng: Double? = null,
    @SerializedName("dropoff_address")
    val dropoffAddress: String? = null,
    @SerializedName("updated_at")
    val updatedAt: String? = null
)
