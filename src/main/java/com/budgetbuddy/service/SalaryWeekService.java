package com.budgetbuddy.service;

import com.budgetbuddy.model.SalaryWeekAnalysis;
import com.budgetbuddy.model.Transaction;
import com.budgetbuddy.model.User;
import com.budgetbuddy.repository.SalaryWeekAnalysisRepository;
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
public class SalaryWeekService {

    private static final Logger logger = LoggerFactory.getLogger(SalaryWeekService.class);

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private SalaryWeekAnalysisRepository salaryWeekAnalysisRepository;

    /**
     * Detect salary and analyze salary week spending
     */
    public SalaryWeekAnalysis analyzeSalaryWeek(User user) {
        logger.info("Analyzing salary week spending for user: {}", user.getId());

        // Detect salary
        SalaryDetectionResult salaryResult = detectSalary(user);

        if (salaryResult == null || salaryResult.getSalaryDate() == null) {
            logger.info("No salary detected for user {}", user.getId());
            return null;
        }

        LocalDate salaryDate = salaryResult.getSalaryDate();
        double salaryAmount = salaryResult.getSalaryAmount();
        LocalDate salaryWeekStart = salaryDate;
        LocalDate salaryWeekEnd = salaryDate.plusDays(6);

        // Get transactions in salary week
        List<Transaction> salaryWeekTxs = transactionRepository.findAll()
            .stream()
            .filter(t -> t.getUser().getId().equals(user.getId()))
            .filter(t -> t.getAmount() != null && t.getAmount() < 0)
            .filter(t -> !t.getDate().isBefore(salaryWeekStart) && !t.getDate().isAfter(salaryWeekEnd))
            .collect(Collectors.toList());

        double salaryWeekSpending = salaryWeekTxs.stream()
            .mapToDouble(t -> Math.abs(t.getAmount()))
            .sum();

        // Get non-salary weeks average (last 3 months, excluding salary weeks)
        double nonSalaryWeekAvg = calculateNonSalaryWeekAverage(user, salaryDate);

        if (nonSalaryWeekAvg == 0) {
            return null;
        }

        double ratio = salaryWeekSpending / nonSalaryWeekAvg;
        double extraSpending = salaryWeekSpending - nonSalaryWeekAvg;
        boolean isAnomaly = ratio > 1.5; // Flag if 50%+ higher

        // Create analysis
        SalaryWeekAnalysis analysis = new SalaryWeekAnalysis();
        analysis.setUser(user);
        analysis.setSalaryDate(salaryDate);
        analysis.setSalaryAmount(salaryAmount);
        analysis.setSalaryWeekStart(salaryWeekStart);
        analysis.setSalaryWeekEnd(salaryWeekEnd);
        analysis.setSalaryWeekSpending(salaryWeekSpending);
        analysis.setNonSalaryWeekAvg(nonSalaryWeekAvg);
        analysis.setRatio(ratio);
        analysis.setExtraSpending(extraSpending);
        analysis.setMonth(YearMonth.from(salaryDate));
        analysis.setIsAnomaly(isAnomaly);
        analysis.setConfidenceScore(salaryResult.getConfidence());

        salaryWeekAnalysisRepository.save(analysis);

        logger.info("Salary week analysis for user {}: ratio={}, extra={}", 
            user.getId(), ratio, extraSpending);
        return analysis;
    }

    /**
     * Detect salary from large recurring deposits
     */
    private SalaryDetectionResult detectSalary(User user) {
        LocalDate sixMonthsAgo = LocalDate.now().minusMonths(6);
        List<Transaction> deposits = transactionRepository.findAll()
            .stream()
            .filter(t -> t.getUser().getId().equals(user.getId()))
            .filter(t -> t.getAmount() != null && t.getAmount() > 0)
            .filter(t -> t.getDate().isAfter(sixMonthsAgo))
            .collect(Collectors.toList());

        if (deposits.isEmpty()) {
            return null;
        }

        // Group by amount (likely salary is same amount each month)
        Map<String, List<Transaction>> byAmount = deposits.stream()
            .collect(Collectors.groupingBy(t -> String.format("%.0f", t.getAmount())));

        // Find recurring deposits (same amount, monthly)
        for (Map.Entry<String, List<Transaction>> entry : byAmount.entrySet()) {
            List<Transaction> txs = entry.getValue();
            
            if (txs.size() >= 3 && isRecurringMonthly(txs)) {
                // This is likely salary
                Transaction latest = txs.stream()
                    .max(Comparator.comparing(Transaction::getDate))
                    .orElse(null);

                if (latest != null) {
                    double confidence = calculateConfidence(txs);
                    return new SalaryDetectionResult(
                        latest.getDate(),
                        latest.getAmount(),
                        confidence
                    );
                }
            }
        }

        // Fallback: find largest recent deposit
        Transaction largestRecent = deposits.stream()
            .max(Comparator.comparing(Transaction::getDate))
            .orElse(null);

        if (largestRecent != null && largestRecent.getAmount() > 10000) {
            return new SalaryDetectionResult(
                largestRecent.getDate(),
                largestRecent.getAmount(),
                0.5 // Lower confidence
            );
        }

        return null;
    }

