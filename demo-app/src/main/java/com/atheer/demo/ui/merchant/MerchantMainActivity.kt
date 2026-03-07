package com.atheer.demo.ui.merchant

import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.atheer.demo.R
import com.atheer.demo.data.local.TokenManager
import com.atheer.demo.data.model.ChargeBody
import com.atheer.demo.data.model.ChargeHeader
import com.atheer.demo.data.model.ChargeRequest
import com.atheer.demo.data.model.ChargeResponse
import com.atheer.demo.data.model.HistoryResponse
import com.atheer.demo.data.network.NetworkConstants
import com.atheer.demo.databinding.ActivityMerchantMainBinding
import com.atheer.demo.ui.login.LoginActivity
import com.atheer.sdk.AtheerSdk
import com.atheer.sdk.nfc.AtheerNfcReader
import com.google.gson.Gson
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * MerchantMainActivity — الشاشة الرئيسية للتاجر (SoftPOS)
 *
 * تعرض:
 * - واجهة استقبال المدفوعات مع لوحة رقمية
 * - قارئ NFC لاستقبال التوكن
 * - سجل المعاملات مع حالة المزامنة
 * - BottomNavigationView (الرئيسية، السجل، الإعدادات)
 */
class MerchantMainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMerchantMainBinding
    private lateinit var tokenManager: TokenManager
    private var nfcAdapter: NfcAdapter? = null
    private var nfcReader: AtheerNfcReader? = null
    private var enteredAmount: StringBuilder = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMerchantMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tokenManager = TokenManager(this)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        setupBottomNavigation()
        setupNumericKeypad()
        setupReceivePayment()
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

        // تسجيل الخروج
        binding.btnLogout.setOnClickListener {
            tokenManager.clearAll()
            startActivity(Intent(this, LoginActivity::class.java))
            finishAffinity()
        }

        // زر المزامنة
        binding.btnSyncNow.setOnClickListener {
            syncTransactions()
        }
    }

    private fun setupNumericKeypad() {
        val buttons = listOf(
            binding.btn0, binding.btn1, binding.btn2, binding.btn3,
            binding.btn4, binding.btn5, binding.btn6, binding.btn7,
            binding.btn8, binding.btn9, binding.btnDot
        )

        buttons.forEach { btn ->
            btn.setOnClickListener {
                val digit = (it as TextView).text.toString()
                if (digit == "." && enteredAmount.contains(".")) return@setOnClickListener
                enteredAmount.append(digit)
                updateAmountDisplay()
            }
        }

        binding.btnBackspace.setOnClickListener {
            if (enteredAmount.isNotEmpty()) {
                enteredAmount.deleteCharAt(enteredAmount.length - 1)
                updateAmountDisplay()
            }
        }

        binding.btnClear.setOnClickListener {
            enteredAmount.clear()
            updateAmountDisplay()
        }
    }

    private fun updateAmountDisplay() {
        val displayText = if (enteredAmount.isEmpty()) "0.00" else enteredAmount.toString()
        binding.tvAmountDisplay.text = displayText
    }

    private fun setupReceivePayment() {
        binding.btnReceivePayment.setOnClickListener {
            val amount = enteredAmount.toString().toDoubleOrNull()
            if (amount == null || amount <= 0) {
                binding.tvAmountDisplay.text = getString(R.string.error_invalid_amount)
                return@setOnClickListener
            }
            startNfcReading(amount)
        }
    }

    private fun startNfcReading(amount: Double) {
        val adapter = nfcAdapter
        if (adapter == null || !adapter.isEnabled) {
            binding.tvMerchantStatus.text = getString(R.string.pos_nfc_unavailable)
            return
        }

        binding.tvMerchantStatus.text = getString(R.string.pos_tap_card)
        binding.btnReceivePayment.isEnabled = false

        val merchantId = tokenManager.getUserPhone() ?: "MERCHANT"

        nfcReader = AtheerNfcReader(
            merchantId = merchantId,
            transactionCallback = { transaction ->
                // اهتزاز قوي عند استلام التوكن
                triggerHapticFeedback()

                runOnUiThread {
                    stopNfcReading()
                    val token = transaction.tokenizedCard
                    if (token != null) {
                        processCharge(amount, token, merchantId)
                    } else {
                        android.util.Log.e("MerchantMain", "NFC token is null, cannot process charge")
                        binding.tvMerchantStatus.text = getString(R.string.charge_failed)
                    }
                }
            },
            errorCallback = { error ->
                runOnUiThread {
                    stopNfcReading()
                    binding.tvMerchantStatus.text = "خطأ: ${error.message}"
                }
            }
        )

        adapter.enableReaderMode(
            this,
            nfcReader,
            NfcAdapter.FLAG_READER_NFC_A or
                    NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null
        )
    }

    private fun stopNfcReading() {
        nfcAdapter?.disableReaderMode(this)
        binding.btnReceivePayment.isEnabled = true
    }

    @Suppress("DEPRECATION")
    private fun triggerHapticFeedback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            val vibrator = vibratorManager.defaultVibrator
            vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    private fun processCharge(amount: Double, token: String, merchantId: String) {
        // عرض حوار المعالجة
        val progressDialog = AlertDialog.Builder(this)
            .setMessage(getString(R.string.processing))
            .setCancelable(false)
            .create()
        progressDialog.show()

        val chargeRequest = ChargeRequest(
            header = ChargeHeader(
                merchantId = merchantId,
                terminalId = "TERMINAL_${Build.MODEL}",
                timestamp = System.currentTimeMillis()
            ),
            body = ChargeBody(
                amount = amount,
                currency = "SAR",
                token = token,
                transactionId = "TXN_${UUID.randomUUID().toString().take(12)}"
            )
        )

        lifecycleScope.launch {
            try {
                val accessToken = tokenManager.getAccessToken() ?: ""
                val networkRouter = AtheerSdk.getInstance().getNetworkRouter()
                val chargeJson = gson.toJson(chargeRequest)
                val responseJson = networkRouter.executeViaCellular(
                    "${NetworkConstants.BASE_URL}merchant/charge",
                    chargeJson,
                    accessToken
                )
                val chargeResponse = responseJson?.let {
                    gson.fromJson(it, ChargeResponse::class.java)
                }

                progressDialog.dismiss()

                if (chargeResponse?.success == true) {
                    binding.tvMerchantStatus.text = getString(R.string.charge_success)
                    enteredAmount.clear()
                    updateAmountDisplay()
                } else {
                    binding.tvMerchantStatus.text = getString(R.string.charge_failed)
                }
            } catch (e: Exception) {
                progressDialog.dismiss()
                binding.tvMerchantStatus.text = getString(R.string.error_network)
            }
        }
    }

    private fun loadHistory() {
        lifecycleScope.launch {
            try {
                val token = tokenManager.getAccessToken() ?: ""
                val networkRouter = AtheerSdk.getInstance().getNetworkRouter()
                val responseJson = networkRouter.executeViaCellular(
                    "${NetworkConstants.BASE_URL}wallet/history",
                    "",
                    token
                )
                val historyResponse = responseJson?.let {
                    gson.fromJson(it, HistoryResponse::class.java)
                }
                if (historyResponse?.transactions != null) {
                    displayTransactions(historyResponse.transactions)
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

        if (transactions.isEmpty()) {
            binding.tvHistoryEmpty.visibility = View.VISIBLE
            container.visibility = View.GONE
            return
        }

        binding.tvHistoryEmpty.visibility = View.GONE
        container.visibility = View.VISIBLE

        for (txn in transactions) {
            val itemView = LayoutInflater.from(this).inflate(R.layout.item_transaction, container, false)
            itemView.findViewById<TextView>(R.id.tvTxnAmount).text =
                String.format("%.2f %s", txn.amount, txn.currency ?: "SAR")
            itemView.findViewById<TextView>(R.id.tvTxnDate).text = txn.createdAt ?: ""
            val statusText = if (txn.synced) "☁️ ${getString(R.string.result_status_synced)}"
                else "📱 ${getString(R.string.result_status_offline)}"
            itemView.findViewById<TextView>(R.id.tvTxnStatus).text = statusText
            val statusIcon = itemView.findViewById<ImageView>(R.id.ivTxnIcon)
            statusIcon.setColorFilter(
                ContextCompat.getColor(this, if (txn.synced) R.color.success_color else R.color.text_secondary)
            )
            container.addView(itemView)
        }
    }

    private fun syncTransactions() {
        binding.btnSyncNow.isEnabled = false
        binding.btnSyncNow.text = getString(R.string.syncing)

        lifecycleScope.launch {
            try {
                // مزامنة المعاملات المحلية عبر SDK
                val sdk = com.atheer.sdk.AtheerSdk.getInstance()
                // SDK sync logic would go here
                loadHistory()
            } catch (e: Exception) {
                android.util.Log.e("MerchantMain", "Sync failed: ${e.message}", e)
            } finally {
                binding.btnSyncNow.isEnabled = true
                binding.btnSyncNow.text = getString(R.string.btn_sync_now)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        stopNfcReading()
    }

    companion object {
        private val gson = Gson()
    }
}
