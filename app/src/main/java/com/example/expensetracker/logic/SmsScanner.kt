package com.example.expensetracker.logic

import android.content.Context
import android.net.Uri
import android.provider.Telephony
import com.example.expensetracker.data.AppDatabase
import com.example.expensetracker.data.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

object SmsScanner {

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress

    suspend fun scanExistingMessages(context: Context) = withContext(Dispatchers.IO) {
        if (_isScanning.value) return@withContext
        
        _isScanning.value = true
        _progress.value = 0f
        
        try {
            val database = AppDatabase.getInstance(context)
            val dao = database.transactionDao()
            
            val uri = Uri.parse("content://sms/inbox")
            val projection = arrayOf(
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE
            )

            context.contentResolver.query(uri, projection, null, null, "date DESC")?.use { cursor ->
                val addressIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
                val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)

                val total = cursor.count
                var processed = 0

                while (cursor.moveToNext()) {
                    val address = cursor.getString(addressIdx)
                    val body = cursor.getString(bodyIdx)
                    val date = cursor.getLong(dateIdx)

                    if (isBankSender(address)) {
                        val parsed = SmsParser.parseBankSms(body)
                        if (parsed.isValidTransaction) {
                            val merchantName = parsed.merchant ?: "Unknown"
                            val learnedCategory = dao.getCategoryForMerchant(merchantName.lowercase())
                            
                            val transaction = Transaction(
                                rawSms = body,
                                amount = parsed.amount,
                                type = parsed.type,
                                merchant = merchantName,
                                timestamp = date,
                                category = learnedCategory ?: "Uncategorized"
                            )
                            dao.insertTransaction(transaction)
                        }
                    }
                    processed++
                    if (processed % 10 == 0) {
                        _progress.value = processed.toFloat() / total.toFloat()
                    }
                }
            }
        } finally {
            _isScanning.value = false
            _progress.value = 1f
        }
    }

    private fun isBankSender(sender: String?): Boolean {
        if (sender == null) return false
        return sender.contains("-") || sender.any { it.isLetter() }
    }
}
