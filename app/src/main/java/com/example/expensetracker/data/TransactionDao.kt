package com.example.expensetracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransaction(transaction: Transaction)

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC LIMIT 100")
    fun getRecentTransactions(): Flow<List<Transaction>>

    @Query("""
        SELECT 
            SUM(CASE WHEN type = 'DEBIT' THEN amount ELSE 0 END) - 
            SUM(CASE WHEN type = 'REFUND' THEN amount ELSE 0 END) 
        FROM transactions 
        WHERE timestamp BETWEEN :startDate AND :endDate
    """)
    fun getTotalSpent(startDate: Long, endDate: Long): Flow<Double?>

    @Query("""
        SELECT SUM(amount) 
        FROM transactions 
        WHERE type = 'INVESTMENT' AND timestamp BETWEEN :startDate AND :endDate
    """)
    fun getTotalInvested(startDate: Long, endDate: Long): Flow<Double?>

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
        WHERE :rawMerchant LIKE '%' || rawMerchant || '%' 
        ORDER BY length(rawMerchant) DESC 
        LIMIT 1
    """)
    suspend fun getRuleForMerchant(rawMerchant: String): MerchantMapping?

    @Query("SELECT * FROM merchant_mappings ORDER BY rawMerchant ASC")
    fun getAllRules(): Flow<List<MerchantMapping>>

    @Delete
    suspend fun deleteRule(mapping: MerchantMapping)

    @Query("UPDATE transactions SET category = :newCategory, merchant = :newName WHERE lower(merchant) = lower(:rawMerchant)")
    suspend fun updatePastTransactionsForMerchant(rawMerchant: String, newName: String, newCategory: String)

    @Query("DELETE FROM transactions")
    suspend fun clearAllTransactions()
}
