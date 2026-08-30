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
        if (messages.isNullOrEmpty()) return

        val sender = messages[0].displayOriginatingAddress ?: return
        val body = messages.joinToString("") { it.displayMessageBody ?: "" }

        if (isBankSender(sender)) {
            processBankSms(context, body, messages[0].timestampMillis)
        }
    }

    private fun isBankSender(sender: String): Boolean {
        val cleanSender = sender.uppercase()
        return cleanSender.contains("-") || 
               cleanSender.any { it.isLetter() } || 
               (cleanSender.length in 3..8 && cleanSender.all { it.isDigit() })
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
                    val savedRule = dao.getRuleForMerchant(rawMerchantName.trim().lowercase())
                    
                    val finalMerchantName = savedRule?.displayName ?: rawMerchantName
                    val finalCategory = savedRule?.category ?: "Uncategorized"

                    val transaction = Transaction(
                        rawSms = smsBody,
                        amount = parsedData.amount,
                        type = parsedData.type,
                        merchant = finalMerchantName,
                        rawMerchant = rawMerchantName,
                        currency = parsedData.currency,
                        timestamp = timestamp,
                        category = finalCategory,
                        referenceId = parsedData.referenceId // Pass Reference ID
                    )

                    dao.insertTransaction(transaction)
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
