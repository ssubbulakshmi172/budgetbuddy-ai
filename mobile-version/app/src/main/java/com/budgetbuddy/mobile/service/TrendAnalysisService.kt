package com.budgetbuddy.mobile.service

import com.budgetbuddy.mobile.data.dao.TransactionDao
import com.budgetbuddy.mobile.data.model.Trend
import com.budgetbuddy.mobile.data.model.TrendAnalysisResult
import com.budgetbuddy.mobile.data.model.SpendingSpike
import com.budgetbuddy.mobile.data.model.SpendingDip
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth

class TrendAnalysisService(
    private val transactionDao: TransactionDao
) {
    
    suspend fun analyzeTrends(userId: Long): TrendAnalysisResult {
        val transactions = transactionDao.getTransactionsByUser(userId).first()
            .filter { it.amount < 0 } // Only expenses
            .filter { it.date.isAfter(LocalDate.now().minusMonths(6)) } // Last 6 months
        
        if (transactions.isEmpty()) {
            return TrendAnalysisResult(emptyList(), emptyList(), emptyList())
        }
        
        val trends = mutableListOf<Trend>()
        val spikes = mutableListOf<SpendingSpike>()
        val dips = mutableListOf<SpendingDip>()
        
        // Analyze by category
        val byCategory = transactions
            .filter { it.predictedCategory != null }
            .groupBy { it.predictedCategory ?: "Uncategorized" }
        
        byCategory.forEach { (category, categoryTransactions) ->
            // Group by month
            val monthlySpending = categoryTransactions
                .groupBy { YearMonth.from(it.date) }
                .mapValues { (_, txs) -> txs.sumOf { kotlin.math.abs(it.amount) } }
            
            if (monthlySpending.size < 3) return@forEach // Need at least 3 months
            
            val sortedMonths = monthlySpending.keys.sorted()
            val amounts = sortedMonths.map { monthlySpending[it]!! }.toDoubleArray()
            
            // Calculate trend
            val trend = calculateTrend(category, amounts, sortedMonths)
            if (trend != null) {
                trends.add(trend)
            }
            
            // Detect spikes
            spikes.addAll(detectSpikes(category, monthlySpending, sortedMonths))
            
            // Detect dips
            dips.addAll(detectDips(category, monthlySpending, sortedMonths))
        }
        
        return TrendAnalysisResult(trends, spikes, dips)
    }
    
    private fun calculateTrend(category: String, amounts: DoubleArray, months: List<YearMonth>): Trend? {
        if (amounts.size < 3) return null
        
        var sumX = 0.0
        var sumY = 0.0
        var sumXY = 0.0
        var sumX2 = 0.0
        val n = amounts.size
        
        for (i in amounts.indices) {
            sumX += i
            sumY += amounts[i]
            sumXY += i * amounts[i]
            sumX2 += i * i
        }
        
        val slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX)
        val avgAmount = sumY / n
        
        val direction = when {
            slope > avgAmount * 0.05 -> Trend.TrendDirection.INCREASING
            slope < -avgAmount * 0.05 -> Trend.TrendDirection.DECREASING
            else -> Trend.TrendDirection.STABLE
        }
        
        val strength = (kotlin.math.abs(slope) / avgAmount.coerceAtLeast(1.0)).coerceAtMost(1.0)
        
        return Trend(category, direction, strength, amounts[0], amounts[n - 1])
    }
    
    private fun detectSpikes(category: String, monthlySpending: Map<YearMonth, Double>, sortedMonths: List<YearMonth>): List<SpendingSpike> {
        val spikes = mutableListOf<SpendingSpike>()
        
        if (sortedMonths.size < 3) return spikes
        
        val amounts = sortedMonths.map { monthlySpending[it]!! }.toDoubleArray()
        val mean = amounts.average()
        val stdDev = calculateStandardDeviation(amounts, mean)
        
        amounts.forEachIndexed { i, amount ->
            if (amount > mean + 2 * stdDev && amount > mean * 1.5) {
                val month = sortedMonths[i]
                val increase = amount - mean
                val percentageIncrease = (increase / mean) * 100
                
                spikes.add(SpendingSpike(category, month, amount, increase, percentageIncrease))
            }
        }
        
        return spikes
    }
    
    private fun detectDips(category: String, monthlySpending: Map<YearMonth, Double>, sortedMonths: List<YearMonth>): List<SpendingDip> {
        val dips = mutableListOf<SpendingDip>()
        
        if (sortedMonths.size < 3) return dips
        
        val amounts = sortedMonths.map { monthlySpending[it]!! }.toDoubleArray()
        val mean = amounts.average()
        val stdDev = calculateStandardDeviation(amounts, mean)
        
        amounts.forEachIndexed { i, amount ->
            if (amount < mean - 2 * stdDev && amount < mean * 0.5) {
                val month = sortedMonths[i]
                val decrease = mean - amount
                val percentageDecrease = (decrease / mean) * 100
                
                dips.add(SpendingDip(category, month, amount, decrease, percentageDecrease))
            }
        }
        
        return dips
    }
    
    private fun calculateStandardDeviation(values: DoubleArray, mean: Double): Double {
        val sumSquaredDiff = values.sumOf { value -> 
            val diff = value - mean
            diff * diff
        }
        return kotlin.math.sqrt(sumSquaredDiff / values.size)
    }
}

