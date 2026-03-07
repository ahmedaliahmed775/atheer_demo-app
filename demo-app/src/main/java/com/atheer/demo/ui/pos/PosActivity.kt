package com.atheer.demo.ui.pos

import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.atheer.demo.R
import com.atheer.demo.data.local.TokenManager
import com.atheer.demo.databinding.ActivityPosBinding
import com.atheer.demo.ui.result.TransactionResultActivity
import com.atheer.sdk.AtheerSdk
import com.atheer.sdk.model.ChargeRequest
import com.atheer.sdk.nfc.AtheerNfcReader
import kotlinx.coroutines.launch

/**
 * PosActivity — مسار نقطة المبيعات (SoftPOS)
 *
 * يحول الهاتف إلى جهاز نقطة بيع:
 * - يقرأ بطاقات NFC وهواتف تعمل بوضع HCE
 * - يستقبل بيانات الدفع المشفرة عبر Atheer SDK
 * - يعرض نتيجة المعاملة في شاشة مستقلة
 */
class PosActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPosBinding
    private lateinit var tokenManager: TokenManager
    private var merchantId: String = "DEMO_MERCHANT"
    private var accessToken: String = ""
    private var amountInput: Double = 0.0
    private var nfcAdapter: NfcAdapter? = null
    private var nfcReader: AtheerNfcReader? = null
    private var isReading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tokenManager = TokenManager(this)
        merchantId = intent.getStringExtra(EXTRA_MERCHANT_ID) ?: "DEMO_MERCHANT"
        accessToken = intent.getStringExtra(EXTRA_ACCESS_TOKEN) ?: (tokenManager.getAccessToken() ?: "")
        amountInput = intent.getDoubleExtra(EXTRA_AMOUNT, 0.0)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        checkNfcAvailability()

        binding.btnBack.setOnClickListener { finish() }
        binding.btnStartReading.setOnClickListener { startNfcReading() }
        binding.btnStopReading.setOnClickListener { stopNfcReading() }
        binding.btnOpenNfcSettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
        }
    }

    /** التحقق من توفر NFC وتفعيله */
    private fun checkNfcAvailability() {
        when {
            nfcAdapter == null -> {
                // الجهاز لا يدعم NFC
                binding.layoutNfcUnavailable.visibility = View.VISIBLE
                binding.tvPosStatus.text = getString(R.string.pos_nfc_unavailable)
                binding.btnStartReading.isEnabled = false
            }
            !nfcAdapter!!.isEnabled -> {
                // NFC متاح لكن غير مفعّل
                binding.layoutNfcUnavailable.visibility = View.VISIBLE
                binding.tvPosStatus.text = getString(R.string.pos_enable_nfc)
                binding.btnOpenNfcSettings.visibility = View.VISIBLE
                binding.btnStartReading.isEnabled = false
            }
            else -> {
                binding.layoutNfcUnavailable.visibility = View.GONE
                binding.btnStartReading.isEnabled = true
            }
        }
    }

    /** بدء قراءة NFC */
    private fun startNfcReading() {
        if (isReading) return

        val adapter = nfcAdapter ?: return
        if (!adapter.isEnabled) {
            checkNfcAvailability()
            return
        }

        // إنشاء قارئ NFC باستخدام SDK
        nfcReader = AtheerNfcReader(
            merchantId = merchantId,
            transactionCallback = { transaction ->
                // تم استلام بيانات الدفع بنجاح — معالجة عبر SDK
                stopNfcReading()
                val capturedAtheerToken = transaction.tokenizedCard ?: transaction.transactionId ?: ""
                processChargeWithSdk(capturedAtheerToken, transaction)
            },
            errorCallback = { error ->
                runOnUiThread {
                    stopNfcReading()
                    binding.tvPosStatus.text = "خطأ: ${error.message}"
                    binding.progressReading.visibility = View.GONE
                }
            }
        )

        // تفعيل وضع القراءة
        adapter.enableReaderMode(
            this,
            nfcReader,
            NfcAdapter.FLAG_READER_NFC_A or
                    NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null
        )

        isReading = true
        updateReadingUi(true)
    }

    /** إيقاف قراءة NFC */
    private fun stopNfcReading() {
        if (!isReading) return
        nfcAdapter?.disableReaderMode(this)
        isReading = false
        runOnUiThread { updateReadingUi(false) }
    }

    private fun updateReadingUi(reading: Boolean) {
        if (reading) {
            binding.tvPosStatus.text = getString(R.string.pos_reading)
            binding.tvPosGuide.text = getString(R.string.pos_tap_card)
            binding.progressReading.visibility = View.VISIBLE
            binding.btnStartReading.isEnabled = false
            binding.btnStopReading.isEnabled = true

            // تأثير وميض لأيقونة NFC
            val blink = AlphaAnimation(0.3f, 1.0f).apply {
                duration = 700
                repeatMode = Animation.REVERSE
                repeatCount = Animation.INFINITE
            }
            binding.ivPosNfcIcon.startAnimation(blink)
            binding.ivPosNfcIcon.alpha = 1f
        } else {
            binding.tvPosStatus.text = getString(R.string.pos_waiting)
            binding.tvPosGuide.text = ""
            binding.progressReading.visibility = View.GONE
            binding.btnStartReading.isEnabled = nfcAdapter?.isEnabled == true
            binding.btnStopReading.isEnabled = false
            binding.ivPosNfcIcon.clearAnimation()
            binding.ivPosNfcIcon.alpha = 0.5f
        }
    }

    override fun onResume() {
        super.onResume()
        // إعادة التحقق من حالة NFC عند العودة من الإعدادات
        checkNfcAvailability()
    }

    override fun onPause() {
        super.onPause()
        stopNfcReading()
    }

    companion object {
        const val EXTRA_MERCHANT_ID = "extra_merchant_id"
        const val EXTRA_ACCESS_TOKEN = "extra_access_token"
        const val EXTRA_AMOUNT = "extra_amount"
    }

    /** معالجة الدفع عبر SDK */
    private fun processChargeWithSdk(capturedAtheerToken: String, transaction: com.atheer.sdk.nfc.AtheerNfcReader.TransactionData) {
        val chargeRequest = ChargeRequest(
            amount = if (amountInput > 0) amountInput else transaction.amount,
            currency = "YER",
            merchantId = "DEMO_MERCHANT",
            atheerToken = capturedAtheerToken
        )

        val token = accessToken
        lifecycleScope.launch {
            try {
                val result = AtheerSdk.getInstance().charge(chargeRequest, "Bearer $token")

                result.onSuccess { response ->
                    val intent = Intent(this@PosActivity, TransactionResultActivity::class.java).apply {
                        putExtra(TransactionResultActivity.EXTRA_TRANSACTION_ID, transaction.transactionId)
                        putExtra(TransactionResultActivity.EXTRA_AMOUNT, transaction.amount)
                        putExtra(TransactionResultActivity.EXTRA_CURRENCY, transaction.currency)
                        putExtra(TransactionResultActivity.EXTRA_MERCHANT_ID, transaction.merchantId)
                        putExtra(TransactionResultActivity.EXTRA_TIMESTAMP, transaction.timestamp)
                        putExtra(TransactionResultActivity.EXTRA_IS_SUCCESS, true)
                        putExtra(TransactionResultActivity.EXTRA_IS_SYNCED, true)
                    }
                    startActivity(intent)
                }.onFailure { error ->
                    runOnUiThread {
                        binding.tvPosStatus.text = "خطأ: ${error.message}"
                        binding.progressReading.visibility = View.GONE
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    binding.tvPosStatus.text = "خطأ: ${e.message}"
                    binding.progressReading.visibility = View.GONE
                }
            }
        }
    }
}
