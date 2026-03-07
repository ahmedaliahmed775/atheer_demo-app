package com.atheer.demo.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.atheer.demo.R
import com.atheer.demo.data.local.TokenManager
import com.atheer.demo.data.model.LoginResponse
import com.atheer.demo.data.model.LoginRequest
import com.atheer.demo.data.network.NetworkConstants
import com.atheer.demo.databinding.ActivityLoginBinding
import com.atheer.demo.ui.customer.CustomerMainActivity
import com.atheer.demo.ui.merchant.MerchantMainActivity
import com.atheer.sdk.AtheerSdk
import com.google.gson.Gson
import kotlinx.coroutines.launch

/**
 * LoginActivity — شاشة تسجيل الدخول
 *
 * تستخدم AtheerSdk للاتصال بـ /auth/login عبر الشبكة الخلوية
 * وتوجّه المستخدم حسب الدور:
 *   - customer → CustomerMainActivity
 *   - merchant → MerchantMainActivity
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tokenManager = TokenManager(this)

        // إذا كان المستخدم مسجل دخوله بالفعل
        if (tokenManager.isLoggedIn()) {
            navigateByRole(tokenManager.getUserRole() ?: "customer")
            return
        }

        binding.btnLogin.setOnClickListener { attemptLogin() }
    }

    private fun attemptLogin() {
        val phone = binding.etMerchantId.text?.toString()?.trim() ?: ""
        val password = binding.etPassword.text?.toString()?.trim() ?: ""

        // إخفاء رسالة الخطأ السابقة
        binding.tvError.visibility = View.GONE
        binding.tilMerchantId.error = null
        binding.tilPassword.error = null

        // التحقق من عدم الفراغ
        if (phone.isEmpty() || password.isEmpty()) {
            binding.tvError.text = getString(R.string.login_error_empty)
            binding.tvError.visibility = View.VISIBLE
            return
        }

        // تعطيل زر الدخول أثناء الطلب
        binding.btnLogin.isEnabled = false
        binding.tvError.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val networkRouter = AtheerSdk.getInstance().getNetworkRouter()
                val loginJson = gson.toJson(LoginRequest(phone, password))
                val responseJson = networkRouter.executeViaCellular(
                    "${NetworkConstants.BASE_URL}auth/login",
                    loginJson,
                    ""
                )

                val loginResponse = responseJson?.let {
                    gson.fromJson(it, LoginResponse::class.java)
                }

                if (loginResponse?.accessToken != null) {
                    // حفظ التوكن والدور
                    tokenManager.saveAccessToken(loginResponse.accessToken)
                    tokenManager.saveUserRole(loginResponse.role)
                    loginResponse.user?.let { user ->
                        user.name?.let { tokenManager.saveUserName(it) }
                        user.phone?.let { tokenManager.saveUserPhone(it) }
                    }

                    navigateByRole(loginResponse.role)
                } else {
                    binding.tvError.text = getString(R.string.login_error_invalid)
                    binding.tvError.visibility = View.VISIBLE
                    binding.btnLogin.isEnabled = true
                }
            } catch (e: Exception) {
                // التعامل مع أخطاء الشبكة — الوضع غير المتصل
                binding.tvError.text = getString(R.string.error_network)
                binding.tvError.visibility = View.VISIBLE
                binding.btnLogin.isEnabled = true
            }
        }
    }

    private fun navigateByRole(role: String) {
        val intent = when (role) {
            "merchant" -> Intent(this, MerchantMainActivity::class.java)
            else -> Intent(this, CustomerMainActivity::class.java)
        }
        startActivity(intent)
        finish()
    }

    companion object {
        private val gson = Gson()
    }
}

