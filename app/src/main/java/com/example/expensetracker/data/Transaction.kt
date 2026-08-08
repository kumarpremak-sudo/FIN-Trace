package com.example.expensetracker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    indices = [Index(value = ["amount", "timestamp", "rawMerchant"], unique = true)] // BUG FIX: Use rawMerchant for unique constraint
)
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val rawSms: String,
    val amount: Double,
    val type: String, // "DEBIT", "CREDIT", "REFUND", "TRANSFER", "INVESTMENT"
    val merchant: String, // User-facing name (may change via rules)
    val rawMerchant: String, // The stable original parsed name
    val currency: String = "INR",
    val timestamp: Long,
    val category: String = "Uncategorized"
)

data class CategorySum(
    val category: String,
    val totalAmount: Double
)
