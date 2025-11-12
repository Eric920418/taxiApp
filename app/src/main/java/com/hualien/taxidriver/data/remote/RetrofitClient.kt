package com.hualien.taxidriver.data.remote

import android.content.Context
import com.hualien.taxidriver.utils.Constants
import com.hualien.taxidriver.utils.DataStoreManager
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Retrofit客戶端單例
 */
object RetrofitClient {

    private var authInterceptor: AuthInterceptor? = null

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    // 延遲初始化，等待 authInterceptor 設置後才創建
    private var okHttpClient: OkHttpClient? = null

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(Constants.BASE_URL)
            .client(getOkHttpClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val apiService: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }

    val passengerApiService: PassengerApiService by lazy {
        retrofit.create(PassengerApiService::class.java)
    }

    /**
     * 初始化 RetrofitClient（在 Application 或 MainActivity 中調用）
     */
    fun init(context: Context) {
        val dataStoreManager = DataStoreManager(context.applicationContext)
        authInterceptor = AuthInterceptor(dataStoreManager)
    }

    private fun getOkHttpClient(): OkHttpClient {
        if (okHttpClient == null) {
            val builder = OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .connectTimeout(Constants.CONNECT_TIMEOUT, TimeUnit.SECONDS)
                .readTimeout(Constants.READ_TIMEOUT, TimeUnit.SECONDS)
                .writeTimeout(Constants.WRITE_TIMEOUT, TimeUnit.SECONDS)

            // 如果有 authInterceptor，添加它
            authInterceptor?.let {
                builder.addInterceptor(it)
            }

            okHttpClient = builder.build()
        }
        return okHttpClient!!
    }
}
