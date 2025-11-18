package com.budgetbuddy.mobile.service

import com.budgetbuddy.mobile.data.dao.FinancialNudgeDao
import com.budgetbuddy.mobile.data.dao.SpendingPredictionDao
import com.budgetbuddy.mobile.data.dao.SpendingPatternDao
import com.budgetbuddy.mobile.data.dao.TransactionDao
import com.budgetbuddy.mobile.data.model.FinancialNudge
import com.budgetbuddy.mobile.data.model.SpendingPrediction
import com.budgetbuddy.mobile.data.model.SpendingPattern
import kotlinx.coroutines.flow.first
import java.time.LocalDate

class FinancialNudgeService(
    private val financialNudgeDao: FinancialNudgeDao,
    private val spendingPredictionDao: SpendingPredictionDao,
    private val spendingPatternDao: SpendingPatternDao,
    private val transactionDao: TransactionDao,
    private val spendingPredictionService: SpendingPredictionService
) {
    
    suspend fun generateNudges(userId: Long): List<FinancialNudge> {
        val nudges = mutableListOf<FinancialNudge>()
        
        // 1. Check for overspending risks
        nudges.addAll(generateOverspendingNudges(userId))
        
        // 2. Notify about new patterns detected
        nudges.addAll(generatePatternNudges(userId))
        
        // 3. Alert about unusual trends
        nudges.addAll(generateTrendNudges(userId))
        
        // 4. Provide savings opportunities
        nudges.addAll(generateSavingsOpportunityNudges(userId))
        
        // Save nudges
        nudges.forEach { financialNudgeDao.insertNudge(it) }
        
        return nudges
    }
    
    private suspend fun generateOverspendingNudges(userId: Long): List<FinancialNudge> {
        val nudges = mutableListOf<FinancialNudge>()
        
        // Get predictions for next month
        val nextMonthStart = LocalDate.now().withDayOfMonth(1).plusMonths(1)
        val nextMonthEnd = nextMonthStart.withDayOfMonth(nextMonthStart.lengthOfMonth())
        
        val predictions = spendingPredictionService.predictFutureSpending(userId, nextMonthStart, nextMonthEnd)
        
        predictions.filter { it.isOverspendingRisk }.forEach { prediction ->
            val historicalAverage = getHistoricalAverage(userId, prediction.category ?: "")
            val percentageIncrease = if (historicalAverage > 0) {
                ((prediction.predictedAmount / historicalAverage) - 1) * 100
            } else 0.0
            
            nudges.add(
                FinancialNudge(
                    userId = userId,
                    nudgeType = FinancialNudge.NudgeType.OVERSPENDING_RISK,
                    title = "Overspending Alert: ${prediction.category}",
                    message = String.format(
                        "Your spending in %s is predicted to increase by %.1f%%. " +
                        "Based on your patterns, you're likely to spend ₹%.0f.",
                        prediction.category,
                        percentageIncrease,
                        prediction.predictedAmount
                    ),
                    suggestion = generateOverspendingSuggestion(prediction),
                    category = prediction.category,
                    subcategory = prediction.subcategory,
                    relatedAmount = prediction.predictedAmount,
                    priority = if (prediction.riskLevel == SpendingPrediction.RiskLevel.HIGH) {
                        FinancialNudge.Priority.HIGH
                    } else {
                        FinancialNudge.Priority.MEDIUM
                    },
                    isRead = false,
                    isDismissed = false,
                    createdAt = LocalDate.now(),
                    expiresAt = LocalDate.now().plusDays(7)
                )
            )
        }
        
        return nudges
    }
    
    private suspend fun generatePatternNudges(userId: Long): List<FinancialNudge> {
        val nudges = mutableListOf<FinancialNudge>()
        
        val recentPatterns = spendingPatternDao.getActivePatternsByUser(userId).first()
            .filter { 
                it.createdAt != null && 
                it.createdAt.isAfter(LocalDate.now().minusDays(7))
            }
        
        recentPatterns.forEach { pattern ->
            nudges.add(
                FinancialNudge(
                    userId = userId,
                    nudgeType = FinancialNudge.NudgeType.PATTERN_DETECTED,
                    title = "New Spending Pattern Detected",
                    message = String.format(
                        "We noticed you regularly spend ₹%.0f on %s%s. " +
                        "This happens %s.",
                        pattern.averageAmount,
                        pattern.category ?: "Unknown",
                        pattern.subcategory?.let { " - $it" } ?: "",
                        formatPatternFrequency(pattern)
                    ),
                    suggestion = "This pattern helps us predict your future spending. Keep an eye on it!",
                    category = pattern.category,
                    subcategory = pattern.subcategory,
                    relatedAmount = pattern.averageAmount,
                    priority = FinancialNudge.Priority.LOW,
                    isRead = false,
                    isDismissed = false,
                    createdAt = LocalDate.now(),
                    expiresAt = LocalDate.now().plusDays(7)
                )
            )
        }
        
        return nudges
    }
    
    private suspend fun generateTrendNudges(userId: Long): List<FinancialNudge> {
        val nudges = mutableListOf<FinancialNudge>()
        
        // Get high-risk predictions based on trends
        val trendBasedPredictions = spendingPredictionDao.getPredictionsByUser(userId).first()
            .filter { it.predictionMethod == "TREND_BASED" }
            .filter { it.riskLevel == SpendingPrediction.RiskLevel.HIGH }
        
        trendBasedPredictions.forEach { prediction ->
            nudges.add(
                FinancialNudge(
                    userId = userId,
                    nudgeType = FinancialNudge.NudgeType.TREND_WARNING,
                    title = "Unusual Spending Trend: ${prediction.category}",
                    message = String.format(
                        "We've detected an unusual trend in your %s spending. " +
                        "Your spending is increasing faster than usual.",
                        prediction.category
                    ),
                    suggestion = "Consider reviewing your recent transactions in this category to understand the increase.",
                    category = prediction.category,
                    subcategory = prediction.subcategory,
                    relatedAmount = prediction.predictedAmount,
                    priority = FinancialNudge.Priority.MEDIUM,
                    isRead = false,
                    isDismissed = false,
                    createdAt = LocalDate.now(),
                    expiresAt = LocalDate.now().plusDays(7)
                )
            )
        }
        
        return nudges
    }
    
    private suspend fun generateSavingsOpportunityNudges(userId: Long): List<FinancialNudge> {
        val nudges = mutableListOf<FinancialNudge>()
        
        val highFrequencyPatterns = spendingPatternDao.getActivePatternsByUser(userId).first()
            .filter { it.frequency != null && it.frequency > 10 }
            .filter { it.averageAmount > 100 }
        
        highFrequencyPatterns.forEach { pattern ->
            val monthlySpend = pattern.averageAmount * (pattern.frequency ?: 0)
            if (monthlySpend > 1000) {
                nudges.add(
                    FinancialNudge(
                        userId = userId,
                        nudgeType = FinancialNudge.NudgeType.SAVINGS_OPPORTUNITY,
                        title = "Savings Opportunity: ${pattern.category}",
                        message = String.format(
                            "You spend about ₹%.0f per month on %s. " +
                            "Small changes here could add up to significant savings.",
                            monthlySpend,
                            pattern.category
                        ),
                        suggestion = "Consider setting a monthly budget for this category or looking for alternatives.",
                        category = pattern.category,
                        subcategory = pattern.subcategory,
                        relatedAmount = monthlySpend,
                        priority = FinancialNudge.Priority.LOW,
                        isRead = false,
                        isDismissed = false,
                        createdAt = LocalDate.now(),
                        expiresAt = LocalDate.now().plusDays(7)
                    )
                )
            }
        }
        
        return nudges
    }
    
    private fun formatPatternFrequency(pattern: SpendingPattern): String {
        return when (pattern.patternType) {
            SpendingPattern.PatternType.DAILY -> "almost daily"
            SpendingPattern.PatternType.WEEKLY -> {
                pattern.dayOfWeek?.let { 
                    val dayNames = listOf("", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
                    "every ${dayNames.getOrNull(it)?.lowercase() ?: "week"}"
                } ?: "weekly"
            }
            SpendingPattern.PatternType.MONTHLY -> {
                pattern.dayOfMonth?.let {
                    "on the ${it}th of each month"
                } ?: "monthly"
            }
        }
    }
    
    private fun generateOverspendingSuggestion(prediction: SpendingPrediction): String {
        val category = prediction.category ?: return "Review your recent transactions in this category."
        
        return when {
            category.contains("Dining", ignoreCase = true) || category.contains("Food", ignoreCase = true) -> {
                "Try meal planning or cooking at home more often to reduce dining expenses."
            }
            category.contains("Transport", ignoreCase = true) -> {
                "Consider carpooling or using public transport to save on transportation costs."
            }
            category.contains("Shopping", ignoreCase = true) -> {
                "Wait 24 hours before making non-essential purchases to avoid impulse buying."
            }
            else -> "Review your recent transactions in this category and identify areas where you can cut back."
        }
    }
    
    private suspend fun getHistoricalAverage(userId: Long, category: String): Double {
        val transactions = transactionDao.getTransactionsByUser(userId).first()
        return transactions
            .filter { it.predictedCategory == category }
            .filter { it.amount < 0 }
            .filter { it.date.isAfter(LocalDate.now().minusMonths(3)) }
            .map { kotlin.math.abs(it.amount) }
            .average()
            .takeIf { !it.isNaN() } ?: 0.0
    }
    
    suspend fun getActiveNudges(userId: Long): List<FinancialNudge> {
        return financialNudgeDao.getActiveNudgesByUser(userId, LocalDate.now()).first()
    }
    
    suspend fun getUnreadNudges(userId: Long): List<FinancialNudge> {
        return financialNudgeDao.getUnreadNudgesByUser(userId, LocalDate.now()).first()
    }
    
    suspend fun markAsRead(nudgeId: Long) {
        financialNudgeDao.markAsRead(nudgeId)
    }
    
    suspend fun dismissNudge(nudgeId: Long) {
        financialNudgeDao.dismissNudge(nudgeId)
    }
}

