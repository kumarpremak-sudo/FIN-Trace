package com.example.expensetracker.logic

import java.util.Locale

data class ParsedSms(
    val amount: Double,
    val type: String,
    val merchant: String,
    val currency: String,
    val referenceId: String?,
    val isValidTransaction: Boolean
)

object SmsParser {
    private val INVALID_KEYWORDS = listOf(
        "due date", "bill due", "minimum balance", "otp", 
        "do not share", "apply for", "pre-approved", "reward points",
        "limit reached", "transaction failed", "declined", "failed",
        "insufficient funds", "unsuccessful", "cancelled", "timed out",
        "not processed", "reversed due to", "reminder", "info:",
        "will be debited", "scheduled", "mandate", "stmt", "statement",
        "request for", "requested", "to be debited"
    )

    private val TRANSFER_KEYWORDS = listOf(
        "own a/c", "self transfer", "linked account", "wallet topup", "to self", "credit card payment"
    )

    private val INVESTMENT_KEYWORDS = listOf(
        "mutual fund", "sip", "investment", "broker", "zerodha", "groww", "upstox",
        "equity", "securities", "demat", "portfolio", "asset management", "amc", "stock"
    )

    fun parseBankSms(smsBody: String): ParsedSms {
        val lowerSms = smsBody.lowercase()

        // 1. Basic validation: exclude informational or future-dated SMS
        if (INVALID_KEYWORDS.any { lowerSms.contains(it) }) {
            return ParsedSms(0.0, "UNKNOWN", "Invalid Message", "INR", null, false)
        }

        // 2. Extract Amount and Currency
        val curPattern = "(?:rs\\.?|inr|usd|\\$|eur|€|gbp|£|aed|sar|cad|aud|sgd)"
        val amountPattern = "([\\d,]+\\.?\\d*)"
        
        val match1 = Regex("(?i)($curPattern)\\s*$amountPattern").find(smsBody)
        val match2 = Regex("(?i)$amountPattern\\s*($curPattern)").find(smsBody)
        
        var currencyRaw = "INR"
        var amountStr = ""

        if (match1 != null) {
            currencyRaw = match1.groupValues[1].uppercase().replace(".", "")
            amountStr = match1.groupValues[2]
        } else if (match2 != null) {
            amountStr = match2.groupValues[1]
            currencyRaw = match2.groupValues[2].uppercase().replace(".", "")
        } else {
            val matchFallback = Regex(amountPattern).find(smsBody)
            if (matchFallback != null) {
                amountStr = matchFallback.groupValues[1]
            } else {
                return ParsedSms(0.0, "UNKNOWN", "No Amount Found", "INR", null, false)
            }
        }

        val currency = when {
            currencyRaw.startsWith("RS") || currencyRaw == "INR" -> "INR"
            currencyRaw == "$" || currencyRaw == "USD" -> "USD"
            currencyRaw == "€" || currencyRaw == "EUR" -> "EUR"
            currencyRaw == "£" || currencyRaw == "GBP" -> "GBP"
            else -> currencyRaw
        }

        val amount = amountStr.replace(",", "").toDoubleOrNull() ?: 0.0
        if (amount <= 0.0) return ParsedSms(0.0, "UNKNOWN", "Zero Amount", currency, null, false)

        // 3. Determine Transaction Type
        val type = when {
            INVESTMENT_KEYWORDS.any { lowerSms.contains(it) } -> "INVESTMENT"
            TRANSFER_KEYWORDS.any { lowerSms.contains(it) } -> "TRANSFER"
            lowerSms.contains("refund") || lowerSms.contains("reversed") || lowerSms.contains("cashback") -> "REFUND"
            lowerSms.contains("debited") || lowerSms.contains("spent") || lowerSms.contains("paid") || lowerSms.contains("purchase") -> "DEBIT"
            lowerSms.contains("credited") || lowerSms.contains("received") || lowerSms.contains("deposit") -> "CREDIT"
            else -> "UNKNOWN"
        }

        // 4. Extract Merchant
        val merchantMarkers = listOf("at ", "to ", "info-", "info:", "by ", "in ", "from ", "towards ", "using ", "for ", "vpa ", "upi ")
        val potentialMatches = mutableListOf<String>()
        
        for (marker in merchantMarkers) {
            var searchIndex = 0
            while (true) {
                val index = lowerSms.indexOf(marker, searchIndex)
                if (index == -1) break
                val start = index + marker.length
                val potential = smsBody.substring(start)
                val terminators = listOf(" on ", " via ", " ref ", " val ", " on\\b", " via\\b", " ref\\b", " val\\b", "\\.", "your available", "ending with", "not you", "bal", "balance", "\\d{2}-\\d{2}-\\d{2}")
                var earliestTerm = potential.length
                for (term in terminators) {
                    val match = Regex("(?i)$term").find(potential)
                    if (match != null && match.range.start < earliestTerm) earliestTerm = match.range.start
                }
                val candidate = potential.substring(0, earliestTerm).trim()
                if (candidate.isNotEmpty() && candidate.length > 2) potentialMatches.add(candidate)
                searchIndex = index + 1 
            }
        }
        
        val rawMerchant = potentialMatches.find { candidate ->
            val lc = candidate.lowercase()
            val isAccNum = lc.contains(Regex("\\d{4,}"))
            val isFooter = lc.contains("block") || lc.contains("call") || lc.contains("limit")
            val isBank = lc.contains("bank") || lc.contains("card")
            !isAccNum && !isFooter && !isBank && lc.any { it.isLetter() }
        } ?: potentialMatches.firstOrNull { cand -> cand.any { it.isLetter() } } ?: "Merchant Details Required"

        // 5. Extract Reference ID for Deduplication
        val refRegex = Regex("(?i)(?:ref|rrn|upi ref|txn|id)[:\\s]+([a-z0-9]{8,})")
        val refMatch = refRegex.find(smsBody)
        val referenceId = refMatch?.groupValues?.get(1)

        return ParsedSms(
            amount = amount,
            type = type,
            merchant = cleanMerchantName(rawMerchant),
            currency = currency,
            referenceId = referenceId,
            isValidTransaction = type != "UNKNOWN"
        )
    }

    fun cleanMerchantName(rawName: String?): String {
        if (rawName.isNullOrBlank()) return "Merchant Details Required"
        var clean = rawName.trim().replace(Regex("(?i)inr|rs\\.|usd|\\$|eur|€"), "").trim()
        clean = clean.replace(Regex("@[a-z]+"), "")
        if (!clean.matches(Regex("^[-/]*\\d+$"))) {
            clean = clean.replace(Regex("[-/]*\\d+$"), "")
        }
        clean = clean.trim { !it.isLetterOrDigit() }
        return clean.split(Regex("\\s+")).joinToString(" ") { word ->
            if (word.isEmpty()) "" else word.lowercase().replaceFirstChar { it.uppercase() }
        }.trim()
    }
}
