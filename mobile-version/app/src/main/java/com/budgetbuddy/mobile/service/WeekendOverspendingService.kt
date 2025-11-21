package com.budgetbuddy.mobile.service

import com.budgetbuddy.mobile.data.dao.TransactionDao
import com.budgetbuddy.mobile.data.dao.WeekendOverspendingDao
import com.budgetbuddy.mobile.data.model.WeekendOverspending
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

class WeekendOverspendingService(
    private val transactionDao: TransactionDao,
    private val weekendDao: WeekendOverspendingDao
) {
    
    /**
     * Check if a transaction category is an investment
     */
    private fun isInvestmentCategory(category: String?): Boolean {
        if (category == null || category.trim().isEmpty()) {
            return false
        }
        val normalized = category.lowercase().trim()
        return normalized == "investments" || normalized.startsWith("investments /")
    }
    
    /**
     * Check if a transaction is an investment
     */
    private fun isInvestmentTransaction(transaction: com.budgetbuddy.mobile.data.model.Transaction): Boolean {
        return isInvestmentCategory(transaction.categoryName) || 
               isInvestmentCategory(transaction.predictedCategory)
    }
    
    /**
     * Determine trend based on historical data
     */
    private fun determineTrend(
        currentRatio: Double,
        historicalRatios: List<Double>
    ): WeekendOverspending.Trend {
        if (historicalRatios.isEmpty()) {
            return WeekendOverspending.Trend.STABLE
        }
        
        val avgHistorical = historicalRatios.average()
        
        return when {
            currentRatio > avgHistorical * 1.1 -> WeekendOverspending.Trend.INCREASING
            currentRatio < avgHistorical * 0.9 -> WeekendOverspending.Trend.DECREASING
            else -> WeekendOverspending.Trend.STABLE
        }
    }
    
    /**
     * Determine alert level based on ratio
     */
    private fun determineAlertLevel(ratio: Double): WeekendOverspending.AlertLevel {
        return when {
            ratio > 1.5 -> WeekendOverspending.AlertLevel.HIGH
            ratio > 1.3 -> WeekendOverspending.AlertLevel.MEDIUM
            else -> WeekendOverspending.AlertLevel.LOW
        }
    }
    
    /**
     * Detect weekend overspending patterns
     */
    suspend fun detectWeekendOverspending(userId: Long): List<WeekendOverspending> {
        val currentMonth = YearMonth.now()
        val monthStart = currentMonth.atDay(1)
        val monthEnd = currentMonth.atEndOfMonth()
        
        // Get current month transactions, excluding investments
        val allTransactions = transactionDao.getTransactionsByUser(userId).first()
        val monthStartDate = currentMonth.atDay(1)
        val monthEndDate = currentMonth.atEndOfMonth()
        val transactions = allTransactions
            .filter { it.userId == userId }
            .filter { it.amount < 0 }
            .filter { !isInvestmentTransaction(it) }
            .filter { it.date.isAfter(monthStartDate.minusDays(1)) && 
                      it.date.isBefore(monthEndDate.plusDays(1)) }
        
        if (transactions.isEmpty()) {
            return emptyList()
        }
        
        // Group by category
        val byCategory = transactions
            .filter { it.predictedCategory != null }
            .groupBy { it.predictedCategory!! }
        
        val results = mutableListOf<WeekendOverspending>()
        
        // Get historical data for trend analysis
        val historicalStart = LocalDate.now().minusMonths(6)
        val historicalTransactions = allTransactions
            .filter { it.userId == userId }
            .filter { it.amount < 0 }
            .filter { !isInvestmentTransaction(it) }
            .filter { it.date.isAfter(historicalStart) && it.date.isBefore(monthStartDate) }
        
        val historicalByCategory = historicalTransactions
            .filter { it.predictedCategory != null }
            .groupBy { it.predictedCategory!! }
        
        for ((category, categoryTxs) in byCategory) {
            // Separate weekend (Sat, Sun) and weekday (Mon-Fri)
            val weekendTxs = categoryTxs.filter {
                val day = it.date.dayOfWeek
                day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY
            }
            
            val weekdayTxs = categoryTxs.filter {
                val day = it.date.dayOfWeek
                day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY
            }
            
            if (weekendTxs.isEmpty() || weekdayTxs.isEmpty()) {
                continue
            }
            
            val weekendSpending = weekendTxs.sumOf { abs(it.amount) }
            val weekdaySpending = weekdayTxs.sumOf { abs(it.amount) }
            
            // Calculate averages (per transaction)
            val weekendAvg = weekendSpending / weekendTxs.size
            val weekdayAvg = weekdaySpending / weekdayTxs.size
            
            // Calculate ratio
            val ratio = if (weekdayAvg > 0) {
                weekendAvg / weekdayAvg
            } else {
                1.0
            }
            
            // Calculate percentage increase
            val percentageIncrease = if (weekdayAvg > 0) {
                ((weekendAvg - weekdayAvg) / weekdayAvg) * 100
            } else {
                0.0
            }
            
            // Get historical data for trend
            val historicalCategoryTxs = historicalByCategory[category] ?: emptyList()
            val historicalRatios = if (historicalCategoryTxs.isNotEmpty()) {
                val historicalByMonth = historicalCategoryTxs.groupBy { 
                    YearMonth.from(it.date) 
                }
                historicalByMonth.values.map { monthTxs ->
                    val histWeekend = monthTxs.filter { 
                        val day = it.date.dayOfWeek
                        day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY
                    }
                    val histWeekday = monthTxs.filter { 
                        val day = it.date.dayOfWeek
                        day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY
                    }
                    if (histWeekend.isNotEmpty() && histWeekday.isNotEmpty()) {
                        val histWeekendAvg = histWeekend.sumOf { abs(it.amount) } / histWeekend.size
                        val histWeekdayAvg = histWeekday.sumOf { abs(it.amount) } / histWeekday.size
                        if (histWeekdayAvg > 0) histWeekendAvg / histWeekdayAvg else 1.0
                    } else {
                        1.0
                    }
                }
            } else {
                emptyList()
            }
            
            val trend = determineTrend(ratio, historicalRatios)
            val alertLevel = determineAlertLevel(ratio)
            
            val weekendOverspending = WeekendOverspending(
                userId = userId,
                category = category,
                weekendAvg = weekendAvg,
                weekendSpending = weekendSpending,
                weekdayAvg = weekdayAvg,
                weekdaySpending = weekdaySpending,
                ratio = ratio,
                percentageIncrease = percentageIncrease,
                month = currentMonth,
                year = currentMonth.year,
                trend = trend,
                alertLevel = alertLevel,
                isActive = true
            )
            
            results.add(weekendOverspending)
        }
        
        // Save results
        if (results.isNotEmpty()) {
            weekendDao.insertAll(results)
        }
        
        return results
    }
    
    /**
     * Get active weekend overspending for user
     */
    suspend fun getActiveAlerts(userId: Long, month: YearMonth): List<WeekendOverspending> {
        return weekendDao.getActiveByMonthSync(userId, month)
    }
}

