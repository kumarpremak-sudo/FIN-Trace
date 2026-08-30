package com.example.expensetracker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["amount", "timestamp", "rawMerchant"], unique = true),
        Index(value = ["referenceId"], unique = true) // Strong deduplication via bank ref ID
    ]
)
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val rawSms: String,
    val amount: Double,
    val type: String, // "DEBIT", "CREDIT", "REFUND", "TRANSFER", "INVESTMENT"
    val merchant: String,
    val rawMerchant: String,
    val currency: String = "INR",
    val timestamp: Long,
    val category: String = "Uncategorized",
    val referenceId: String? = null // Extracted Ref No / UPI ID
)

data class CategorySum(
    val category: String,
    val totalAmount: Double
)
