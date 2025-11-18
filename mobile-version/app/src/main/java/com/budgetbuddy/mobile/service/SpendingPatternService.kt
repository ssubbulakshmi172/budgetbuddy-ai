package com.budgetbuddy.mobile.service

import com.budgetbuddy.mobile.data.dao.SpendingPatternDao
import com.budgetbuddy.mobile.data.dao.TransactionDao
import com.budgetbuddy.mobile.data.model.SpendingPattern
import com.budgetbuddy.mobile.data.model.Transaction
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class SpendingPatternService(
    private val spendingPatternDao: SpendingPatternDao,
    private val transactionDao: TransactionDao
) {
    
    /**
     * Detect spending patterns for a user
     */
    suspend fun detectPatterns(userId: Long): List<SpendingPattern> {
        val transactions = transactionDao.getTransactionsByUser(userId).first()
            .filter { it.amount < 0 } // Only expenses
            .filter { it.date.isAfter(LocalDate.now().minusMonths(6)) } // Last 6 months
        
        if (transactions.isEmpty()) {
            return emptyList()
        }
        
        val patterns = mutableListOf<SpendingPattern>()
        
        // Detect daily patterns
        patterns.addAll(detectDailyPatterns(userId, transactions))
        
        // Detect weekly patterns
        patterns.addAll(detectWeeklyPatterns(userId, transactions))
        
        // Detect monthly patterns
        patterns.addAll(detectMonthlyPatterns(userId, transactions))
        
        // Save patterns
        patterns.forEach { spendingPatternDao.insertPattern(it) }
        
        return patterns
    }
    
    private fun detectDailyPatterns(userId: Long, transactions: List<Transaction>): List<SpendingPattern> {
        val patterns = mutableListOf<SpendingPattern>()
        
        // Group by category/subcategory
        val grouped = transactions
            .filter { it.predictedCategory != null }
            .groupBy { 
                "${it.predictedCategory}|${it.predictedSubcategory ?: ""}|${extractMerchantPattern(it.narration)}"
            }
        
        grouped.forEach { (key, txs) ->
            if (txs.size < 10) return@forEach // Need at least 10 occurrences
            
            val parts = key.split("|")
            val category = parts[0]
            val subcategory = parts.getOrNull(1)?.takeIf { it.isNotEmpty() }
            val merchantPattern = parts.getOrNull(2)?.takeIf { it.isNotEmpty() }
            
            val uniqueDays = txs.map { it.date }.distinct().count()
            val avgAmount = txs.map { kotlin.math.abs(it.amount) }.average()
            
            val totalDays = ChronoUnit.DAYS.between(
                txs.minOfOrNull { it.date } ?: LocalDate.now(),
                LocalDate.now()
            )
            
            // If it occurs on more than 30% of days, it's a daily pattern
            if (totalDays > 0 && (uniqueDays * 100.0 / totalDays) > 30) {
                patterns.add(
                    SpendingPattern(
                        userId = userId,
                        patternType = SpendingPattern.PatternType.DAILY,
                        category = category,
                        subcategory = subcategory,
                        merchantPattern = merchantPattern,
                        averageAmount = avgAmount,
                        frequency = (uniqueDays * 30 / totalDays.coerceAtLeast(1)).toInt(),
                        confidenceScore = (uniqueDays * 1.0 / (totalDays / 30).coerceAtLeast(1)).coerceAtMost(1.0),
                        firstObserved = txs.minOfOrNull { it.date },
                        lastObserved = txs.maxOfOrNull { it.date },
                        isActive = true,
                        createdAt = LocalDate.now()
                    )
                )
            }
        }
        
        return patterns
    }
    
    private fun detectWeeklyPatterns(userId: Long, transactions: List<Transaction>): List<SpendingPattern> {
        val patterns = mutableListOf<SpendingPattern>()
        
        // Group by day of week and category
        val byDayOfWeek = transactions
            .filter { it.predictedCategory != null }
            .groupBy { it.date.dayOfWeek.value } // Use value (1-7) instead of DayOfWeek enum
            .mapValues { (_, txs) ->
                txs.groupBy { 
                    "${it.predictedCategory}|${it.predictedSubcategory ?: ""}|${extractMerchantPattern(it.narration)}"
                }
            }
        
        byDayOfWeek.forEach { (dayOfWeekValue, categoryGroups) ->
            categoryGroups.forEach { (key, txs) ->
                if (txs.size < 4) return@forEach // Need at least 4 occurrences
                
                val parts = key.split("|")
                val category = parts[0]
                val subcategory = parts.getOrNull(1)?.takeIf { it.isNotEmpty() }
                val merchantPattern = parts.getOrNull(2)?.takeIf { it.isNotEmpty() }
                
                val weeksWithTransaction = txs.map { 
                    it.date.minusDays((it.date.dayOfWeek.value - 1).toLong()) // Get Monday of that week
                }.distinct().count()
                
                val totalWeeks = ChronoUnit.WEEKS.between(
                    txs.minOfOrNull { it.date } ?: LocalDate.now(),
                    LocalDate.now()
                )
                
                if (totalWeeks > 0 && (weeksWithTransaction * 100.0 / totalWeeks) > 40) {
                    val avgAmount = txs.map { kotlin.math.abs(it.amount) }.average()
                    
                    patterns.add(
                        SpendingPattern(
                            userId = userId,
                            patternType = SpendingPattern.PatternType.WEEKLY,
                            category = category,
                            subcategory = subcategory,
                            merchantPattern = merchantPattern,
                            dayOfWeek = dayOfWeekValue,
                            averageAmount = avgAmount,
                            frequency = weeksWithTransaction.toInt(),
                            confidenceScore = (weeksWithTransaction * 1.0 / totalWeeks.coerceAtLeast(1)).coerceAtMost(1.0),
                            firstObserved = txs.minOfOrNull { it.date },
                            lastObserved = txs.maxOfOrNull { it.date },
                            isActive = true,
                            createdAt = LocalDate.now()
                        )
                    )
                }
            }
        }
        
        return patterns
    }
    
    private fun detectMonthlyPatterns(userId: Long, transactions: List<Transaction>): List<SpendingPattern> {
        val patterns = mutableListOf<SpendingPattern>()
        
        // Group by day of month and category
        val byDayOfMonth = transactions
            .filter { it.predictedCategory != null }
            .groupBy { it.date.dayOfMonth }
            .mapValues { (_, txs) ->
                txs.groupBy { 
                    "${it.predictedCategory}|${it.predictedSubcategory ?: ""}|${extractMerchantPattern(it.narration)}"
                }
            }
        
        byDayOfMonth.forEach { (dayOfMonth, categoryGroups) ->
            categoryGroups.forEach { (key, txs) ->
                if (txs.size < 3) return@forEach // Need at least 3 occurrences
                
                val parts = key.split("|")
                val category = parts[0]
                val subcategory = parts.getOrNull(1)?.takeIf { it.isNotEmpty() }
                val merchantPattern = parts.getOrNull(2)?.takeIf { it.isNotEmpty() }
                
                val monthsWithTransaction = txs.map { 
                    it.date.withDayOfMonth(1) 
                }.distinct().count()
                
                val totalMonths = ChronoUnit.MONTHS.between(
                    txs.minOfOrNull { it.date } ?: LocalDate.now(),
                    LocalDate.now()
                )
                
                if (totalMonths > 0 && (monthsWithTransaction * 100.0 / totalMonths) > 50) {
                    val avgAmount = txs.map { kotlin.math.abs(it.amount) }.average()
                    
                    patterns.add(
                        SpendingPattern(
                            userId = userId,
                            patternType = SpendingPattern.PatternType.MONTHLY,
                            category = category,
                            subcategory = subcategory,
                            merchantPattern = merchantPattern,
                            dayOfMonth = dayOfMonth,
                            averageAmount = avgAmount,
                            frequency = monthsWithTransaction.toInt(),
                            confidenceScore = (monthsWithTransaction * 1.0 / totalMonths.coerceAtLeast(1)).coerceAtMost(1.0),
                            firstObserved = txs.minOfOrNull { it.date },
                            lastObserved = txs.maxOfOrNull { it.date },
                            isActive = true,
                            createdAt = LocalDate.now()
                        )
                    )
                }
            }
        }
        
        return patterns
    }
    
    private fun extractMerchantPattern(narration: String?): String {
        if (narration.isNullOrEmpty()) return "UNKNOWN"
        
        val cleaned = narration.uppercase()
            .replace(Regex("UPI[^\\s]*"), "")
            .replace(Regex("\\d+"), "")
            .replace(Regex("[^A-Z\\s]"), " ")
            .trim()
        
        val words = cleaned.split("\\s+".toRegex())
        return if (words.isNotEmpty()) {
            words[0] + if (words.size > 1) " ${words[1]}" else ""
        } else "UNKNOWN"
    }
    
    suspend fun getActivePatterns(userId: Long): List<SpendingPattern> {
        return spendingPatternDao.getActivePatternsByUser(userId).first()
    }
    
    suspend fun refreshPatterns(userId: Long) {
        // Deactivate old patterns
        spendingPatternDao.deactivateAllPatternsForUser(userId)
        
        // Detect new patterns
        detectPatterns(userId)
    }
}

