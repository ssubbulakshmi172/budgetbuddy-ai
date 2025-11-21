package com.budgetbuddy.mobile.service

import com.budgetbuddy.mobile.data.dao.TransactionDao
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

class FinancialAnalyticsService(
    private val transactionDao: TransactionDao
) {
    
    /**
     * Check if a transaction is an investment
     */
    private fun isInvestmentTransaction(transaction: com.budgetbuddy.mobile.data.model.Transaction): Boolean {
        val category = transaction.categoryName ?: transaction.predictedCategory
        if (category == null || category.trim().isEmpty()) {
            return false
        }
        val normalized = category.lowercase().trim()
        return normalized == "investments" || normalized.startsWith("investments /")
    }
    
    /**
     * Analyze Grocery vs Eating-Out pattern
     */
    suspend fun analyzeGroceryVsEatingOut(userId: Long): Map<String, Any> {
        val threeMonthsAgo = LocalDate.now().minusMonths(3)
        val allTransactions = transactionDao.getTransactionsByUser(userId).first()
        val transactions = allTransactions
            .filter { it.userId == userId }
            .filter { it.amount < 0 }
            .filter { it.date.isAfter(threeMonthsAgo) }
        
        // Group by month and category
        val monthlyByCategory = mutableMapOf<YearMonth, MutableMap<String, Double>>()
        
        for (tx in transactions) {
            val category = tx.categoryName ?: tx.predictedCategory ?: "Unknown"
            val month = YearMonth.from(tx.date)
            
            monthlyByCategory.putIfAbsent(month, mutableMapOf())
            val categoryMap = monthlyByCategory[month]!!
            
            when {
                category.lowercase().contains("grocery") || category.lowercase().contains("groceries") -> {
                    categoryMap["grocery"] = categoryMap.getOrDefault("grocery", 0.0) + abs(tx.amount)
                }
                category.lowercase().contains("dining") || category.lowercase().contains("food") -> {
                    categoryMap["eating_out"] = categoryMap.getOrDefault("eating_out", 0.0) + abs(tx.amount)
                }
            }
        }
        
        // Calculate weekly splits
        val weeklySplits = mutableListOf<Map<String, Any>>()
        var totalGrocery = 0.0
        var totalEatingOut = 0.0
        
        for ((month, categoryMap) in monthlyByCategory) {
            val grocery = categoryMap.getOrDefault("grocery", 0.0)
            val eatingOut = categoryMap.getOrDefault("eating_out", 0.0)
            val total = grocery + eatingOut
            
            if (total > 0) {
                val groceryPercent = (grocery / total) * 100
                val eatingOutPercent = (eatingOut / total) * 100
                
                weeklySplits.add(mapOf(
                    "month" to month.toString(),
                    "grocery_percent" to groceryPercent.toInt(),
                    "eating_out_percent" to eatingOutPercent.toInt(),
                    "grocery_amount" to grocery.toInt(),
                    "eating_out_amount" to eatingOut.toInt(),
                    "unhealthy_shift" to (eatingOutPercent > 40)
                ))
                
                totalGrocery += grocery
                totalEatingOut += eatingOut
            }
        }
        
        val total = totalGrocery + totalEatingOut
        val overallGroceryPercent = if (total > 0) ((totalGrocery / total) * 100).toInt() else 0
        val overallEatingOutPercent = if (total > 0) ((totalEatingOut / total) * 100).toInt() else 0
        
        return mapOf(
            "weekly_splits" to weeklySplits,
            "total_grocery" to totalGrocery.toInt(),
            "total_eating_out" to totalEatingOut.toInt(),
            "overall_grocery_percent" to overallGroceryPercent,
            "overall_eating_out_percent" to overallEatingOutPercent,
            "unhealthy_shift_detected" to (totalEatingOut > totalGrocery * 0.67),
            "improvement_suggestion" to if (totalEatingOut > totalGrocery * 0.67) 
                "Eating out exceeds 40% of food budget. Try meal planning and grocery shopping to reduce by 20%." else
                "Food spending is balanced. Maintain current grocery-to-dining ratio."
        )
    }
    
    /**
     * Track investments
     */
    suspend fun trackInvestments(userId: Long): Map<String, Any> {
        val allTransactions = transactionDao.getTransactionsByUser(userId).first()
        val transactions = allTransactions
            .filter { it.userId == userId }
            .filter { it.amount < 0 }
            .filter { isInvestmentTransaction(it) }
        
        // Group by month
        val byMonth = transactions.groupBy { YearMonth.from(it.date) }
        
        val monthlyTotals = mutableListOf<Map<String, Any>>()
        var cumulativeTotal = 0.0
        
        for ((month, monthTxs) in byMonth.entries.sortedBy { it.key }) {
            val monthlyTotal = monthTxs.sumOf { abs(it.amount) }
            cumulativeTotal += monthlyTotal
            
            monthlyTotals.add(mapOf(
                "month" to month.toString(),
                "monthly_total" to monthlyTotal.toInt(),
                "cumulative_total" to cumulativeTotal.toInt(),
                "transaction_count" to monthTxs.size
            ))
        }
        
        return mapOf(
            "monthly_totals" to monthlyTotals,
            "total_invested" to cumulativeTotal.toInt(),
            "average_monthly" to if (monthlyTotals.isNotEmpty()) 
                (cumulativeTotal / monthlyTotals.size).toInt() else 0,
            "total_transactions" to transactions.size
        )
    }
    
    /**
     * Analyze subscriptions
     */
    suspend fun analyzeSubscriptions(userId: Long): Map<String, Any> {
        val sixMonthsAgo = LocalDate.now().minusMonths(6)
        val allTransactions = transactionDao.getTransactionsByUser(userId).first()
        val transactions = allTransactions
            .filter { it.userId == userId }
            .filter { it.amount < 0 }
            .filter { it.date.isAfter(sixMonthsAgo) }
            .filter { !isInvestmentTransaction(it) }
        
        // Group by merchant pattern and amount (for recurring payments)
        val grouped = transactions.groupBy {
            val merchant = it.narration?.take(50) ?: "Unknown"
            val amount = abs(it.amount)
            "$merchant|${String.format("%.2f", amount)}"
        }
        
        val subscriptions = mutableListOf<Map<String, Any>>()
        var totalMonthly = 0.0
        
        for ((key, txs) in grouped) {
            if (txs.size >= 3) { // At least 3 occurrences
                val parts = key.split("|")
                val merchant = parts[0]
                val amount = parts[1].toDouble()
                
                // Check if recurring (simplified - check if transactions are roughly monthly)
                val sorted = txs.sortedBy { it.date }
                val isRecurring = sorted.size >= 3 && 
                    sorted.zipWithNext().all { (a, b) ->
                        val days = java.time.temporal.ChronoUnit.DAYS.between(a.date, b.date)
                        days in 25..35
                    }
                
                if (isRecurring) {
                    subscriptions.add(mapOf(
                        "merchant" to merchant,
                        "monthly_amount" to amount.toInt(),
                        "annual_amount" to (amount * 12).toInt(),
                        "transaction_count" to txs.size
                    ))
                    totalMonthly += amount
                }
            }
        }
        
        return mapOf(
            "subscriptions" to subscriptions,
            "total_monthly" to totalMonthly.toInt(),
            "total_annual" to (totalMonthly * 12).toInt(),
            "count" to subscriptions.size
        )
    }
}

