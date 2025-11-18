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
     * Calculate and project year-end savings
     */
    public SavingsProjection calculateYearEndSavings(User user) {
        logger.info("Calculating year-end savings projection for user: {}", user.getId());

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

        // Calculate monthly income average (deposits/credits)
        double monthlyIncomeAvg = calculateMonthlyIncome(transactions);

        // Calculate monthly expense average (withdrawals/debits)
        double monthlyExpenseAvg = calculateMonthlyExpense(transactions);

        // Calculate monthly savings rate
        double monthlySavingsRate = monthlyIncomeAvg - monthlyExpenseAvg;

        // Calculate current savings (sum of positive net months this year)
        double currentSavings = calculateCurrentSavings(user, currentYear);

        // Project additional savings
        double projectedAdditionalSavings = monthlySavingsRate * remainingMonths;

        // Adjust for current month trend
        double trendAdjustmentFactor = calculateTrendAdjustment(user, monthlyExpenseAvg);
        projectedAdditionalSavings *= trendAdjustmentFactor;

        // Calculate year-end projection
        double projectedYearEnd = currentSavings + projectedAdditionalSavings;

        // Calculate confidence score (based on data consistency)
        double confidenceScore = calculateConfidenceScore(transactions);

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
     * Calculate average monthly expenses
     */
    private double calculateMonthlyExpense(List<Transaction> transactions) {
        Map<YearMonth, Double> monthlyExpense = transactions.stream()
            .filter(t -> t.getAmount() != null && t.getAmount() < 0) // Withdrawals/debits
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
     * Calculate current savings (sum of positive net months this year)
     */
    private double calculateCurrentSavings(User user, int year) {
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate now = LocalDate.now();

        List<Transaction> yearTransactions = transactionRepository.findAll()
            .stream()
            .filter(t -> t.getUser().getId().equals(user.getId()))
            .filter(t -> !t.getDate().isBefore(yearStart) && !t.getDate().isAfter(now))
            .collect(Collectors.toList());

        // Group by month and calculate net (income - expense)
        Map<YearMonth, Double> monthlyNet = new HashMap<>();

        for (Transaction t : yearTransactions) {
            YearMonth month = YearMonth.from(t.getDate());
            double amount = t.getAmount() != null ? t.getAmount() : 0.0;
            monthlyNet.put(month, monthlyNet.getOrDefault(month, 0.0) + amount);
        }

        // Sum positive months
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
            .filter(t -> !t.getDate().isBefore(monthStart) && !t.getDate().isAfter(today))
            .collect(Collectors.toList());

        double currentMonthSpending = currentMonthTxs.stream()
            .mapToDouble(t -> Math.abs(t.getAmount()))
            .sum();

        // Project current month total
        int daysElapsed = today.getDayOfMonth();
        int totalDays = currentMonth.lengthOfMonth();
        double projectedCurrentMonth = (currentMonthSpending / daysElapsed) * totalDays;

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

