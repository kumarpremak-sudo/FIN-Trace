package com.example.expensetracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.example.expensetracker.data.AppDatabase
import com.example.expensetracker.data.Transaction
import com.example.expensetracker.logic.SmsParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        
        for (sms in messages) {
            val sender = sms.displayOriginatingAddress ?: continue
            val body = sms.displayMessageBody ?: continue

            if (isBankSender(sender)) {
                processBankSms(context, body, sms.timestampMillis)
            }
        }
    }

    private fun isBankSender(sender: String): Boolean {
        return sender.contains("-") || sender.any { it.isLetter() }
    }

    private fun processBankSms(context: Context, smsBody: String, timestamp: Long) {
        val parsedData = SmsParser.parseBankSms(smsBody)

        if (parsedData.isValidTransaction) {
            val pendingResult = goAsync() 

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val database = AppDatabase.getInstance(context)
                    val dao = database.transactionDao()

                    val rawMerchantName = parsedData.merchant
                    val savedRule = dao.getRuleForMerchant(rawMerchantName.lowercase())
                    
                    val finalMerchantName = savedRule?.displayName ?: rawMerchantName
                    val finalCategory = savedRule?.category ?: "Uncategorized"

                    val transaction = Transaction(
                        rawSms = smsBody,
                        amount = parsedData.amount,
                        type = parsedData.type,
                        merchant = finalMerchantName,
                        rawMerchant = rawMerchantName, // Preserve raw name for future rules
                        timestamp = timestamp,
                        category = finalCategory
                    )

                    dao.insertTransaction(transaction)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
