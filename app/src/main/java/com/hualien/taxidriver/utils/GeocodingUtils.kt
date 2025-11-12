package com.hualien.taxidriver.utils

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.resume

/**
 * 反向地理編碼工具類
 * 將 GPS 座標轉換為可讀的地址
 */
object GeocodingUtils {

    private const val TAG = "GeocodingUtils"

    /**
     * 將經緯度座標轉換為地址字串（反向地理編碼）
     *
     * @param context Android Context
     * @param latLng GPS 座標
     * @param maxResults 最多返回幾個地址結果
     * @return 地址字串，失敗時返回座標字串
     */
    suspend fun getAddressFromLocation(
        context: Context,
        latLng: LatLng,
        maxResults: Int = 1
    ): String = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(context, java.util.Locale.TAIWAN)

            // Android 13 (API 33) 以上使用新的 async API
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                return@withContext getAddressAsync(geocoder, latLng, maxResults)
            } else {
                return@withContext getAddressSync(geocoder, latLng, maxResults)
            }

        } catch (e: IOException) {
            Log.e(TAG, "反向地理編碼失敗 (IOException)", e)
            return@withContext formatCoordinates(latLng)
        } catch (e: Exception) {
            Log.e(TAG, "反向地理編碼失敗", e)
            return@withContext formatCoordinates(latLng)
        }
    }

    /**
     * Android 13+ 異步方式獲取地址
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun getAddressAsync(
        geocoder: Geocoder,
        latLng: LatLng,
        maxResults: Int
    ): String = suspendCancellableCoroutine { continuation ->
        geocoder.getFromLocation(
            latLng.latitude,
            latLng.longitude,
            maxResults
        ) { addresses ->
            if (addresses.isNotEmpty()) {
                val address = formatAddress(addresses[0])
                Log.d(TAG, "反向地理編碼成功: $address")
                continuation.resume(address)
            } else {
                Log.w(TAG, "未找到地址，返回座標")
                continuation.resume(formatCoordinates(latLng))
            }
        }
    }

    /**
     * Android 13 以下同步方式獲取地址
     */
    @Suppress("DEPRECATION")
    private fun getAddressSync(
        geocoder: Geocoder,
        latLng: LatLng,
        maxResults: Int
    ): String {
        val addresses = geocoder.getFromLocation(
            latLng.latitude,
            latLng.longitude,
            maxResults
        )

        return if (!addresses.isNullOrEmpty()) {
            val address = formatAddress(addresses[0])
            Log.d(TAG, "反向地理編碼成功: $address")
            address
        } else {
            Log.w(TAG, "未找到地址，返回座標")
            formatCoordinates(latLng)
        }
    }

    /**
     * 格式化 Address 物件為可讀字串
     */
    private fun formatAddress(address: Address): String {
        // 嘗試多種格式，選擇最適合的
        return when {
            // 優先使用完整地址
            !address.getAddressLine(0).isNullOrBlank() -> {
                cleanAddress(address.getAddressLine(0))
            }
            // 如果沒有完整地址，組合各個部分
            else -> {
                buildString {
                    address.adminArea?.let { append(it) }  // 縣市
                    address.locality?.let { append(it) }   // 鄉鎮市區
                    address.thoroughfare?.let { append(it) }  // 街道
                    address.subThoroughfare?.let { append(it) }  // 門牌號
                    address.featureName?.let {
                        if (it != address.subThoroughfare) {
                            append(it)
                        }
                    }
                }.ifBlank {
                    formatCoordinates(LatLng(address.latitude, address.longitude))
                }
            }
        }
    }

    /**
     * 清理地址字串（移除重複的台灣、縣市名稱）
     */
    private fun cleanAddress(address: String): String {
        return address
            .replace("台灣", "")
            .replace("臺灣", "")
            .replace(Regex("^\\d+"), "")  // 移除開頭的郵遞區號
            .trim()
    }

    /**
     * 將座標格式化為字串（作為 fallback）
     */
    private fun formatCoordinates(latLng: LatLng): String {
        return "(${String.format("%.4f", latLng.latitude)}, ${String.format("%.4f", latLng.longitude)})"
    }

    /**
     * 將地址字串轉換為座標（正向地理編碼）
     *
     * @param context Android Context
     * @param addressString 地址字串
     * @return GPS 座標，失敗時返回 null
     */
    suspend fun getLocationFromAddress(
        context: Context,
        addressString: String
    ): LatLng? = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(context, java.util.Locale.TAIWAN)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                return@withContext getLocationAsync(geocoder, addressString)
            } else {
                return@withContext getLocationSync(geocoder, addressString)
            }

        } catch (e: IOException) {
            Log.e(TAG, "正向地理編碼失敗 (IOException)", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "正向地理編碼失敗", e)
            null
        }
    }

    /**
     * Android 13+ 異步方式獲取座標
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun getLocationAsync(
        geocoder: Geocoder,
        addressString: String
    ): LatLng? = suspendCancellableCoroutine { continuation ->
        geocoder.getFromLocationName(addressString, 1) { addresses ->
            if (addresses.isNotEmpty()) {
                val location = LatLng(addresses[0].latitude, addresses[0].longitude)
                Log.d(TAG, "正向地理編碼成功: $location")
                continuation.resume(location)
            } else {
                Log.w(TAG, "未找到座標")
                continuation.resume(null)
            }
        }
    }

    /**
     * Android 13 以下同步方式獲取座標
     */
    @Suppress("DEPRECATION")
    private fun getLocationSync(
        geocoder: Geocoder,
        addressString: String
    ): LatLng? {
        val addresses = geocoder.getFromLocationName(addressString, 1)

        return if (!addresses.isNullOrEmpty()) {
            val location = LatLng(addresses[0].latitude, addresses[0].longitude)
            Log.d(TAG, "正向地理編碼成功: $location")
            location
        } else {
            Log.w(TAG, "未找到座標")
            null
        }
    }
}
