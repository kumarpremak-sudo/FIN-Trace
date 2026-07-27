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
    val type: String, // "DEBIT", "CREDIT", "REFUND", "TRANSFER"
    val merchant: String?,
    val timestamp: Long,
    val category: String = "Uncategorized"
)

data class CategorySum(
    val category: String,
    val totalAmount: Double
)
