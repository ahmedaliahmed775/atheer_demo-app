package com.atheer.demo.data.network

import com.atheer.demo.data.local.TokenManager
import okhttp3.Interceptor
import okhttp3.Response

/**
 * AuthInterceptor — يضيف JWT token لكل طلب شبكة
 */
class AuthInterceptor(private val tokenManager: TokenManager) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val token = tokenManager.getAccessToken()

        val request = if (!token.isNullOrEmpty()) {
            original.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            original
        }

        return chain.proceed(request)
    }
}
