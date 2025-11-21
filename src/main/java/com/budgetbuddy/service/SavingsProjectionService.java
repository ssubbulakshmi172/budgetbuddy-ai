package com.budgetbuddy.service;

import com.budgetbuddy.model.SavingsProjection;
import com.budgetbuddy.model.Transaction;
import com.budgetbuddy.model.User;
import com.budgetbuddy.repository.SavingsProjectionRepository;
import com.budgetbuddy.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SavingsProjectionService {

    private static final Logger logger = LoggerFactory.getLogger(SavingsProjectionService.class);

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private SavingsProjectionRepository projectionRepository;

    /**
     * Check if a transaction category is an investment (should be excluded from expense calculations).
     * Investments are not considered expenses because they represent asset allocation, not consumption.
     */
    private boolean isInvestmentCategory(String category) {
        if (category == null || category.trim().isEmpty()) {
            return false;
        }
        String normalized = category.toLowerCase().trim();
        return normalized.equals("investments") || normalized.startsWith("investments /");
    }

    /**
     * Check if a transaction should be excluded from expense calculations.
     * A transaction is excluded if either its categoryName or predictedCategory is an investment.
     */
    private boolean isInvestmentTransaction(Transaction transaction) {
        String category = transaction.getCategoryName() != null ? transaction.getCategoryName() : 
                         (transaction.getPredictedCategory() != null ? transaction.getPredictedCategory() : "");
        return isInvestmentCategory(category);
    }

    /**
     * Check if a transaction is income/salary (should be excluded from expense calculations).
     */
    private boolean isIncomeTransaction(Transaction transaction) {
        String category = transaction.getCategoryName() != null ? transaction.getCategoryName() : 
                         (transaction.getPredictedCategory() != null ? transaction.getPredictedCategory() : "");
        if (category == null || category.trim().isEmpty()) {
            return false;
        }
        String normalized = category.toLowerCase().trim();
        return normalized.equals("salary") || normalized.startsWith("salary /") ||
               normalized.equals("income") || normalized.startsWith("income /");
    }

    /**
     * Calculate and project year-end savings
     * INCLUDES investments as part of savings (investments are asset allocation, not expenses)
     * Formula: Income - Expenses + Investments = Total Savings/Net Worth
     */
    public SavingsProjection calculateYearEndSavings(User user) {
        logger.info("Calculating year-end savings projection for user: {} (including investments)", user.getId());

        LocalDate now = LocalDate.now();
        int currentMonth = now.getMonthValue();
        int currentYear = now.getYear();
        int remainingMonths = 12 - currentMonth;

        // Get transactions for last 6 months
        LocalDate sixMonthsAgo = LocalDate.now().minusMonths(6);
        List<Transaction> transactions = transactionRepository.findAll()
            .stream()
            .filter(t -> t.getUser().getId().equals(user.getId()))
            .filter(t -> t.getDate().isAfter(sixMonthsAgo))
            .collect(Collectors.toList());

        // Calculate monthly income average (deposits/credits, excluding investment returns)
        double monthlyIncomeAvg = calculateMonthlyIncome(transactions);

        // Calculate monthly expense average (withdrawals/debits, EXCLUDING investments)
        double monthlyExpenseAvg = calculateMonthlyExpense(transactions);

        // Calculate monthly investment average (investments are negative amounts, but we count them as savings)
        double monthlyInvestmentAvg = calculateMonthlyInvestment(transactions);

        // Calculate monthly savings rate: Income - Expenses + Investments
        // Investments are added because they're asset allocation, not expenses
        double monthlySavingsRate = monthlyIncomeAvg - monthlyExpenseAvg + monthlyInvestmentAvg;

        // Calculate current savings (sum of positive net months this year, INCLUDING investments)
        double currentSavings = calculateCurrentSavings(user, currentYear);

        // Project additional savings (including investments)
        double projectedAdditionalSavings = monthlySavingsRate * remainingMonths;

        // Adjust for current month trend
        double trendAdjustmentFactor = calculateTrendAdjustment(user, monthlyExpenseAvg);
        projectedAdditionalSavings *= trendAdjustmentFactor;

        // Calculate year-end projection (includes investments)
        double projectedYearEnd = currentSavings + projectedAdditionalSavings;
        
        // Validation: Ensure projection is reasonable
        // If we have no income data but have expenses, projection might be negative (debt)
        // That's valid, but log it for awareness
        if (monthlyIncomeAvg <= 0 && monthlyExpenseAvg > 0) {
            logger.warn("No income data found for user {}. Projection may be negative (debt).", user.getId());
        }
        
        // If savings rate is negative, ensure projection reflects that
        if (monthlySavingsRate < 0) {
            logger.info("Negative savings rate detected for user {}: ₹{} per month. Projection will show debt accumulation.", 
                user.getId(), monthlySavingsRate);
        }

        // Calculate confidence score (based on data consistency)
        double confidenceScore = calculateConfidenceScore(transactions);
        
        // Ensure confidence is between 0 and 1
        confidenceScore = Math.max(0.0, Math.min(1.0, confidenceScore));

        // Create or update projection
        SavingsProjection projection = new SavingsProjection();
        projection.setUser(user);
        projection.setCurrentMonth(currentMonth);
        projection.setCurrentSavings(currentSavings);
        projection.setMonthlyIncomeAvg(monthlyIncomeAvg);
        projection.setMonthlyExpenseAvg(monthlyExpenseAvg);
        projection.setMonthlySavingsRate(monthlySavingsRate);
        projection.setRemainingMonths(remainingMonths);
        projection.setProjectedAdditionalSavings(projectedAdditionalSavings);
        projection.setProjectedYearEnd(projectedYearEnd);
        projection.setConfidenceScore(confidenceScore);
        projection.setTrendAdjustmentFactor(trendAdjustmentFactor);
        projection.setYear(currentYear);
        
        // Store monthly investment average for reference (if SavingsProjection model has this field)
        // Note: If the model doesn't have this field, we can add it or log it separately
        logger.info("Monthly averages - Income: ₹{}, Expenses: ₹{}, Investments: ₹{}, Savings Rate: ₹{}", 
            monthlyIncomeAvg, monthlyExpenseAvg, monthlyInvestmentAvg, monthlySavingsRate);

        projectionRepository.save(projection);

        logger.info("Year-end projection for user {}: ₹{}", user.getId(), projectedYearEnd);
        return projection;
    }

    /**
     * Calculate average monthly income from deposits
     */
    private double calculateMonthlyIncome(List<Transaction> transactions) {
        Map<YearMonth, Double> monthlyIncome = transactions.stream()
            .filter(t -> t.getAmount() != null && t.getAmount() > 0) // Deposits/credits
            .collect(Collectors.groupingBy(
                t -> YearMonth.from(t.getDate()),
                Collectors.summingDouble(Transaction::getAmount)
            ));

        if (monthlyIncome.isEmpty()) {
            return 0.0;
        }

        return monthlyIncome.values().stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.0);
    }

    /**
     * Calculate average monthly expenses (EXCLUDING investments and income).
     * Investments are not expenses - they're asset allocation.
     */
    private double calculateMonthlyExpense(List<Transaction> transactions) {
        Map<YearMonth, Double> monthlyExpense = transactions.stream()
            .filter(t -> t.getAmount() != null && t.getAmount() < 0) // Withdrawals/debits
            .filter(t -> !isInvestmentTransaction(t)) // EXCLUDE investments
            .filter(t -> !isIncomeTransaction(t)) // EXCLUDE income (shouldn't be negative anyway)
            .collect(Collectors.groupingBy(
                t -> YearMonth.from(t.getDate()),
                Collectors.summingDouble(t -> Math.abs(t.getAmount()))
            ));

        if (monthlyExpense.isEmpty()) {
            return 0.0;
        }

        return monthlyExpense.values().stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.0);
    }

    /**
     * Calculate average monthly investments.
     * Investments are negative amounts (money going out), but they're asset allocation, not expenses.
     * We count them as positive contributions to savings/net worth.
     */
    private double calculateMonthlyInvestment(List<Transaction> transactions) {
        Map<YearMonth, Double> monthlyInvestment = transactions.stream()
            .filter(t -> t.getAmount() != null && t.getAmount() < 0) // Withdrawals/debits
            .filter(t -> isInvestmentTransaction(t)) // ONLY investments
            .collect(Collectors.groupingBy(
                t -> YearMonth.from(t.getDate()),
                Collectors.summingDouble(t -> Math.abs(t.getAmount())) // Convert to positive (investments are savings)
            ));

        if (monthlyInvestment.isEmpty()) {
            return 0.0;
        }

        return monthlyInvestment.values().stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.0);
    }

    /**
     * Calculate current savings (sum of positive net months this year).
     * Net = Income - Expenses + Investments
     * Investments are included as positive contributions to savings (they're asset allocation, not expenses).
     */
    private double calculateCurrentSavings(User user, int year) {
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate now = LocalDate.now();

        List<Transaction> yearTransactions = transactionRepository.findAll()
            .stream()
            .filter(t -> t.getUser().getId().equals(user.getId()))
            .filter(t -> !t.getDate().isBefore(yearStart) && !t.getDate().isAfter(now))
            .collect(Collectors.toList());

        // Group by month and calculate net (income - expense + investments)
        // Investments are negative amounts, but we add them as positive (they're savings)
        Map<YearMonth, Double> monthlyNet = new HashMap<>();

        for (Transaction t : yearTransactions) {
            YearMonth month = YearMonth.from(t.getDate());
            double amount = t.getAmount() != null ? t.getAmount() : 0.0;
            
            // For expenses (negative amounts), exclude investments from expenses
            // But add investments separately as positive contributions
            if (amount < 0 && isInvestmentTransaction(t)) {
                // Investments are negative amounts, but we count them as positive savings
                monthlyNet.put(month, monthlyNet.getOrDefault(month, 0.0) + Math.abs(amount));
            } else if (amount < 0 && !isInvestmentTransaction(t) && !isIncomeTransaction(t)) {
                // Regular expenses (negative)
                monthlyNet.put(month, monthlyNet.getOrDefault(month, 0.0) + amount);
            } else if (amount > 0) {
                // Income (positive)
                monthlyNet.put(month, monthlyNet.getOrDefault(month, 0.0) + amount);
            }
        }

        // Sum positive months (months where income + investments > expenses)
        return monthlyNet.values().stream()
            .filter(net -> net > 0)
            .mapToDouble(Double::doubleValue)
            .sum();
    }

    /**
     * Calculate trend adjustment factor based on current month spending
     */
    private double calculateTrendAdjustment(User user, double historicalAvg) {
        YearMonth currentMonth = YearMonth.now();
        LocalDate monthStart = currentMonth.atDay(1);
        LocalDate monthEnd = currentMonth.atEndOfMonth();
        LocalDate today = LocalDate.now();

        List<Transaction> currentMonthTxs = transactionRepository.findAll()
            .stream()
            .filter(t -> t.getUser().getId().equals(user.getId()))
            .filter(t -> t.getAmount() != null && t.getAmount() < 0)
            .filter(t -> !isInvestmentTransaction(t)) // EXCLUDE investments
            .filter(t -> !isIncomeTransaction(t)) // EXCLUDE income
            .filter(t -> !t.getDate().isBefore(monthStart) && !t.getDate().isAfter(today))
            .collect(Collectors.toList());

        double currentMonthSpending = currentMonthTxs.stream()
            .mapToDouble(t -> Math.abs(t.getAmount()))
            .sum();

        // Project current month total
        int daysElapsed = today.getDayOfMonth();
        int totalDays = currentMonth.lengthOfMonth();
        // Avoid division by zero
        double projectedCurrentMonth = (daysElapsed > 0 && totalDays > 0) ? 
            (currentMonthSpending / daysElapsed) * totalDays : currentMonthSpending;

        // Adjust factor: if spending > avg, reduce projection; if < avg, increase
        if (historicalAvg > 0) {
            double ratio = projectedCurrentMonth / historicalAvg;
            // Reduce by 10% for every 10% increase, increase by 5% for every 10% decrease
            if (ratio > 1.0) {
                return Math.max(0.5, 1.0 - ((ratio - 1.0) * 0.1));
            } else {
                return Math.min(1.2, 1.0 + ((1.0 - ratio) * 0.05));
            }
        }

        return 1.0;
    }

    /**
     * Calculate confidence score based on data consistency
     */
    private double calculateConfidenceScore(List<Transaction> transactions) {
        if (transactions.isEmpty()) {
            return 0.0;
        }

        // More months of data = higher confidence
        long uniqueMonths = transactions.stream()
            .map(t -> YearMonth.from(t.getDate()))
            .distinct()
            .count();

        double monthsScore = Math.min(1.0, uniqueMonths / 6.0);

        // Consistency: lower variance = higher confidence
        Map<YearMonth, Double> monthlyNet = new HashMap<>();
        for (Transaction t : transactions) {
            YearMonth month = YearMonth.from(t.getDate());
            double amount = t.getAmount() != null ? t.getAmount() : 0.0;
            monthlyNet.put(month, monthlyNet.getOrDefault(month, 0.0) + amount);
        }

        if (monthlyNet.size() < 2) {
            return monthsScore * 0.5;
        }

        double[] amounts = monthlyNet.values().stream()
            .mapToDouble(Double::doubleValue)
            .toArray();

        double mean = Arrays.stream(amounts).average().orElse(0.0);
        double variance = Arrays.stream(amounts)
            .map(a -> Math.pow(a - mean, 2))
            .average()
            .orElse(0.0);

        // Lower variance = higher confidence
        double consistencyScore = Math.max(0.0, 1.0 - (variance / (Math.abs(mean) + 1)));

        return (monthsScore * 0.6) + (consistencyScore * 0.4);
    }

    /**
     * Get latest projection for a user
     */
    public SavingsProjection getLatestProjection(User user) {
        return projectionRepository.findFirstByUserOrderByProjectionDateDesc(user)
            .orElse(null);
    }
}

