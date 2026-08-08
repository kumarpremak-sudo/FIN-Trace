package com.example.expensetracker.logic

import java.util.Locale

data class ParsedSms(
    val amount: Double,
    val type: String,
    val merchant: String,
    val currency: String,
    val isValidTransaction: Boolean
)

object SmsParser {
    private val INVALID_KEYWORDS = listOf(
        "due date", "bill due", "minimum balance", "otp", 
        "do not share", "apply for", "pre-approved", "reward points",
        "limit reached", "transaction failed", "declined", "failed",
        "insufficient funds", "unsuccessful", "cancelled", "timed out",
        "not processed", "reversed due to"
    )

    private val TRANSFER_KEYWORDS = listOf(
        "own a/c", "self transfer", "linked account", "wallet topup", "to self", "credit card payment"
    )

    private val INVESTMENT_KEYWORDS = listOf(
        "mutual fund", "sip", "investment", "broker", "zerodha", "groww", "upstox",
        "equity", "securities", "demat", "portfolio", "asset management", "amc"
    )

    fun parseBankSms(smsBody: String): ParsedSms {
        val lowerSms = smsBody.lowercase()

        // 1. Basic validation
        if (INVALID_KEYWORDS.any { lowerSms.contains(it) }) {
            return ParsedSms(0.0, "UNKNOWN", "Invalid Message", "INR", false)
        }

        // 2. Extract Amount and Currency
        // More robust regex for international formats: symbol/code before or after amount
        // Groups: 1=PrefixCurrency, 2=Amount, 3=SuffixCurrency
        val combinedRegex = Regex("(?i)(rs\\.?|inr|usd|\\$|eur|€|gbp|£|aed|sar)?\\s*([\\d,]+\\.?\\d*)\\s*(inr|usd|\\$|eur|€|gbp|£|aed|sar)?")
        val amountMatch = combinedRegex.find(smsBody)
        
        val prefixCurr = amountMatch?.groupValues?.get(1)?.uppercase() ?: ""
        val suffixCurr = amountMatch?.groupValues?.get(3)?.uppercase() ?: ""
        val amountStr = amountMatch?.groupValues?.get(2)?.replace(",", "") ?: ""
        val amount = amountStr.toDoubleOrNull() ?: 0.0

        val currencyRaw = when {
            prefixCurr.isNotEmpty() -> prefixCurr
            suffixCurr.isNotEmpty() -> suffixCurr
            else -> "INR"
        }

        val currency = when {
            currencyRaw.startsWith("RS") || currencyRaw == "INR" -> "INR"
            currencyRaw == "$" || currencyRaw == "USD" -> "USD"
            currencyRaw == "€" || currencyRaw == "EUR" -> "EUR"
            currencyRaw == "£" || currencyRaw == "GBP" -> "GBP"
            else -> currencyRaw
        }

        if (amount <= 0.0) return ParsedSms(0.0, "UNKNOWN", "Invalid Amount", currency, false)

        // 3. Determine Transaction Type
        val type = when {
            INVESTMENT_KEYWORDS.any { lowerSms.contains(it) } -> "INVESTMENT"
            TRANSFER_KEYWORDS.any { lowerSms.contains(it) } -> "TRANSFER"
            lowerSms.contains("refund") || lowerSms.contains("reversed") || lowerSms.contains("cashback") -> "REFUND"
            lowerSms.contains("debited") || lowerSms.contains("spent") || lowerSms.contains("paid") -> "DEBIT"
            lowerSms.contains("credited") || lowerSms.contains("received") || lowerSms.contains("payment of") -> "CREDIT"
            else -> "UNKNOWN"
        }

        // 4. Extract Merchant
        val merchantMarkers = listOf("at ", "to ", "info-", "info:", "by ", "in ", "from ", "towards ")
        val potentialMatches = mutableListOf<String>()
        for (marker in merchantMarkers) {
            var searchIndex = 0
            while (true) {
                val index = lowerSms.indexOf(marker, searchIndex)
                if (index == -1) break
                val start = index + marker.length
                val potential = smsBody.substring(start)
                val terminators = listOf(" on ", " via ", " ref ", " val ", " on\\b", " via\\b", " ref\\b", " val\\b", "\\.", "your available", "ending with", "not you")
                var earliestTerm = potential.length
                for (term in terminators) {
                    val match = Regex("(?i)$term").find(potential)
                    if (match != null && match.range.start < earliestTerm) {
                        earliestTerm = match.range.start
                    }
                }
                val candidate = potential.substring(0, earliestTerm).trim()
                if (candidate.isNotEmpty()) potentialMatches.add(candidate)
                searchIndex = index + 1 
            }
        }
        
        val rawMerchant = potentialMatches.find { candidate ->
            val lowerCandidate = candidate.lowercase()
            val isSusAccountNumber = lowerCandidate.contains(Regex("\\d{4,}")) || lowerCandidate.startsWith("a/c") || lowerCandidate.startsWith("acct")
            val isFooterNoise = lowerCandidate.contains("block") || lowerCandidate.contains("reissue") || lowerCandidate.contains("call") || lowerCandidate.contains("not you")
            !isSusAccountNumber && !isFooterNoise && !lowerCandidate.startsWith("inr") && !lowerCandidate.startsWith("rs") && !lowerCandidate.contains(Regex("^\\d+$"))
        } ?: potentialMatches.firstOrNull { !it.contains(Regex("\\d{4,}")) } ?: "Transaction Details Needed"
        
        val merchant = cleanMerchantName(rawMerchant)

        return ParsedSms(
            amount = amount,
            type = type,
            merchant = merchant,
            currency = currency,
            isValidTransaction = type != "UNKNOWN"
        )
    }

    fun cleanMerchantName(rawName: String?): String {
        if (rawName.isNullOrBlank()) return "Transaction Details Needed"
        var clean = rawName.trim()
        clean = clean.replace(Regex("@[a-z]+"), "")
        val lowerClean = clean.lowercase()
        if (lowerClean.startsWith("vpa-")) clean = clean.substring(4)
        else if (lowerClean.startsWith("info-")) clean = clean.substring(5)
        if (!clean.matches(Regex("^[-/]*\\d+$"))) {
            clean = clean.replace(Regex("[-/]*\\d+$"), "")
        }
        clean = clean.trim { !it.isLetterOrDigit() }
        return clean.trim().split(Regex("\\s+")).joinToString(" ") { word ->
            if (word.isEmpty()) return@joinToString ""
            word.lowercase().replaceFirstChar { it.uppercase() }
        }
    }
}
