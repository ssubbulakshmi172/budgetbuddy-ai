package com.budgetbuddy.mobile.service

import com.budgetbuddy.mobile.data.dao.CategoryOverspendingAlertDao
import com.budgetbuddy.mobile.data.dao.TransactionDao
import com.budgetbuddy.mobile.data.model.CategoryOverspendingAlert
import com.budgetbuddy.mobile.data.model.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.sqrt

class CategoryOverspendingService(
    private val transactionDao: TransactionDao,
    private val alertDao: CategoryOverspendingAlertDao
) {
    
    /**
     * Detect category-level overspending for current month
     */
    suspend fun detectOverspending(userId: Long): List<CategoryOverspendingAlert> {
        val currentMonth = YearMonth.now()
        val monthStart = currentMonth.atDay(1)
        val monthEnd = currentMonth.atEndOfMonth()
        val today = LocalDate.now()
        val daysElapsed = today.dayOfMonth
        val totalDaysInMonth = currentMonth.lengthOfMonth()
        
        // Get current month transactions
        val allTransactions = transactionDao.getTransactionsByUser(userId).first()
        val currentMonthTransactions = allTransactions
            .filter { it.userId == userId }
            .filter { it.amount < 0 }
            .filter { it.date.isAfter(monthStart.minusDays(1)) && it.date.isBefore(monthEnd.plusDays(1)) }
        
        // Get historical transactions (last 6 months)
        val historicalStart = LocalDate.now().minusMonths(6)
        val historicalTransactions = allTransactions
            .filter { it.userId == userId }
            .filter { it.amount < 0 }
            .filter { it.date.isAfter(historicalStart) && it.date.isBefore(monthStart) }
        
        // Group by category
        val currentByCategory = currentMonthTransactions
            .filter { it.predictedCategory != null }
            .groupBy { it.predictedCategory!! }
        
        val historicalByCategory = historicalTransactions
            .filter { it.predictedCategory != null }
            .groupBy { it.predictedCategory!! }
        
        val alerts = mutableListOf<CategoryOverspendingAlert>()
        
        for ((category, currentTxs) in currentByCategory) {
            // Calculate current month spending
            val currentAmount = currentTxs.sumOf { abs(it.amount) }
            
            // Get historical data for this category
            val historicalTxs = historicalByCategory[category] ?: emptyList()
            
            if (historicalTxs.isEmpty()) {
                continue // Skip if no historical data
            }
            
            // Calculate historical average and standard deviation
            val monthlySpending = historicalTxs
                .groupBy { YearMonth.from(it.date) }
                .mapValues { (_, txs) -> txs.sumOf { abs(it.amount) } }
            
            if (monthlySpending.size < 2) {
                continue // Need at least 2 months of data
            }
            
            val historicalAvg = monthlySpending.values.average()
            val variance = monthlySpending.values.map { 
                val diff = it - historicalAvg
                diff * diff
            }.average()
            val standardDeviation = sqrt(variance)
            
            // Project monthly spending based on current rate
            val projectedMonthly = (currentAmount / daysElapsed) * totalDaysInMonth
            
            // Calculate percentage increase
            val percentageIncrease = if (historicalAvg > 0) {
                ((projectedMonthly - historicalAvg) / historicalAvg) * 100
            } else {
                0.0
            }
            
            // Determine alert level
            val alertLevel = when {
                percentageIncrease > 50 || projectedMonthly > historicalAvg + (2 * standardDeviation) -> 
                    CategoryOverspendingAlert.AlertLevel.CRITICAL
                percentageIncrease > 25 -> CategoryOverspendingAlert.AlertLevel.HIGH
                percentageIncrease > 10 -> CategoryOverspendingAlert.AlertLevel.MEDIUM
                else -> CategoryOverspendingAlert.AlertLevel.LOW
            }
            
            // Check if alert already exists for this category and month
            val existingAlert = alertDao.findByCategoryAndMonth(userId, category, currentMonth)
            
            if (existingAlert == null) {
                // Create new alert
                val alert = CategoryOverspendingAlert(
                    userId = userId,
                    category = category,
                    alertLevel = alertLevel,
                    currentAmount = currentAmount,
                    historicalAvg = historicalAvg,
                    standardDeviation = standardDeviation,
                    percentageIncrease = percentageIncrease,
                    projectedMonthly = projectedMonthly,
                    month = currentMonth,
                    daysElapsed = daysElapsed,
                    isActive = true
                )
                alerts.add(alert)
            } else {
                // Update existing alert
                val updatedAlert = existingAlert.copy(
                    alertLevel = alertLevel,
                    currentAmount = currentAmount,
                    historicalAvg = historicalAvg,
                    standardDeviation = standardDeviation,
                    percentageIncrease = percentageIncrease,
                    projectedMonthly = projectedMonthly,
                    daysElapsed = daysElapsed
                )
                alerts.add(updatedAlert)
            }
        }
        
        // Save alerts
        if (alerts.isNotEmpty()) {
            alertDao.insertAll(alerts)
        }
        
        return alerts
    }
    
    /**
     * Get active alerts for user
     */
    fun getActiveAlerts(userId: Long): Flow<List<CategoryOverspendingAlert>> {
        return alertDao.getActiveAlerts(userId)
    }
    
    suspend fun getActiveAlertsSync(userId: Long): List<CategoryOverspendingAlert> {
        return alertDao.getActiveAlertsSync(userId)
    }
}

