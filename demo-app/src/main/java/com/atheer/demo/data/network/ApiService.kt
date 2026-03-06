package com.atheer.demo.data.network

import com.atheer.demo.data.model.*
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * ApiService — واجهة Retrofit لنقاط نهاية atheer_server
 */
interface ApiService {

    // ── المصادقة ──
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("auth/signup")
    suspend fun signup(@Body request: SignupRequest): Response<LoginResponse>

    // ── المحفظة ──
    @GET("wallet/balance")
    suspend fun getBalance(): Response<BalanceResponse>

    @GET("wallet/history")
    suspend fun getHistory(): Response<HistoryResponse>

    // ── التاجر ──
    @POST("merchant/charge")
    suspend fun charge(@Body request: ChargeRequest): Response<ChargeResponse>
}
