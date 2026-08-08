package com.example.expensetracker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    indices = [Index(value = ["amount", "timestamp", "merchant"], unique = true)]
)
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val rawSms: String,
    val amount: Double,
    val type: String, // "DEBIT", "CREDIT", "REFUND", "TRANSFER", "INVESTMENT"
    val merchant: String, // Preferred name or parsed name
    val rawMerchant: String, // Original parsed name from SMS
    val currency: String = "INR", // Support for multiple currencies
    val timestamp: Long,
    val category: String = "Uncategorized"
)

data class CategorySum(
    val category: String,
    val totalAmount: Double
)
