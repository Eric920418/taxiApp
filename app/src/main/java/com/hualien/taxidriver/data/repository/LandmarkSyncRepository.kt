package com.hualien.taxidriver.data.repository

import android.util.Log
import com.hualien.taxidriver.data.remote.PassengerApiService
import com.hualien.taxidriver.data.remote.dto.RemoteLandmarkDto
import com.hualien.taxidriver.service.HualienLocalAddressDB
import com.hualien.taxidriver.service.HualienLocalAddressDB.LocalLandmark
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * LandmarkSyncRepository
 *
 * 負責從 Server 拉最新地標清單並合併到 HualienLocalAddressDB 的動態索引。
 * 失敗不影響 App 使用 — hardcoded LANDMARKS 作為 fallback。
 *
 * 使用方式：
 *   LandmarkSyncRepository(passengerApi).syncInBackground()
 *   - MainActivity.onCreate 呼叫，不 block 啟動流程
 *
 * 同步策略：
 *   - 每次啟動都全量同步（since=null）— 資料量小（100 筆 ~ 30KB）
 *   - 不支援刪除 hardcoded 的 97 筆（永遠保留為 offline fallback）
 *   - 同名時 Server 版本優先（Admin 修正的座標/別名會生效）
 */
class LandmarkSyncRepository(
    private val api: PassengerApiService
) {
    companion object {
        private const val TAG = "LandmarkSync"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Fire-and-forget 背景同步。失敗只 log，不 throw。
     */
    fun syncInBackground() {
        scope.launch {
            try {
                sync()
            } catch (e: Exception) {
                Log.w(TAG, "地標同步失敗，退回使用 hardcoded 資料：${e.message}", e)
            }
        }
    }

    private suspend fun sync() {
        val response = api.syncLandmarks(since = null)
        if (!response.isSuccessful) {
            Log.w(TAG, "sync HTTP ${response.code()}: ${response.errorBody()?.string()}")
            return
        }

        val body = response.body() ?: return
        if (!body.success) {
            Log.w(TAG, "sync 回 success=false: ${body.error}")
            return
        }

        val localLandmarks = body.landmarks.map(::toLocalLandmark)
        HualienLocalAddressDB.applyRemoteLandmarks(localLandmarks, emptySet())

        Log.d(TAG, "同步完成：收到 ${localLandmarks.size} 筆，版本 ${body.version}")
    }

    private fun toLocalLandmark(dto: RemoteLandmarkDto): LocalLandmark {
        return LocalLandmark(
            name = dto.name,
            lat = dto.lat,
            lng = dto.lng,
            address = dto.address,
            dropoffLat = dto.dropoffLat,
            dropoffLng = dto.dropoffLng,
            dropoffAddress = dto.dropoffAddress,
            category = dto.category,
            aliases = dto.aliases,
            priority = dto.priority
        )
    }
}