    /**
     * Check if transactions recur monthly
     */
    private boolean isRecurringMonthly(List<Transaction> transactions) {
        if (transactions.size() < 3) {
            return false;
        }

        transactions.sort(Comparator.comparing(Transaction::getDate));

        for (int i = 1; i < transactions.size(); i++) {
            long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(
                transactions.get(i - 1).getDate(),
                transactions.get(i).getDate()
            );

            // Should be approximately 30 days (±5 days)
            if (daysBetween < 25 || daysBetween > 35) {
                return false;
            }
        }

        return true;
    }

    /**
     * Calculate confidence in salary detection
     */
    private double calculateConfidence(List<Transaction> transactions) {
        // More occurrences = higher confidence
        double occurrenceScore = Math.min(1.0, transactions.size() / 6.0);
        
        // Consistency in amount = higher confidence
        double avgAmount = transactions.stream()
            .mapToDouble(Transaction::getAmount)
            .average()
            .orElse(0.0);

        double variance = transactions.stream()
            .mapToDouble(t -> Math.pow(t.getAmount() - avgAmount, 2))
            .average()
            .orElse(0.0);

        double consistencyScore = Math.max(0.0, 1.0 - (variance / (avgAmount * avgAmount + 1)));

        return (occurrenceScore * 0.6) + (consistencyScore * 0.4);
    }

    /**
     * Calculate average spending in non-salary weeks
     */
    private double calculateNonSalaryWeekAverage(User user, LocalDate salaryDate) {
        LocalDate threeMonthsAgo = LocalDate.now().minusMonths(3);
        
        List<Transaction> transactions = transactionRepository.findAll()
            .stream()
            .filter(t -> t.getUser().getId().equals(user.getId()))
            .filter(t -> t.getAmount() != null && t.getAmount() < 0)
            .filter(t -> t.getDate().isAfter(threeMonthsAgo))
            .collect(Collectors.toList());

        // Group by week
        Map<LocalDate, List<Transaction>> byWeek = transactions.stream()
            .collect(Collectors.groupingBy(t -> {
                // Get Monday of the week
                LocalDate date = t.getDate();
                int dayOfWeek = date.getDayOfWeek().getValue();
                return date.minusDays(dayOfWeek - 1);
            }));

        // Exclude salary week
        LocalDate salaryWeekStart = salaryDate;
        List<Double> weeklyTotals = byWeek.entrySet().stream()
            .filter(e -> !isInSalaryWeek(e.getKey(), salaryWeekStart))
            .map(e -> e.getValue().stream()
                .mapToDouble(t -> Math.abs(t.getAmount()))
                .sum())
            .collect(Collectors.toList());

        if (weeklyTotals.isEmpty()) {
            return 0.0;
        }

        return weeklyTotals.stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.0);
    }

    /**
     * Check if a week start date is in salary week
     */
    private boolean isInSalaryWeek(LocalDate weekStart, LocalDate salaryDate) {
        LocalDate salaryWeekStart = salaryDate;
        LocalDate salaryWeekEnd = salaryDate.plusDays(6);
        return !weekStart.isBefore(salaryWeekStart) && !weekStart.isAfter(salaryWeekEnd);
    }

    /**
     * Get latest salary week analysis
     */
    public List<SalaryWeekAnalysis> getAnomalies(User user) {
        return salaryWeekAnalysisRepository.findByUserAndIsAnomalyTrue(user);
    }

    /**
     * Helper class for salary detection result
     */
    private static class SalaryDetectionResult {
        private final LocalDate salaryDate;
        private final double salaryAmount;
        private final double confidence;

        public SalaryDetectionResult(LocalDate salaryDate, double salaryAmount, double confidence) {
            this.salaryDate = salaryDate;
            this.salaryAmount = salaryAmount;
            this.confidence = confidence;
        }

        public LocalDate getSalaryDate() { return salaryDate; }
        public double getSalaryAmount() { return salaryAmount; }
        public double getConfidence() { return confidence; }
    }
}

