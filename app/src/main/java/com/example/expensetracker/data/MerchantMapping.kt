package com.example.expensetracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "merchant_mappings")
data class MerchantMapping(
    @PrimaryKey val rawMerchant: String, // The merchant extracted from SMS
    val displayName: String,             // User-assigned clean name
    val category: String                 // User-assigned category
)
