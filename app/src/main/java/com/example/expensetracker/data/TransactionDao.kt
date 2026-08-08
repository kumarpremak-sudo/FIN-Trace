package com.example.expensetracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction as RoomTransaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransaction(transaction: Transaction)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransactions(transactions: List<Transaction>)

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC LIMIT 150")
    fun getRecentTransactions(): Flow<List<Transaction>>

    @Query("""
        SELECT 
            currency,
            SUM(CASE WHEN type = 'DEBIT' THEN amount ELSE 0 END) - 
            SUM(CASE WHEN type = 'REFUND' THEN amount ELSE 0 END) as totalAmount
        FROM transactions 
        WHERE timestamp BETWEEN :startDate AND :endDate
        GROUP BY currency
        HAVING totalAmount != 0
    """)
    fun getTotalSpentByCurrency(startDate: Long, endDate: Long): Flow<List<CurrencySum>>

    @Query("""
        SELECT 
            currency,
            SUM(amount) as totalAmount
        FROM transactions 
        WHERE type = 'INVESTMENT' AND timestamp BETWEEN :startDate AND :endDate
        GROUP BY currency
        HAVING totalAmount != 0
    """)
    fun getTotalInvestedByCurrency(startDate: Long, endDate: Long): Flow<List<CurrencySum>>

    @Query("""
        SELECT category, SUM(amount) as totalAmount 
        FROM transactions 
        WHERE (type = 'DEBIT' OR type = 'INVESTMENT') AND timestamp BETWEEN :startDate AND :endDate 
        GROUP BY category 
        ORDER BY totalAmount DESC
    """)
    fun getExpensesByCategory(startDate: Long, endDate: Long): Flow<List<CategorySum>>

    @Query("SELECT * FROM transactions WHERE category = 'Uncategorized' ORDER BY timestamp DESC")
    fun getUncategorizedTransactions(): Flow<List<Transaction>>

    @Query("UPDATE transactions SET category = :newCategory, merchant = :newName WHERE id = :transactionId")
    suspend fun updateTransactionDetails(transactionId: Int, newName: String, newCategory: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMerchantRule(mapping: MerchantMapping)

    @Query("""
        SELECT * 
        FROM merchant_mappings 
        WHERE :rawMerchantInput LIKE '%' || rawMerchant || '%' 
        ORDER BY length(rawMerchant) DESC 
        LIMIT 1
    """)
    suspend fun getRuleForMerchant(rawMerchantInput: String): MerchantMapping?

    @Query("SELECT * FROM merchant_mappings ORDER BY rawMerchant ASC")
    fun getAllRules(): Flow<List<MerchantMapping>>

    @Delete
    suspend fun deleteRule(mapping: MerchantMapping)

    @Query("UPDATE transactions SET category = :newCategory, merchant = :newName WHERE lower(trim(rawMerchant)) = lower(trim(:rawMerchantKey))")
    suspend fun updatePastTransactionsForMerchant(rawMerchantKey: String, newName: String, newCategory: String)

    @Query("DELETE FROM transactions")
    suspend fun clearAllTransactions()

    @RoomTransaction
    suspend fun applyRuleAndRename(rawMerchantKey: String, newName: String, newCategory: String) {
        val cleanKey = rawMerchantKey.trim().lowercase()
        insertMerchantRule(MerchantMapping(cleanKey, newName, newCategory))
        updatePastTransactionsForMerchant(cleanKey, newName, newCategory)
    }
}

data class CurrencySum(
    val currency: String,
    val totalAmount: Double
)
