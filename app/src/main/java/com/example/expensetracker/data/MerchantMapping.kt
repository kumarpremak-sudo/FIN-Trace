package com.example.expensetracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "merchant_mappings")
data class MerchantMapping(
    @PrimaryKey val merchant: String,
    val category: String
)
