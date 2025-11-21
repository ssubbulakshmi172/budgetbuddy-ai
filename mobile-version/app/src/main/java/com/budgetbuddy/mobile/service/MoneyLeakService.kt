package com.budgetbuddy.mobile.service

import com.budgetbuddy.mobile.data.dao.MoneyLeakDao
import com.budgetbuddy.mobile.data.dao.TransactionDao
import com.budgetbuddy.mobile.data.model.MoneyLeak
import com.budgetbuddy.mobile.data.model.Transaction
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

class MoneyLeakService(
    private val transactionDao: TransactionDao,
    private val moneyLeakDao: MoneyLeakDao
) {
    
    companion object {
        private const val SMALL_TRANSACTION_THRESHOLD = 200.0 // ₹200
        private const val MIN_SUBSCRIPTION_OCCURRENCES = 3
        private const val MIN_COFFEE_EFFECT_TRANSACTIONS = 10
    }
    
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
    private fun isInvestmentTransaction(transaction: Transaction): Boolean {
        return isInvestmentCategory(transaction.categoryName) || 
               isInvestmentCategory(transaction.predictedCategory)
    }
    
    /**
     * Check if a transaction is income/salary
     */
    private fun isIncomeTransaction(transaction: Transaction): Boolean {
        // Positive amounts are deposits/income
        if (transaction.amount > 0) {
            return true
        }
        
        // Check category
        val category = transaction.categoryName ?: transaction.predictedCategory
        if (category != null) {
            val normalized = category.lowercase().trim()
            if (normalized == "salary" || normalized.startsWith("salary /") ||
                normalized == "income" || normalized.startsWith("income /")) {
                return true
            }
        }
        
        // Check narration for income keywords
        val narration = transaction.narration?.uppercase() ?: ""
        return narration.contains("SALARY") && 
               (narration.contains("CREDIT") || narration.contains("DEPOSIT"))
    }
    
    /**
     * Extract merchant pattern from narration (simplified version)
     */
    private fun extractMerchantPattern(narration: String?): String {
        if (narration == null || narration.trim().isEmpty()) {
            return "Unknown"
        }
        
        var cleaned = narration.trim()
        
        // Remove UPI prefix
        cleaned = cleaned.replace(Regex("(?i)^UPI[-/]"), "")
        
        // Remove @bank references
        cleaned = cleaned.replace(Regex("(?i)@[A-Z0-9]+"), "")
        
        // Remove transaction IDs (long numbers)
        cleaned = cleaned.replace(Regex("[-/]\\d{9,}"), "")
        cleaned = cleaned.replace(Regex("\\s+\\d{9,}"), "")
        
        // Remove transaction numbers with prefixes
        cleaned = cleaned.replace(Regex("(?i)[A-Z]+\\.\\d{12,}"), "")
        
        // Remove PAYTM prefixes
        cleaned = cleaned.replace(Regex("(?i)PAYTM\\.[A-Z0-9]+"), "")
        cleaned = cleaned.replace(Regex("(?i)[-/]PAYTMQR[A-Z0-9]+"), "")
        
        // Normalize separators
        cleaned = cleaned.replace(Regex("[-/]+"), " ")
        cleaned = cleaned.replace(Regex("\\s+"), " ")
        
        return cleaned.trim().takeIf { it.isNotEmpty() } ?: "Unknown"
    }
    
    /**
     * Check if transactions are recurring monthly (within ±3 days)
     */
    private fun isRecurringMonthly(transactions: List<Transaction>): Boolean {
        if (transactions.size < MIN_SUBSCRIPTION_OCCURRENCES) {
            return false
        }
        
        val sorted = transactions.sortedBy { it.date }
        val firstDate = sorted.first().date
        
        // Check if transactions occur roughly monthly (25-35 days apart)
        for (i in 1 until sorted.size) {
            val daysBetween = ChronoUnit.DAYS.between(sorted[i - 1].date, sorted[i].date)
            if (daysBetween < 25 || daysBetween > 35) {
                return false
            }
        }
        
        return true
    }
    
    /**
     * Detect top 3 money leaks
     */
    suspend fun detectMoneyLeaks(userId: Long): List<MoneyLeak> {
        val leaks = mutableListOf<MoneyLeak>()
        val sixMonthsAgo = LocalDate.now().minusMonths(6)
        val oneMonthAgo = LocalDate.now().minusMonths(1)
        
        val allTransactions = transactionDao.getTransactionsByUser(userId).first()
        val transactions = allTransactions
            .filter { it.userId == userId }
            .filter { it.amount < 0 }
            .filter { it.date.isAfter(sixMonthsAgo) }
            .filter { !isInvestmentTransaction(it) }
            .filter { !isIncomeTransaction(it) }
        
        // 1. Detect repeating subscriptions
        leaks.addAll(detectRepeatingSubscriptions(userId, transactions))
        
        // 2. Detect coffee effect (small frequent purchases)
        val recentTransactions = transactions.filter { it.date.isAfter(oneMonthAgo) }
        leaks.addAll(detectCoffeeEffect(userId, recentTransactions))
        
        // 3. Detect emotional/late-night spending
        leaks.addAll(detectEmotionalLateNightSpending(userId, recentTransactions))
        
        // Aggregate and rank leaks
        val aggregated = leaks.groupBy { 
            "${it.leakType}|${it.merchantPattern ?: "UNKNOWN"}"
        }.mapValues { (_, leakList) ->
            leakList.reduce { acc, leak ->
                acc.copy(
                    monthlyAmount = acc.monthlyAmount + leak.monthlyAmount,
                    annualAmount = acc.annualAmount + leak.annualAmount,
                    transactionCount = (acc.transactionCount ?: 0) + (leak.transactionCount ?: 0)
                )
            }
        }
        
        // Sort by annual amount and take top 3
        val ranked = aggregated.values
            .sortedByDescending { it.annualAmount }
            .take(3)
            .mapIndexed { index, leak ->
                leak.copy(rank = index + 1, isActive = true)
            }
        
        // Deactivate old leaks and save new ones
        moneyLeakDao.deactivateAll(userId)
        if (ranked.isNotEmpty()) {
            moneyLeakDao.insertAll(ranked)
        }
        
        return ranked
    }
    
    /**
     * Detect repeating subscriptions
     */
    private fun detectRepeatingSubscriptions(
        userId: Long,
        transactions: List<Transaction>
    ): List<MoneyLeak> {
        val leaks = mutableListOf<MoneyLeak>()
        
        // Group by merchant pattern and amount
        val grouped = transactions.groupBy {
            val merchant = extractMerchantPattern(it.narration)
            val amount = abs(it.amount)
            "$merchant|${String.format("%.2f", amount)}"
        }
        
        for ((key, txs) in grouped) {
            if (txs.size < MIN_SUBSCRIPTION_OCCURRENCES) {
                continue
            }
            
            if (isRecurringMonthly(txs)) {
                val parts = key.split("|")
                val merchant = parts[0]
                val amount = parts[1].toDouble()
                
                val monthlyAmount = amount
                val annualAmount = monthlyAmount * 12
                
                leaks.add(
                    MoneyLeak(
                        userId = userId,
                        leakType = MoneyLeak.LeakType.REPEATING_SUBSCRIPTION,
                        title = "Recurring Subscription: $merchant",
                        description = "You pay ₹${monthlyAmount.toInt()} monthly to $merchant. This adds up to ₹${annualAmount.toInt()} per year.",
                        merchantPattern = merchant,
                        monthlyAmount = monthlyAmount,
                        annualAmount = annualAmount,
                        transactionCount = txs.size,
                        averageTransactionAmount = amount,
                        suggestion = "Review if this subscription is still needed. Consider canceling unused services."
                    )
                )
            }
        }
        
        return leaks
    }
    
    /**
     * Detect coffee effect (small frequent purchases)
     */
    private fun detectCoffeeEffect(
        userId: Long,
        transactions: List<Transaction>
    ): List<MoneyLeak> {
        val leaks = mutableListOf<MoneyLeak>()
        
        // Filter small transactions
        val smallTxs = transactions.filter { abs(it.amount) < SMALL_TRANSACTION_THRESHOLD }
        
        // Group by merchant pattern
        val grouped = smallTxs.groupBy { extractMerchantPattern(it.narration) }
        
        for ((merchant, txs) in grouped) {
            if (txs.size < MIN_COFFEE_EFFECT_TRANSACTIONS) {
                continue
            }
            
            val monthlyTotal = txs.sumOf { abs(it.amount) }
            val annualAmount = monthlyTotal * 12
            val avgAmount = monthlyTotal / txs.size
            
            leaks.add(
                MoneyLeak(
                    userId = userId,
                    leakType = MoneyLeak.LeakType.COFFEE_EFFECT,
                    title = "Coffee Effect: $merchant",
                    description = "You make ${txs.size} small purchases (< ₹${SMALL_TRANSACTION_THRESHOLD.toInt()}) at $merchant. Monthly total: ₹${monthlyTotal.toInt()}.",
                    merchantPattern = merchant,
                    monthlyAmount = monthlyTotal,
                    annualAmount = annualAmount,
                    transactionCount = txs.size,
                    averageTransactionAmount = avgAmount,
                    suggestion = "Consider consolidating small purchases. Set a daily limit for small expenses."
                )
            )
        }
        
        return leaks
    }
    
    /**
     * Detect emotional/late-night spending
     */
    private fun detectEmotionalLateNightSpending(
        userId: Long,
        transactions: List<Transaction>
    ): List<MoneyLeak> {
        val leaks = mutableListOf<MoneyLeak>()
        
        // Filter food/dining transactions
        val foodTxs = transactions.filter {
            val category = it.categoryName ?: it.predictedCategory ?: ""
            val narration = it.narration?.lowercase() ?: ""
            category.lowercase().contains("dining") || 
            category.lowercase().contains("food") ||
            narration.contains("food") || narration.contains("restaurant") ||
            narration.contains("cafe") || narration.contains("coffee")
        }
        
        // Group by date
        val byDate = foodTxs.groupBy { it.date }
        
        var totalImpulse = 0.0
        var impulseDays = 0
        val impulseTxs = mutableListOf<Transaction>()
        
        for ((date, dayTxs) in byDate) {
            // If 3+ food/dining transactions on same day, likely impulse
            if (dayTxs.size >= 3) {
                val dayTotal = dayTxs.sumOf { abs(it.amount) }
                impulseTxs.addAll(dayTxs)
                totalImpulse += dayTotal
                impulseDays++
            }
        }
        
        if (impulseDays > 0) {
            val monthlyAmount = totalImpulse
            val annualAmount = monthlyAmount * 12
            
            leaks.add(
                MoneyLeak(
                    userId = userId,
                    leakType = MoneyLeak.LeakType.COFFEE_EFFECT, // Reuse type
                    title = "Emotional / Impulse Spending",
                    description = "Detected $impulseDays days with 3+ food/dining transactions (likely impulse/emotional spending). Total: ₹${totalImpulse.toInt()} in last month.",
                    merchantPattern = "Dining & Food",
                    monthlyAmount = monthlyAmount,
                    annualAmount = annualAmount,
                    transactionCount = impulseTxs.size,
                    averageTransactionAmount = if (impulseTxs.isNotEmpty()) totalImpulse / impulseTxs.size else 0.0,
                    suggestion = "Try meal planning and grocery shopping to reduce impulse food purchases. Set a daily food budget."
                )
            )
        }
        
        return leaks
    }
    
    /**
     * Detect regular monthly spending (expenses and investments)
     */
    suspend fun detectRegularMonthlySpending(userId: Long): List<MoneyLeak> {
        val regularSpending = mutableListOf<MoneyLeak>()
        val sixMonthsAgo = LocalDate.now().minusMonths(6)
        
        val allTransactions = transactionDao.getTransactionsByUser(userId).first()
        val transactions = allTransactions
            .filter { it.userId == userId }
            .filter { it.amount < 0 }
            .filter { it.date.isAfter(sixMonthsAgo) }
            .filter { !isIncomeTransaction(it) }
        
        // Group by category, merchant, and amount
        val grouped = transactions.groupBy {
            val merchant = extractMerchantPattern(it.narration)
            val amount = abs(it.amount)
            val category = it.categoryName ?: it.predictedCategory ?: "Unknown"
            "$category|$merchant|${String.format("%.2f", amount)}"
        }
        
        for ((key, txs) in grouped) {
            if (txs.size < MIN_SUBSCRIPTION_OCCURRENCES) {
                continue
            }
            
            if (isRecurringMonthly(txs)) {
                val parts = key.split("|")
                val category = parts[0]
                val merchant = if (parts.size > 1) parts[1] else "Unknown"
                val amount = if (parts.size > 2) parts[2].toDouble() else 
                    txs.map { abs(it.amount) }.average()
                
                val monthlyAmount = amount
                val annualAmount = monthlyAmount * 12
                val isInvestment = isInvestmentCategory(category)
                
                regularSpending.add(
                    MoneyLeak(
                        userId = userId,
                        leakType = MoneyLeak.LeakType.REPEATING_SUBSCRIPTION,
                        title = if (isInvestment) "Monthly Investment: $merchant" else "Monthly Expense: $merchant",
                        description = "You spend ₹${monthlyAmount.toInt()} monthly on $merchant ($category). This adds up to ₹${annualAmount.toInt()} per year.",
                        merchantPattern = merchant,
                        monthlyAmount = monthlyAmount,
                        annualAmount = annualAmount,
                        transactionCount = txs.size,
                        averageTransactionAmount = amount,
                        suggestion = if (isInvestment) 
                            "This is a regular investment. Consider reviewing if it aligns with your financial goals." else
                            "This is a recurring expense. Review if this subscription/service is still needed."
                    )
                )
            }
        }
        
        return regularSpending.sortedByDescending { it.monthlyAmount }
    }
    
    /**
     * Get top 3 money leaks
     */
    suspend fun getTopMoneyLeaks(userId: Long): List<MoneyLeak> {
        return moneyLeakDao.getTop3MoneyLeaks(userId)
    }
}

