package com.atheer.demo.ui.customer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.atheer.demo.R
import com.atheer.demo.data.local.TokenManager
import com.atheer.demo.data.network.RetrofitClient
import com.atheer.demo.databinding.ActivityCustomerMainBinding
import com.atheer.demo.ui.login.LoginActivity
import com.atheer.demo.ui.payment.PaymentSuccessActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch

/**
 * CustomerMainActivity — الشاشة الرئيسية للعميل (الدافع)
 *
 * تعرض:
 * - بطاقة الرصيد مع إمكانية إخفاء/إظهار
 * - شبكة الخدمات
 * - زر "Atheer Pay" مع BiometricPrompt
 * - BottomNavigationView (الرئيسية، السجل، الإعدادات)
 */
class CustomerMainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCustomerMainBinding
    private lateinit var tokenManager: TokenManager
    private var isBalanceVisible = true
    private var currentBalance: Double = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomerMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tokenManager = TokenManager(this)

        setupBottomNavigation()
        setupBalanceCard()
        setupAtheerPayButton()
        loadBalance()
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    binding.layoutHome.visibility = View.VISIBLE
                    binding.layoutHistory.visibility = View.GONE
                    binding.layoutSettings.visibility = View.GONE
                    true
                }
                R.id.nav_history -> {
                    binding.layoutHome.visibility = View.GONE
                    binding.layoutHistory.visibility = View.VISIBLE
                    binding.layoutSettings.visibility = View.GONE
                    loadHistory()
                    true
                }
                R.id.nav_settings -> {
                    binding.layoutHome.visibility = View.GONE
                    binding.layoutHistory.visibility = View.GONE
                    binding.layoutSettings.visibility = View.VISIBLE
                    true
                }
                else -> false
            }
        }

        // الإعدادات — تسجيل الخروج
        binding.btnLogout.setOnClickListener {
            tokenManager.clearAll()
            RetrofitClient.reset()
            startActivity(Intent(this, LoginActivity::class.java))
            finishAffinity()
        }
    }

    private fun setupBalanceCard() {
        binding.ivToggleBalance.setOnClickListener {
            isBalanceVisible = !isBalanceVisible
            updateBalanceDisplay()
        }
    }

    private fun updateBalanceDisplay() {
        if (isBalanceVisible) {
            binding.tvBalance.text = String.format("%.2f", currentBalance)
            binding.tvCurrencyLabel.text = getString(R.string.sar_currency)
            binding.ivToggleBalance.setImageResource(android.R.drawable.ic_menu_view)
        } else {
            binding.tvBalance.text = "••••••"
            binding.tvCurrencyLabel.text = ""
            binding.ivToggleBalance.setImageResource(android.R.drawable.ic_secure)
        }
    }

    private fun loadBalance() {
        lifecycleScope.launch {
            try {
                val apiService = RetrofitClient.getApiService(tokenManager)
                val response = apiService.getBalance()
                if (response.isSuccessful && response.body() != null) {
                    currentBalance = response.body()!!.balance
                    updateBalanceDisplay()
                }
            } catch (e: Exception) {
                // وضع عدم الاتصال — عرض آخر رصيد محفوظ
                binding.tvBalance.text = getString(R.string.error_offline)
            }
        }
    }

    private fun loadHistory() {
        lifecycleScope.launch {
            try {
                val apiService = RetrofitClient.getApiService(tokenManager)
                val response = apiService.getHistory()
                if (response.isSuccessful && response.body()?.transactions != null) {
                    val transactions = response.body()!!.transactions!!
                    if (transactions.isEmpty()) {
                        binding.tvHistoryEmpty.visibility = View.VISIBLE
                        binding.rvHistory.visibility = View.GONE
                    } else {
                        binding.tvHistoryEmpty.visibility = View.GONE
                        binding.rvHistory.visibility = View.VISIBLE
                        // عرض المعاملات
                        displayTransactions(transactions)
                    }
                }
            } catch (e: Exception) {
                binding.tvHistoryEmpty.text = getString(R.string.error_offline)
                binding.tvHistoryEmpty.visibility = View.VISIBLE
            }
        }
    }

    private fun displayTransactions(transactions: List<com.atheer.demo.data.model.TransactionItem>) {
        val container = binding.rvHistory
        container.removeAllViews()
        for (txn in transactions) {
            val itemView = LayoutInflater.from(this).inflate(R.layout.item_transaction, container, false)
            itemView.findViewById<TextView>(R.id.tvTxnAmount).text =
                String.format("%.2f %s", txn.amount, txn.currency ?: "SAR")
            itemView.findViewById<TextView>(R.id.tvTxnDate).text = txn.createdAt ?: ""
            itemView.findViewById<TextView>(R.id.tvTxnStatus).text =
                if (txn.synced) getString(R.string.result_status_synced) else getString(R.string.result_status_offline)
            val statusIcon = itemView.findViewById<ImageView>(R.id.ivTxnIcon)
            statusIcon.setColorFilter(
                ContextCompat.getColor(this, if (txn.synced) R.color.success_color else R.color.text_secondary)
            )
            container.addView(itemView)
        }
    }

    private fun setupAtheerPayButton() {
        binding.btnAtheerPay.setOnClickListener {
            authenticateWithBiometric()
        }
    }

    private fun authenticateWithBiometric() {
        val biometricManager = BiometricManager.from(this)
        val canAuth = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)

        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            // لا يدعم القياسات الحيوية — المتابعة مباشرة
            showNfcBottomSheet()
            return
        }

        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    showNfcBottomSheet()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    // المستخدم ألغى المصادقة
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    // فشل المصادقة
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.biometric_title))
            .setSubtitle(getString(R.string.biometric_subtitle))
            .setNegativeButtonText(getString(R.string.biometric_cancel))
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun showNfcBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_nfc, null)
        dialog.setContentView(view)

        // تحضير خدمة HCE عبر SDK
        try {
            val sdk = com.atheer.sdk.AtheerSdk.getInstance()
            // SDK HCE service preparation
        } catch (e: Exception) {
            android.util.Log.w("CustomerMain", "SDK not initialized for HCE: ${e.message}")
        }

        dialog.show()
    }
}
