package com.budgetbuddy.mobile.data.dao

import androidx.room.*
import com.budgetbuddy.mobile.data.model.Transaction
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface TransactionDao {
    
    @Query("SELECT * FROM transactions WHERE userId = :userId ORDER BY date DESC")
    fun getTransactionsByUser(userId: Long): Flow<List<Transaction>>
    
    @Query("SELECT * FROM transactions WHERE userId = :userId AND date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    fun getTransactionsByDateRange(userId: Long, startDate: LocalDate, endDate: LocalDate): Flow<List<Transaction>>
    
    @Query("SELECT * FROM transactions WHERE userId = :userId AND categoryName = :category ORDER BY date DESC")
    fun getTransactionsByCategory(userId: Long, category: String): Flow<List<Transaction>>
    
    @Query("SELECT * FROM transactions WHERE userId = :userId AND (narration LIKE '%' || :searchQuery || '%' OR categoryName LIKE '%' || :searchQuery || '%') ORDER BY date DESC")
    fun searchTransactions(userId: Long, searchQuery: String): Flow<List<Transaction>>
    
    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransactionById(id: Long): Transaction?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<Transaction>): List<Long>
    
    @Update
    suspend fun updateTransaction(transaction: Transaction)
    
    @Delete
    suspend fun deleteTransaction(transaction: Transaction)
    
    @Query("DELETE FROM transactions WHERE userId = :userId")
    suspend fun deleteAllTransactionsForUser(userId: Long)
    
    @Query("SELECT SUM(ABS(amount)) FROM transactions WHERE userId = :userId AND amount < 0")
    suspend fun getTotalExpenses(userId: Long): Double?
    
    @Query("SELECT SUM(amount) FROM transactions WHERE userId = :userId AND amount > 0")
    suspend fun getTotalIncome(userId: Long): Double?
    
    @Query("""
        SELECT categoryName, SUM(ABS(amount)) as total 
        FROM transactions 
        WHERE userId = :userId AND amount < 0 AND categoryName IS NOT NULL
        GROUP BY categoryName
        ORDER BY total DESC
    """)
    suspend fun getExpensesByCategory(userId: Long): List<CategoryExpense>
    
    data class CategoryExpense(
        val categoryName: String,
        val total: Double
    )
}

