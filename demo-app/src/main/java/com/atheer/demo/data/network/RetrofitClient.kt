package com.atheer.demo.data.network

import com.atheer.demo.data.local.TokenManager
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * RetrofitClient — مصنع Retrofit مع Auth Interceptor
 */
object RetrofitClient {

    private const val BASE_URL = "https://api.atheer.com/"
    private const val TIMEOUT_SECONDS = 30L

    @Volatile
    private var apiService: ApiService? = null

    fun getApiService(tokenManager: TokenManager): ApiService {
        return apiService ?: synchronized(this) {
            apiService ?: createApiService(tokenManager).also { apiService = it }
        }
    }

    private fun createApiService(tokenManager: TokenManager): ApiService {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenManager))
            .addInterceptor(loggingInterceptor)
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    /**
     * إعادة تهيئة العميل (مثلاً بعد تسجيل الخروج)
     */
    fun reset() {
        apiService = null
    }
}
