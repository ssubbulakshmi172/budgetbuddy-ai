package com.budgetbuddy.service;

import com.budgetbuddy.model.CategoryOverspendingAlert;
import com.budgetbuddy.model.Transaction;
import com.budgetbuddy.model.User;
import com.budgetbuddy.repository.CategoryOverspendingAlertRepository;
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
public class CategoryOverspendingService {

    private static final Logger logger = LoggerFactory.getLogger(CategoryOverspendingService.class);

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private CategoryOverspendingAlertRepository alertRepository;

    /**
     * Detect category-level overspending for current month
     */
    public List<CategoryOverspendingAlert> detectOverspending(User user) {
        logger.info("Detecting category overspending for user: {}", user.getId());

        YearMonth currentMonth = YearMonth.now();
        LocalDate monthStart = currentMonth.atDay(1);
        LocalDate monthEnd = currentMonth.atEndOfMonth();
        LocalDate today = LocalDate.now();
        int daysElapsed = today.getDayOfMonth();
        int totalDaysInMonth = currentMonth.lengthOfMonth();

        // Get current month transactions
        List<Transaction> currentMonthTransactions = transactionRepository.findAll()
            .stream()
            .filter(t -> t.getUser().getId().equals(user.getId()))
            .filter(t -> t.getAmount() != null && t.getAmount() < 0) // Only expenses
            .filter(t -> !t.getDate().isBefore(monthStart) && !t.getDate().isAfter(monthEnd))
            .collect(Collectors.toList());

        // Get historical transactions (last 3-6 months)
        LocalDate historicalStart = LocalDate.now().minusMonths(6);
        List<Transaction> historicalTransactions = transactionRepository.findAll()
            .stream()
            .filter(t -> t.getUser().getId().equals(user.getId()))
            .filter(t -> t.getAmount() != null && t.getAmount() < 0)
            .filter(t -> t.getDate().isAfter(historicalStart) && t.getDate().isBefore(monthStart))
            .collect(Collectors.toList());

        // Group by category
        Map<String, List<Transaction>> currentByCategory = currentMonthTransactions.stream()
            .filter(t -> t.getPredictedCategory() != null)
            .collect(Collectors.groupingBy(Transaction::getPredictedCategory));

        Map<String, List<Transaction>> historicalByCategory = historicalTransactions.stream()
            .filter(t -> t.getPredictedCategory() != null)
            .collect(Collectors.groupingBy(Transaction::getPredictedCategory));

        List<CategoryOverspendingAlert> alerts = new ArrayList<>();

        for (Map.Entry<String, List<Transaction>> entry : currentByCategory.entrySet()) {
            String category = entry.getKey();
            List<Transaction> currentTxs = entry.getValue();

            // Calculate current month spending
            double currentAmount = currentTxs.stream()
                .mapToDouble(t -> Math.abs(t.getAmount()))
                .sum();

            // Get historical data for this category
            List<Transaction> historicalTxs = historicalByCategory.getOrDefault(category, Collections.emptyList());

            if (historicalTxs.isEmpty()) {
                continue; // Skip if no historical data
            }

            // Calculate historical average and standard deviation
            Map<YearMonth, Double> monthlySpending = historicalTxs.stream()
                .collect(Collectors.groupingBy(
                    t -> YearMonth.from(t.getDate()),
                    Collectors.summingDouble(t -> Math.abs(t.getAmount()))
                ));

            if (monthlySpending.size() < 2) {
                continue; // Need at least 2 months of data
            }

            double historicalAvg = monthlySpending.values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

            double[] amounts = monthlySpending.values().stream()
                .mapToDouble(Double::doubleValue)
                .toArray();

            double stdDev = calculateStandardDeviation(amounts, historicalAvg);

            // Calculate percentage increase
            double percentageIncrease = historicalAvg > 0 
                ? ((currentAmount - historicalAvg) / historicalAvg) * 100 
                : 0.0;

            // Project end-of-month total
            double projectedMonthly = (currentAmount / daysElapsed) * totalDaysInMonth;

            // Determine alert level
            CategoryOverspendingAlert.AlertLevel alertLevel = determineAlertLevel(
                percentageIncrease, currentAmount, historicalAvg, stdDev
            );

            // Only create alert if it's medium risk or higher
            if (alertLevel.ordinal() >= CategoryOverspendingAlert.AlertLevel.MEDIUM.ordinal()) {
                CategoryOverspendingAlert alert = new CategoryOverspendingAlert();
                alert.setUser(user);
                alert.setCategory(category);
                alert.setAlertLevel(alertLevel);
                alert.setCurrentAmount(currentAmount);
                alert.setHistoricalAvg(historicalAvg);
                alert.setStandardDeviation(stdDev);
                alert.setPercentageIncrease(percentageIncrease);
                alert.setProjectedMonthly(projectedMonthly);
                alert.setMonth(currentMonth);
                alert.setDaysElapsed(daysElapsed);
                alert.setIsActive(true);

                alerts.add(alert);
            }
        }

        // Save alerts
        for (CategoryOverspendingAlert alert : alerts) {
            alertRepository.save(alert);
        }

        logger.info("Detected {} category overspending alerts for user {}", alerts.size(), user.getId());
        return alerts;
    }

    /**
     * Determine alert level based on percentage increase and standard deviation
     */
    private CategoryOverspendingAlert.AlertLevel determineAlertLevel(
        double percentageIncrease, double currentAmount, double historicalAvg, double stdDev
    ) {
        // Critical: > 50% increase OR exceeds 2× standard deviation
        if (percentageIncrease > 50 || currentAmount > historicalAvg + (2 * stdDev)) {
            return CategoryOverspendingAlert.AlertLevel.CRITICAL;
        }
        // High: 25-50% increase
        if (percentageIncrease > 25) {
            return CategoryOverspendingAlert.AlertLevel.HIGH;
        }
        // Medium: 10-25% increase
        if (percentageIncrease > 10) {
            return CategoryOverspendingAlert.AlertLevel.MEDIUM;
        }
        // Low: < 10% increase
        return CategoryOverspendingAlert.AlertLevel.LOW;
    }

    private double calculateStandardDeviation(double[] values, double mean) {
        double sumSquaredDiff = 0;
        for (double value : values) {
            sumSquaredDiff += Math.pow(value - mean, 2);
        }
        return Math.sqrt(sumSquaredDiff / values.length);
    }

    /**
     * Get active alerts for a user
     */
    public List<CategoryOverspendingAlert> getActiveAlerts(User user) {
        return alertRepository.findByUserAndIsActiveTrue(user);
    }

    /**
     * Get alerts by level
     */
    public List<CategoryOverspendingAlert> getAlertsByLevel(
        User user, CategoryOverspendingAlert.AlertLevel level
    ) {
        return alertRepository.findByUserAndAlertLevel(user, level);
    }
}

