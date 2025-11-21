package com.budgetbuddy.mobile.service

import com.budgetbuddy.mobile.data.dao.*
import android.util.Log
import kotlinx.coroutines.flow.first

/**
 * Data Cleanup Service - Equivalent to Spring Boot's DataCleanupService
 * Clears all transaction data and financial guidance data for a user
 */
class DataCleanupService(
    private val transactionDao: TransactionDao,
    private val categoryOverspendingAlertDao: CategoryOverspendingAlertDao,
    private val moneyLeakDao: MoneyLeakDao,
    private val savingsProjectionDao: SavingsProjectionDao,
    private val weekendOverspendingDao: WeekendOverspendingDao,
    private val spendingPatternDao: SpendingPatternDao,
    private val spendingPredictionDao: SpendingPredictionDao,
    private val financialNudgeDao: FinancialNudgeDao
) {
    
    /**
     * Clear all transaction data and financial guidance data for a user
     * WARNING: This will permanently delete all transactions and all financial analysis data
     * Equivalent to Spring Boot's clearAllTransactionAndGuidanceData()
     */
    suspend fun clearAllTransactionAndGuidanceData(userId: Long) {
        Log.w("DataCleanupService", "⚠️ Starting data cleanup for user: $userId")
        
        try {
            // 1. Clear all financial guidance data first (to avoid foreign key issues)
            clearFinancialGuidanceData(userId)
            
            // 2. Delete all transactions for this user
            transactionDao.deleteAllTransactionsForUser(userId)
            Log.i("DataCleanupService", "✅ Deleted all transactions for user $userId")
            
            Log.i("DataCleanupService", "✅ Successfully cleared all transaction and financial guidance data for user: $userId")
        } catch (e: Exception) {
            Log.e("DataCleanupService", "❌ Error clearing transaction and guidance data for user $userId: ${e.message}", e)
            throw RuntimeException("Failed to clear data: ${e.message}", e)
        }
    }
    
    /**
     * Clear all financial guidance data for a user
     * Equivalent to Spring Boot's clearFinancialGuidanceData()
     */
    private suspend fun clearFinancialGuidanceData(userId: Long) {
        Log.i("DataCleanupService", "Clearing financial guidance data for user: $userId")
        
        try {
            // Delete Category Overspending Alerts
            categoryOverspendingAlertDao.deleteByUserId(userId)
            Log.i("DataCleanupService", "✅ Deleted category overspending alerts")
            
            // Delete Money Leaks
            moneyLeakDao.deleteByUserId(userId)
            Log.i("DataCleanupService", "✅ Deleted money leaks")
            
            // Delete Savings Projections
            savingsProjectionDao.deleteByUserId(userId)
            Log.i("DataCleanupService", "✅ Deleted savings projections")
            
            // Delete Weekend Overspending
            weekendOverspendingDao.deleteByUserId(userId)
            Log.i("DataCleanupService", "✅ Deleted weekend overspending records")
            
            // Delete Spending Patterns
            try {
                spendingPatternDao.deactivateAllPatternsForUser(userId)
                // Also delete all patterns
                val allPatterns = spendingPatternDao.getAllPatternsByUser(userId).first()
                allPatterns.forEach { pattern ->
                    spendingPatternDao.deletePattern(pattern)
                }
                Log.i("DataCleanupService", "✅ Deleted spending patterns")
            } catch (e: Exception) {
                Log.w("DataCleanupService", "Could not delete spending patterns: ${e.message}")
            }
            
            // Delete Spending Predictions
            try {
                spendingPredictionDao.deleteAllPredictionsForUser(userId)
                Log.i("DataCleanupService", "✅ Deleted spending predictions")
            } catch (e: Exception) {
                Log.w("DataCleanupService", "Could not delete spending predictions: ${e.message}")
            }
            
            // Delete Financial Nudges
            try {
                val allNudges = financialNudgeDao.getAllNudgesByUser(userId).first()
                allNudges.forEach { nudge ->
                    financialNudgeDao.deleteNudge(nudge)
                }
                Log.i("DataCleanupService", "✅ Deleted financial nudges")
            } catch (e: Exception) {
                Log.w("DataCleanupService", "Could not delete financial nudges: ${e.message}")
            }
            
            Log.i("DataCleanupService", "✅ Successfully cleared all financial guidance data for user: $userId")
        } catch (e: Exception) {
            Log.e("DataCleanupService", "❌ Error clearing financial guidance data for user $userId: ${e.message}", e)
            throw e
        }
    }
}

