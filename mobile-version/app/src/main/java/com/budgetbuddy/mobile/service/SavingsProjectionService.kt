package com.budgetbuddy.mobile.service

import com.budgetbuddy.mobile.data.dao.SavingsProjectionDao
import com.budgetbuddy.mobile.data.dao.TransactionDao
import com.budgetbuddy.mobile.data.model.SavingsProjection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import kotlin.math.abs

class SavingsProjectionService(
    private val transactionDao: TransactionDao,
    private val projectionDao: SavingsProjectionDao
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
        val category = transaction.categoryName ?: transaction.predictedCategory
        return isInvestmentCategory(category)
    }
    
    /**
     * Check if a transaction is income/salary
     */
    private fun isIncomeTransaction(transaction: com.budgetbuddy.mobile.data.model.Transaction): Boolean {
        val category = transaction.categoryName ?: transaction.predictedCategory
        if (category != null) {
            val normalized = category.lowercase().trim()
            if (normalized == "salary" || normalized.startsWith("salary /") ||
                normalized == "income" || normalized.startsWith("income /")) {
                return true
            }
        }
        // Positive amounts are deposits/income
        return transaction.amount > 0
    }
    
    /**
     * Calculate monthly income average
     */
    private suspend fun calculateMonthlyIncome(transactions: List<com.budgetbuddy.mobile.data.model.Transaction>): Double {
        val incomeTxs = transactions.filter { isIncomeTransaction(it) }
        if (incomeTxs.isEmpty()) return 0.0
        
        val byMonth = incomeTxs.groupBy { 
            java.time.YearMonth.from(it.date) 
        }
        
        val monthlyTotals = byMonth.values.map { monthTxs ->
            monthTxs.sumOf { abs(it.amount) }
        }
        
        return monthlyTotals.average()
    }
    
    /**
     * Calculate monthly expense average (excluding investments)
     */
    private suspend fun calculateMonthlyExpense(transactions: List<com.budgetbuddy.mobile.data.model.Transaction>): Double {
        val expenseTxs = transactions
            .filter { it.amount < 0 }
            .filter { !isInvestmentTransaction(it) }
            .filter { !isIncomeTransaction(it) }
        
        if (expenseTxs.isEmpty()) return 0.0
        
        val byMonth = expenseTxs.groupBy { 
            java.time.YearMonth.from(it.date) 
        }
        
        val monthlyTotals = byMonth.values.map { monthTxs ->
            monthTxs.sumOf { abs(it.amount) }
        }
        
        return monthlyTotals.average()
    }
    
    /**
     * Calculate monthly investment average
     */
    private suspend fun calculateMonthlyInvestment(transactions: List<com.budgetbuddy.mobile.data.model.Transaction>): Double {
        val investmentTxs = transactions.filter { isInvestmentTransaction(it) }
        if (investmentTxs.isEmpty()) return 0.0
        
        val byMonth = investmentTxs.groupBy { 
            java.time.YearMonth.from(it.date) 
        }
        
        val monthlyTotals = byMonth.values.map { monthTxs ->
            monthTxs.sumOf { abs(it.amount) }
        }
        
        return monthlyTotals.average()
    }
    
    /**
     * Calculate current savings (sum of positive net months this year, including investments)
     */
    private suspend fun calculateCurrentSavings(userId: Long, year: Int): Double {
        val yearStart = LocalDate.of(year, 1, 1)
        val today = LocalDate.now()
        
        val allTransactions = transactionDao.getTransactionsByUser(userId).first()
        val yearTransactions = allTransactions
            .filter { it.userId == userId }
            .filter { it.date.isAfter(yearStart.minusDays(1)) && it.date.isBefore(today.plusDays(1)) }
        
        val byMonth = yearTransactions.groupBy { 
            java.time.YearMonth.from(it.date) 
        }
        
        var currentSavings = 0.0
        
        for ((month, monthTxs) in byMonth) {
            val income = monthTxs
                .filter { isIncomeTransaction(it) }
                .sumOf { abs(it.amount) }
            
            val expenses = monthTxs
                .filter { it.amount < 0 }
                .filter { !isInvestmentTransaction(it) }
                .filter { !isIncomeTransaction(it) }
                .sumOf { abs(it.amount) }
            
            val investments = monthTxs
                .filter { isInvestmentTransaction(it) }
                .sumOf { abs(it.amount) }
            
            // Net savings for the month: Income - Expenses + Investments
            val monthlyNet = income - expenses + investments
            currentSavings += monthlyNet
        }
        
        return currentSavings
    }
    
    /**
     * Calculate and project year-end savings
     * INCLUDES investments as part of savings
     */
    suspend fun calculateYearEndSavings(userId: Long): SavingsProjection {
        val now = LocalDate.now()
        val currentMonth = now.monthValue
        val currentYear = now.year
        val remainingMonths = 12 - currentMonth
        
        // Get transactions for last 6 months
        val sixMonthsAgo = LocalDate.now().minusMonths(6)
        val allTransactions = transactionDao.getTransactionsByUser(userId).first()
        val transactions = allTransactions
            .filter { it.userId == userId }
            .filter { it.date.isAfter(sixMonthsAgo) }
        
        // Calculate averages
        val monthlyIncomeAvg = calculateMonthlyIncome(transactions)
        val monthlyExpenseAvg = calculateMonthlyExpense(transactions)
        val monthlyInvestmentAvg = calculateMonthlyInvestment(transactions)
        
        // Calculate monthly savings rate: Income - Expenses + Investments
        val monthlySavingsRate = monthlyIncomeAvg - monthlyExpenseAvg + monthlyInvestmentAvg
        
        // Calculate current savings
        val currentSavings = calculateCurrentSavings(userId, currentYear)
        
        // Project additional savings for remaining months
        val projectedAdditionalSavings = monthlySavingsRate * remainingMonths
        
        // Project year-end savings
        val projectedYearEnd = currentSavings + projectedAdditionalSavings
        
        // Calculate confidence score (based on data availability)
        val monthsOfData = transactions.groupBy { java.time.YearMonth.from(it.date) }.size
        val confidenceScore = (monthsOfData / 6.0).coerceAtMost(1.0)
        
        // Trend adjustment factor (simplified - can be enhanced)
        val trendAdjustmentFactor = 1.0
        
        val projection = SavingsProjection(
            userId = userId,
            projectionDate = now,
            currentMonth = currentMonth,
            currentSavings = currentSavings,
            monthlyIncomeAvg = monthlyIncomeAvg,
            monthlyExpenseAvg = monthlyExpenseAvg,
            monthlySavingsRate = monthlySavingsRate,
            remainingMonths = remainingMonths,
            projectedAdditionalSavings = projectedAdditionalSavings,
            projectedYearEnd = projectedYearEnd,
            confidenceScore = confidenceScore,
            trendAdjustmentFactor = trendAdjustmentFactor,
            year = currentYear
        )
        
        // Save projection
        projectionDao.insert(projection)
        
        return projection
    }
    
    /**
     * Get latest projection for user
     */
    fun getLatestProjection(userId: Long, year: Int): Flow<SavingsProjection?> {
        return projectionDao.getLatestProjection(userId, year)
    }
    
    suspend fun getLatestProjectionSync(userId: Long, year: Int): SavingsProjection? {
        return projectionDao.getLatestProjectionSync(userId, year)
    }
}

