package com.atheer.demo.data.model

import com.google.gson.annotations.SerializedName

/**
 * نماذج البيانات لطلبات واستجابات API
 */

// ── طلب تسجيل الدخول ──
data class LoginRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String
)

// ── استجابة تسجيل الدخول ──
data class LoginResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("role") val role: String,
    @SerializedName("user") val user: UserInfo?
)

data class UserInfo(
    @SerializedName("id") val id: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("email") val email: String?
)

// ── طلب التسجيل ──
data class SignupRequest(
    @SerializedName("name") val name: String,
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String,
    @SerializedName("role") val role: String
)

// ── استجابة الرصيد ──
data class BalanceResponse(
    @SerializedName("balance") val balance: Double,
    @SerializedName("currency") val currency: String?
)

// ── استجابة سجل المعاملات ──
data class TransactionItem(
    @SerializedName("id") val id: String?,
    @SerializedName("transaction_id") val transactionId: String?,
    @SerializedName("amount") val amount: Double,
    @SerializedName("currency") val currency: String?,
    @SerializedName("merchant_id") val merchantId: String?,
    @SerializedName("status") val status: String?,
    @SerializedName("type") val type: String?,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("synced") val synced: Boolean = true
)

data class HistoryResponse(
    @SerializedName("transactions") val transactions: List<TransactionItem>?
)

// ── طلب الدفع من التاجر ──
data class ChargeRequest(
    @SerializedName("header") val header: ChargeHeader,
    @SerializedName("body") val body: ChargeBody
)

data class ChargeHeader(
    @SerializedName("merchant_id") val merchantId: String,
    @SerializedName("terminal_id") val terminalId: String?,
    @SerializedName("timestamp") val timestamp: Long
)

data class ChargeBody(
    @SerializedName("amount") val amount: Double,
    @SerializedName("currency") val currency: String,
    @SerializedName("token") val token: String,
    @SerializedName("transaction_id") val transactionId: String?
)

// ── استجابة الدفع ──
data class ChargeResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("transaction_id") val transactionId: String?,
    @SerializedName("message") val message: String?
)
