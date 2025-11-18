package com.budgetbuddy.service;

import com.budgetbuddy.model.Transaction;
import com.budgetbuddy.model.User;
import com.budgetbuddy.model.WeekendOverspending;
import com.budgetbuddy.repository.TransactionRepository;
import com.budgetbuddy.repository.WeekendOverspendingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class WeekendOverspendingService {

    private static final Logger logger = LoggerFactory.getLogger(WeekendOverspendingService.class);

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private WeekendOverspendingRepository weekendOverspendingRepository;

    /**
     * Detect weekend overspending patterns
     */
    public List<WeekendOverspending> detectWeekendOverspending(User user) {
        logger.info("Detecting weekend overspending for user: {}", user.getId());

        YearMonth currentMonth = YearMonth.now();
        LocalDate monthStart = currentMonth.atDay(1);
        LocalDate monthEnd = currentMonth.atEndOfMonth();

        // Get current month transactions
        List<Transaction> transactions = transactionRepository.findAll()
            .stream()
            .filter(t -> t.getUser().getId().equals(user.getId()))
            .filter(t -> t.getAmount() != null && t.getAmount() < 0)
            .filter(t -> !t.getDate().isBefore(monthStart) && !t.getDate().isAfter(monthEnd))
            .collect(Collectors.toList());

        if (transactions.isEmpty()) {
            return Collections.emptyList();
        }

        // Group by category
        Map<String, List<Transaction>> byCategory = transactions.stream()
            .filter(t -> t.getPredictedCategory() != null)
            .collect(Collectors.groupingBy(Transaction::getPredictedCategory));

        List<WeekendOverspending> results = new ArrayList<>();

        for (Map.Entry<String, List<Transaction>> entry : byCategory.entrySet()) {
            String category = entry.getKey();
            List<Transaction> categoryTxs = entry.getValue();

            // Separate weekend (Sat, Sun) and weekday (Mon-Fri)
            List<Transaction> weekendTxs = categoryTxs.stream()
                .filter(t -> {
                    DayOfWeek day = t.getDate().getDayOfWeek();
                    return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
                })
                .collect(Collectors.toList());

            List<Transaction> weekdayTxs = categoryTxs.stream()
                .filter(t -> {
                    DayOfWeek day = t.getDate().getDayOfWeek();
                    return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
                })
                .collect(Collectors.toList());

            if (weekendTxs.isEmpty() || weekdayTxs.isEmpty()) {
                continue;
            }

            // Calculate averages
            double weekendAvg = weekendTxs.stream()
                .mapToDouble(t -> Math.abs(t.getAmount()))
                .average()
                .orElse(0.0);

            double weekdayAvg = weekdayTxs.stream()
                .mapToDouble(t -> Math.abs(t.getAmount()))
                .average()
                .orElse(0.0);

            if (weekdayAvg == 0) {
                continue;
            }

            double ratio = weekendAvg / weekdayAvg;
            double percentageIncrease = (ratio - 1.0) * 100;

            // Only flag if weekend spending is 30%+ higher than weekday
            if (ratio > 1.3) {
                WeekendOverspending overspending = new WeekendOverspending();
                overspending.setUser(user);
                overspending.setCategory(category);
                overspending.setWeekendAvg(weekendAvg);
                overspending.setWeekdayAvg(weekdayAvg);
                overspending.setRatio(ratio);
                overspending.setPercentageIncrease(percentageIncrease);
                overspending.setMonth(currentMonth);
                overspending.setTrend(determineTrend(user, category));
                overspending.setAlertLevel(determineAlertLevel(ratio));
                overspending.setIsActive(true);

                results.add(overspending);
            }
        }

        // Save results
        for (WeekendOverspending result : results) {
            weekendOverspendingRepository.save(result);
        }

        logger.info("Detected {} weekend overspending patterns for user {}", results.size(), user.getId());
        return results;
    }

    /**
     * Determine trend (increasing, decreasing, stable)
     */
    private WeekendOverspending.Trend determineTrend(User user, String category) {
        // Get last 3 months of weekend spending
        LocalDate threeMonthsAgo = LocalDate.now().minusMonths(3);
        List<Transaction> transactions = transactionRepository.findAll()
            .stream()
            .filter(t -> t.getUser().getId().equals(user.getId()))
            .filter(t -> t.getPredictedCategory() != null && t.getPredictedCategory().equals(category))
            .filter(t -> t.getAmount() != null && t.getAmount() < 0)
            .filter(t -> t.getDate().isAfter(threeMonthsAgo))
            .collect(Collectors.toList());

        Map<YearMonth, Double> monthlyWeekendSpending = transactions.stream()
            .filter(t -> {
                DayOfWeek day = t.getDate().getDayOfWeek();
                return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
            })
            .collect(Collectors.groupingBy(
                t -> YearMonth.from(t.getDate()),
                Collectors.summingDouble(t -> Math.abs(t.getAmount()))
            ));

        if (monthlyWeekendSpending.size() < 2) {
            return WeekendOverspending.Trend.STABLE;
        }

        List<Double> amounts = monthlyWeekendSpending.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(Map.Entry::getValue)
            .collect(Collectors.toList());

        double first = amounts.get(0);
        double last = amounts.get(amounts.size() - 1);

        if (last > first * 1.1) {
            return WeekendOverspending.Trend.INCREASING;
        } else if (last < first * 0.9) {
            return WeekendOverspending.Trend.DECREASING;
        }

        return WeekendOverspending.Trend.STABLE;
    }

    /**
     * Determine alert level based on ratio
     */
    private WeekendOverspending.AlertLevel determineAlertLevel(double ratio) {
        if (ratio > 1.5) {
            return WeekendOverspending.AlertLevel.HIGH;
        } else if (ratio > 1.3) {
            return WeekendOverspending.AlertLevel.MEDIUM;
        }
        return WeekendOverspending.AlertLevel.LOW;
    }

    /**
     * Get active weekend overspending alerts
     */
    public List<WeekendOverspending> getActiveAlerts(User user) {
        return weekendOverspendingRepository.findByUserAndIsActiveTrue(user);
    }
}

