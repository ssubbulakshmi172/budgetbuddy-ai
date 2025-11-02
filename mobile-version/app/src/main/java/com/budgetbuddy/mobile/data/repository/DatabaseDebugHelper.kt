package com.budgetbuddy.mobile.data.repository

import android.util.Log
import com.budgetbuddy.mobile.data.dao.TransactionDao
import com.budgetbuddy.mobile.data.model.Transaction
import kotlinx.coroutines.flow.first

object DatabaseDebugHelper {
    private const val TAG = "DATABASE_DEBUG"
    
    /**
     * Log all transactions from the database
     */
    suspend fun logAllTransactions(transactionDao: TransactionDao, userId: Long) {
        Log.d(TAG, "🔍 ========== DATABASE DEBUG - ALL TRANSACTIONS ==========")
        
        val transactions = transactionDao.getTransactionsByUser(userId).first()
        
        if (transactions.isEmpty()) {
            Log.d(TAG, "❌ No transactions found in database")
            return
        }
        
        Log.d(TAG, "📊 Total transactions: ${transactions.size}")
        Log.d(TAG, "")
        
        transactions.forEachIndexed { index, transaction ->
            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.d(TAG, "Transaction #${index + 1}")
            Log.d(TAG, "  ID: ${transaction.id}")
            Log.d(TAG, "  Date: ${transaction.date}")
            Log.d(TAG, "  Narration: ${transaction.narration ?: "N/A"}")
            Log.d(TAG, "  Amount: ${transaction.amount}")
            Log.d(TAG, "  Withdrawal: ${transaction.withdrawalAmt ?: "N/A"}")
            Log.d(TAG, "  Deposit: ${transaction.depositAmt ?: "N/A"}")
            Log.d(TAG, "  Category: ${transaction.categoryName ?: "N/A"}")
            Log.d(TAG, "  Predicted Category: ${transaction.predictedCategory ?: "N/A"}")
            Log.d(TAG, "  Transaction Type: ${transaction.predictedTransactionType ?: "N/A"}")
            Log.d(TAG, "  Intent: ${transaction.predictedIntent ?: "N/A"}")
            Log.d(TAG, "  Confidence: ${transaction.predictionConfidence ?: "N/A"}")
            Log.d(TAG, "  User ID: ${transaction.userId}")
            Log.d(TAG, "  Closing Balance: ${transaction.closingBalance ?: "N/A"}")
            Log.d(TAG, "")
        }
        
        // Summary
        val totalIncome = transactionDao.getTotalIncome(userId) ?: 0.0
        val totalExpenses = transactionDao.getTotalExpenses(userId) ?: 0.0
        val expensesByCategory = transactionDao.getExpensesByCategory(userId)
        
        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.d(TAG, "📈 SUMMARY")
        Log.d(TAG, "  Total Income: $totalIncome")
        Log.d(TAG, "  Total Expenses: ${totalExpenses}")
        Log.d(TAG, "  Balance: ${totalIncome - totalExpenses}")
        Log.d(TAG, "")
        
        if (expensesByCategory.isNotEmpty()) {
            Log.d(TAG, "📊 Expenses by Category:")
            expensesByCategory.forEach { (category, total) ->
                Log.d(TAG, "  - $category: $total")
            }
        }
        
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
    }
    
    /**
     * Log transaction statistics
     */
    suspend fun logTransactionStats(transactionDao: TransactionDao, userId: Long) {
        val transactions = transactionDao.getTransactionsByUser(userId).first()
        
        Log.d(TAG, "📊 Transaction Statistics:")
        Log.d(TAG, "  Total Count: ${transactions.size}")
        
        val incomeCount = transactions.count { it.amount > 0 }
        val expenseCount = transactions.count { it.amount < 0 }
        
        Log.d(TAG, "  Income Transactions: $incomeCount")
        Log.d(TAG, "  Expense Transactions: $expenseCount")
        
        val categories = transactions.mapNotNull { it.categoryName }.distinct()
        Log.d(TAG, "  Unique Categories: ${categories.size}")
        Log.d(TAG, "  Categories: ${categories.joinToString(", ")}")
    }
    
    /**
     * Format transaction for display
     */
    fun formatTransaction(transaction: Transaction): String {
        return buildString {
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("Transaction ID: ${transaction.id}")
            appendLine("Date: ${transaction.date}")
            appendLine("Narration: ${transaction.narration ?: "N/A"}")
            appendLine("Amount: ${transaction.amount}")
            appendLine("Type: ${if (transaction.amount >= 0) "Income" else "Expense"}")
            appendLine("Category: ${transaction.categoryName ?: "Uncategorized"}")
            if (transaction.predictedCategory != null) {
                appendLine("Predicted: ${transaction.predictedCategory} (${String.format("%.1f", (transaction.predictionConfidence ?: 0.0) * 100)}%)")
            }
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        }
    }
}

