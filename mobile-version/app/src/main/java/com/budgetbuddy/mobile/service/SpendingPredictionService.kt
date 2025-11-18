package com.budgetbuddy.mobile.service

import com.budgetbuddy.mobile.data.dao.SpendingPredictionDao
import com.budgetbuddy.mobile.data.dao.SpendingPatternDao
import com.budgetbuddy.mobile.data.dao.TransactionDao
import com.budgetbuddy.mobile.data.model.SpendingPrediction
import com.budgetbuddy.mobile.data.model.SpendingPattern
import com.budgetbuddy.mobile.data.model.Trend
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

class SpendingPredictionService(
    private val spendingPredictionDao: SpendingPredictionDao,
    private val spendingPatternDao: SpendingPatternDao,
    private val transactionDao: TransactionDao,
    private val trendAnalysisService: TrendAnalysisService
) {
    
    suspend fun predictFutureSpending(
        userId: Long,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<SpendingPrediction> {
        val historicalTransactions = transactionDao.getTransactionsByUser(userId).first()
            .filter { it.amount < 0 } // Only expenses
            .filter { it.date.isBefore(startDate) }
            .filter { it.date.isAfter(LocalDate.now().minusMonths(12)) } // Last 12 months
        
        val patterns = spendingPatternDao.getActivePatternsByUser(userId).first()
        val trends = trendAnalysisService.analyzeTrends(userId)
        
        // Group historical transactions by category
        val byCategory = historicalTransactions
            .filter { it.predictedCategory != null }
            .groupBy { it.predictedCategory ?: "Uncategorized" }
        
        val predictions = mutableListOf<SpendingPrediction>()
        
        byCategory.forEach { (category, categoryTransactions) ->
            // Find matching pattern
            val matchingPattern = patterns.firstOrNull { it.category == category }
            
            // Find trend
            val trend = trends.trends.firstOrNull { it.category == category }
            
            // Predict amount
            val prediction = predictCategorySpending(
                userId, category, categoryTransactions, matchingPattern, trend, startDate, endDate
            )
            
            if (prediction != null) {
                predictions.add(prediction)
            }
        }
        
        // Save predictions
        predictions.forEach { spendingPredictionDao.insertPrediction(it) }
        
        return predictions
    }
    
    private fun predictCategorySpending(
        userId: Long,
        category: String,
        historicalTransactions: List<com.budgetbuddy.mobile.data.model.Transaction>,
        pattern: SpendingPattern?,
        trend: Trend?,
        startDate: LocalDate,
        endDate: LocalDate
    ): SpendingPrediction? {
        if (historicalTransactions.isEmpty()) return null
        
        // Calculate base prediction using historical average
        var predictedAmount = calculateHistoricalAverage(historicalTransactions, startDate, endDate)
        var predictionMethod = "HISTORICAL_AVERAGE"
        var confidence = 0.5
        
        // Adjust based on pattern if available
        pattern?.let {
            predictedAmount = adjustForPattern(predictedAmount, it, startDate, endDate)
            predictionMethod = "PATTERN_BASED"
            confidence = (it.confidenceScore ?: 0.5).coerceAtLeast(confidence)
        }
        
        // Adjust based on trend if available
        trend?.let {
            predictedAmount = adjustForTrend(predictedAmount, it, calculateHistoricalAverage(historicalTransactions, startDate, endDate))
            if (predictionMethod != "PATTERN_BASED") {
                predictionMethod = "TREND_BASED"
            }
            confidence = it.strength.coerceAtLeast(confidence)
        }
        
        // Determine risk level
        val historicalAverage = calculateHistoricalAverage(historicalTransactions, startDate, endDate)
        val riskLevel = determineRiskLevel(predictedAmount, historicalAverage)
        val isOverspendingRisk = predictedAmount > historicalAverage * 1.2 // 20% increase
        
        return SpendingPrediction(
            userId = userId,
            predictionDate = LocalDate.now(),
            forecastStartDate = startDate,
            forecastEndDate = endDate,
            category = category,
            subcategory = null, // Can be extracted from pattern if needed
            predictedAmount = predictedAmount,
            confidenceScore = confidence,
            predictionMethod = predictionMethod,
            riskLevel = riskLevel,
            isOverspendingRisk = isOverspendingRisk,
            createdAt = LocalDate.now()
        )
    }
    
    private fun calculateHistoricalAverage(transactions: List<com.budgetbuddy.mobile.data.model.Transaction>, startDate: LocalDate, endDate: LocalDate): Double {
        val daysInPeriod = ChronoUnit.DAYS.between(startDate, endDate) + 1
        
        // Group by month to get monthly averages
        val monthlySpending = transactions
            .groupBy { YearMonth.from(it.date) }
            .mapValues { (_, txs) -> txs.sumOf { kotlin.math.abs(it.amount) } }
        
        if (monthlySpending.isEmpty()) return 0.0
        
        val avgMonthlySpending = monthlySpending.values.average()
        
        // Scale to period length
        return avgMonthlySpending * (daysInPeriod / 30.0)
    }
    
    private fun adjustForPattern(baseAmount: Double, pattern: SpendingPattern, startDate: LocalDate, endDate: LocalDate): Double {
        val daysInPeriod = ChronoUnit.DAYS.between(startDate, endDate) + 1
        
        return when (pattern.patternType) {
            SpendingPattern.PatternType.DAILY -> {
                pattern.averageAmount * daysInPeriod * (pattern.frequency?.div(30.0) ?: 1.0)
            }
            SpendingPattern.PatternType.WEEKLY -> {
                val weeks = daysInPeriod / 7
                pattern.averageAmount * weeks
            }
            SpendingPattern.PatternType.MONTHLY -> {
                val months = daysInPeriod / 30
                pattern.averageAmount * months
            }
        }
    }
    
    private fun adjustForTrend(baseAmount: Double, trend: Trend, historicalAverage: Double): Double {
        return when (trend.direction) {
            Trend.TrendDirection.INCREASING -> {
                val growthRate = (trend.endAmount - trend.startAmount) / trend.startAmount.coerceAtLeast(1.0)
                baseAmount * (1 + growthRate * trend.strength)
            }
            Trend.TrendDirection.DECREASING -> {
                val declineRate = (trend.startAmount - trend.endAmount) / trend.startAmount.coerceAtLeast(1.0)
                baseAmount * (1 - declineRate * trend.strength)
            }
            Trend.TrendDirection.STABLE -> baseAmount
        }
    }
    
    private fun determineRiskLevel(predictedAmount: Double, historicalAverage: Double): SpendingPrediction.RiskLevel {
        if (historicalAverage == 0.0) return SpendingPrediction.RiskLevel.MEDIUM
        
        val ratio = predictedAmount / historicalAverage
        
        return when {
            ratio > 1.3 -> SpendingPrediction.RiskLevel.HIGH
            ratio > 1.1 -> SpendingPrediction.RiskLevel.MEDIUM
            else -> SpendingPrediction.RiskLevel.LOW
        }
    }
    
    suspend fun getOverspendingRisks(userId: Long): List<SpendingPrediction> {
        return spendingPredictionDao.getOverspendingRisksByUser(userId).first()
    }
}

