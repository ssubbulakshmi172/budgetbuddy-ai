package com.budgetbuddy.mobile.data.repository

import com.budgetbuddy.mobile.data.dao.TransactionDao
import com.budgetbuddy.mobile.data.model.Transaction
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class TransactionRepository(private val transactionDao: TransactionDao) {
    
    fun getTransactionsByUser(userId: Long): Flow<List<Transaction>> {
        return transactionDao.getTransactionsByUser(userId)
    }
    
    fun getTransactionsByDateRange(
        userId: Long,
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<List<Transaction>> {
        return transactionDao.getTransactionsByDateRange(userId, startDate, endDate)
    }
    
    fun getTransactionsByCategory(userId: Long, category: String): Flow<List<Transaction>> {
        return transactionDao.getTransactionsByCategory(userId, category)
    }
    
    fun searchTransactions(userId: Long, query: String): Flow<List<Transaction>> {
        return transactionDao.searchTransactions(userId, query)
    }
    
    suspend fun getTransactionById(id: Long): Transaction? {
        return transactionDao.getTransactionById(id)
    }
    
    suspend fun insertTransaction(transaction: Transaction): Long {
        return transactionDao.insertTransaction(transaction)
    }
    
    suspend fun insertTransactions(transactions: List<Transaction>): List<Long> {
        return transactionDao.insertTransactions(transactions)
    }
    
    suspend fun updateTransaction(transaction: Transaction) {
        transactionDao.updateTransaction(transaction)
    }
    
    suspend fun deleteTransaction(transaction: Transaction) {
        transactionDao.deleteTransaction(transaction)
    }
    
    suspend fun deleteAllTransactions(userId: Long) {
        transactionDao.deleteAllTransactionsForUser(userId)
    }
    
    suspend fun getTotalExpenses(userId: Long): Double {
        return transactionDao.getTotalExpenses(userId) ?: 0.0
    }
    
    suspend fun getTotalIncome(userId: Long): Double {
        return transactionDao.getTotalIncome(userId) ?: 0.0
    }
    
    suspend fun getExpensesByCategory(userId: Long): List<TransactionDao.CategoryExpense> {
        return transactionDao.getExpensesByCategory(userId)
    }
}

