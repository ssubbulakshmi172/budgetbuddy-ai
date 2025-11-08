package com.budgetbuddy.service;

import com.budgetbuddy.model.SpendingPattern;
import com.budgetbuddy.model.Transaction;
import com.budgetbuddy.model.User;
import com.budgetbuddy.repository.SpendingPatternRepository;
import com.budgetbuddy.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SpendingPatternService {

    private static final Logger logger = LoggerFactory.getLogger(SpendingPatternService.class);

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private SpendingPatternRepository spendingPatternRepository;

    /**
     * Analyze transactions and detect spending patterns for a user.
     * Detects daily, weekly, and monthly routines.
     */
    public List<SpendingPattern> detectPatterns(User user) {
        logger.info("Detecting spending patterns for user: {}", user.getId());
        
        List<Transaction> transactions = transactionRepository.findAll()
            .stream()
            .filter(t -> t.getUser().getId().equals(user.getId()))
            .filter(t -> t.getAmount() != null && t.getAmount() < 0) // Only expenses
            .filter(t -> t.getDate().isAfter(LocalDate.now().minusMonths(6))) // Last 6 months
            .collect(Collectors.toList());

        if (transactions.isEmpty()) {
            logger.info("No transactions found for pattern detection");
            return Collections.emptyList();
        }

        List<SpendingPattern> patterns = new ArrayList<>();

        // Detect daily patterns (e.g., coffee every evening)
        patterns.addAll(detectDailyPatterns(user, transactions));

        // Detect weekly patterns (e.g., weekend food orders)
        patterns.addAll(detectWeeklyPatterns(user, transactions));

        // Detect monthly patterns (e.g., utility bills on 1st of month)
        patterns.addAll(detectMonthlyPatterns(user, transactions));

        // Save patterns
        for (SpendingPattern pattern : patterns) {
            spendingPatternRepository.save(pattern);
        }

        logger.info("Detected {} spending patterns for user {}", patterns.size(), user.getId());
        return patterns;
    }

    /**
     * Detect daily patterns (same merchant/category at similar times)
     */
    private List<SpendingPattern> detectDailyPatterns(User user, List<Transaction> transactions) {
        List<SpendingPattern> patterns = new ArrayList<>();

        // Group by category/subcategory and merchant pattern
        Map<String, List<Transaction>> grouped = transactions.stream()
            .filter(t -> t.getPredictedCategory() != null)
            .collect(Collectors.groupingBy(t -> {
                String key = (t.getPredictedCategory() != null ? t.getPredictedCategory() : "Unknown") + "|" +
                             (t.getPredictedSubcategory() != null ? t.getPredictedSubcategory() : "") + "|" +
                             extractMerchantPattern(t.getNarration());
                return key;
            }));

        for (Map.Entry<String, List<Transaction>> entry : grouped.entrySet()) {
            List<Transaction> txs = entry.getValue();
            if (txs.size() < 10) continue; // Need at least 10 occurrences

            String[] parts = entry.getKey().split("\\|");
            String category = parts[0];
            String subcategory = parts.length > 1 ? parts[1] : null;
            String merchantPattern = parts.length > 2 ? parts[2] : null;

            // Check if it's a daily pattern (occurs most days)
            long uniqueDays = txs.stream()
                .map(Transaction::getDate)
                .distinct()
                .count();

            double avgAmount = txs.stream()
                .mapToDouble(t -> Math.abs(t.getAmount()))
                .average()
                .orElse(0.0);

            // If it occurs on more than 30% of days, it's a daily pattern
            long totalDays = ChronoUnit.DAYS.between(
                txs.stream().map(Transaction::getDate).min(LocalDate::compareTo).orElse(LocalDate.now()),
                LocalDate.now()
            );
            
            if (totalDays > 0 && (uniqueDays * 100.0 / totalDays) > 30) {
                SpendingPattern pattern = new SpendingPattern();
                pattern.setUser(user);
                pattern.setPatternType(SpendingPattern.PatternType.DAILY);
                pattern.setCategory(category);
                pattern.setSubcategory(subcategory);
                pattern.setMerchantPattern(merchantPattern);
                pattern.setAverageAmount(avgAmount);
                pattern.setFrequency((int) (uniqueDays * 30 / Math.max(totalDays, 1))); // Frequency per month
                pattern.setConfidenceScore(Math.min(uniqueDays * 1.0 / Math.max(totalDays / 30, 1), 1.0));
                pattern.setFirstObserved(txs.stream().map(Transaction::getDate).min(LocalDate::compareTo).orElse(LocalDate.now()));
                pattern.setLastObserved(txs.stream().map(Transaction::getDate).max(LocalDate::compareTo).orElse(LocalDate.now()));
                pattern.setIsActive(true);

                patterns.add(pattern);
            }
        }

        return patterns;
    }

    /**
     * Detect weekly patterns (e.g., weekend food orders, Friday coffee)
     */
    private List<SpendingPattern> detectWeeklyPatterns(User user, List<Transaction> transactions) {
        List<SpendingPattern> patterns = new ArrayList<>();

        // Group by day of week and category
        Map<DayOfWeek, Map<String, List<Transaction>>> byDayOfWeek = transactions.stream()
            .filter(t -> t.getPredictedCategory() != null)
            .collect(Collectors.groupingBy(
                t -> t.getDate().getDayOfWeek(),
                Collectors.groupingBy(t -> 
                    (t.getPredictedCategory() != null ? t.getPredictedCategory() : "Unknown") + "|" +
                    (t.getPredictedSubcategory() != null ? t.getPredictedSubcategory() : "") + "|" +
                    extractMerchantPattern(t.getNarration())
                )
            ));

        for (Map.Entry<DayOfWeek, Map<String, List<Transaction>>> dayEntry : byDayOfWeek.entrySet()) {
            DayOfWeek dayOfWeek = dayEntry.getKey();
            
            for (Map.Entry<String, List<Transaction>> categoryEntry : dayEntry.getValue().entrySet()) {
                List<Transaction> txs = categoryEntry.getValue();
                if (txs.size() < 4) continue; // Need at least 4 occurrences

                String[] parts = categoryEntry.getKey().split("\\|");
                String category = parts[0];
                String subcategory = parts.length > 1 ? parts[1] : null;
                String merchantPattern = parts.length > 2 ? parts[2] : null;

                // Check if it occurs consistently on this day
                long weeksWithTransaction = txs.stream()
                    .map(t -> t.getDate().with(DayOfWeek.MONDAY)) // Group by week
                    .distinct()
                    .count();

                long totalWeeks = ChronoUnit.WEEKS.between(
                    txs.stream().map(Transaction::getDate).min(LocalDate::compareTo).orElse(LocalDate.now()),
                    LocalDate.now()
                );

                if (totalWeeks > 0 && (weeksWithTransaction * 100.0 / totalWeeks) > 40) {
                    double avgAmount = txs.stream()
                        .mapToDouble(t -> Math.abs(t.getAmount()))
                        .average()
                        .orElse(0.0);

                    SpendingPattern pattern = new SpendingPattern();
                    pattern.setUser(user);
                    pattern.setPatternType(SpendingPattern.PatternType.WEEKLY);
                    pattern.setCategory(category);
                    pattern.setSubcategory(subcategory);
                    pattern.setMerchantPattern(merchantPattern);
                    pattern.setDayOfWeek(dayOfWeek);
                    pattern.setAverageAmount(avgAmount);
                    pattern.setFrequency((int) weeksWithTransaction);
                    pattern.setConfidenceScore(Math.min(weeksWithTransaction * 1.0 / Math.max(totalWeeks, 1), 1.0));
                    pattern.setFirstObserved(txs.stream().map(Transaction::getDate).min(LocalDate::compareTo).orElse(LocalDate.now()));
                    pattern.setLastObserved(txs.stream().map(Transaction::getDate).max(LocalDate::compareTo).orElse(LocalDate.now()));
                    pattern.setIsActive(true);

                    patterns.add(pattern);
                }
            }
        }

        return patterns;
    }

    /**
     * Detect monthly patterns (e.g., utility bills on 1st, rent on 15th)
     */
    private List<SpendingPattern> detectMonthlyPatterns(User user, List<Transaction> transactions) {
        List<SpendingPattern> patterns = new ArrayList<>();

        // Group by day of month and category
        Map<Integer, Map<String, List<Transaction>>> byDayOfMonth = transactions.stream()
            .filter(t -> t.getPredictedCategory() != null)
            .collect(Collectors.groupingBy(
                t -> t.getDate().getDayOfMonth(),
                Collectors.groupingBy(t -> 
                    (t.getPredictedCategory() != null ? t.getPredictedCategory() : "Unknown") + "|" +
                    (t.getPredictedSubcategory() != null ? t.getPredictedSubcategory() : "") + "|" +
                    extractMerchantPattern(t.getNarration())
                )
            ));

        for (Map.Entry<Integer, Map<String, List<Transaction>>> dayEntry : byDayOfMonth.entrySet()) {
            Integer dayOfMonth = dayEntry.getKey();
            
            for (Map.Entry<String, List<Transaction>> categoryEntry : dayEntry.getValue().entrySet()) {
                List<Transaction> txs = categoryEntry.getValue();
                if (txs.size() < 3) continue; // Need at least 3 occurrences

                String[] parts = categoryEntry.getKey().split("\\|");
                String category = parts[0];
                String subcategory = parts.length > 1 ? parts[1] : null;
                String merchantPattern = parts.length > 2 ? parts[2] : null;

                // Check if it occurs consistently on this day of month
                long monthsWithTransaction = txs.stream()
                    .map(t -> t.getDate().withDayOfMonth(1)) // Group by month
                    .distinct()
                    .count();

                long totalMonths = ChronoUnit.MONTHS.between(
                    txs.stream().map(Transaction::getDate).min(LocalDate::compareTo).orElse(LocalDate.now()),
                    LocalDate.now()
                );

                if (totalMonths > 0 && (monthsWithTransaction * 100.0 / totalMonths) > 50) {
                    double avgAmount = txs.stream()
                        .mapToDouble(t -> Math.abs(t.getAmount()))
                        .average()
                        .orElse(0.0);

                    SpendingPattern pattern = new SpendingPattern();
                    pattern.setUser(user);
                    pattern.setPatternType(SpendingPattern.PatternType.MONTHLY);
                    pattern.setCategory(category);
                    pattern.setSubcategory(subcategory);
                    pattern.setMerchantPattern(merchantPattern);
                    pattern.setDayOfMonth(dayOfMonth);
                    pattern.setAverageAmount(avgAmount);
                    pattern.setFrequency((int) monthsWithTransaction);
                    pattern.setConfidenceScore(Math.min(monthsWithTransaction * 1.0 / Math.max(totalMonths, 1), 1.0));
                    pattern.setFirstObserved(txs.stream().map(Transaction::getDate).min(LocalDate::compareTo).orElse(LocalDate.now()));
                    pattern.setLastObserved(txs.stream().map(Transaction::getDate).max(LocalDate::compareTo).orElse(LocalDate.now()));
                    pattern.setIsActive(true);

                    patterns.add(pattern);
                }
            }
        }

        return patterns;
    }

    /**
     * Extract merchant pattern from narration (simplified merchant name)
     */
    private String extractMerchantPattern(String narration) {
        if (narration == null || narration.isEmpty()) {
            return "UNKNOWN";
        }

        // Extract key words (uppercase, remove UPI codes, etc.)
        String cleaned = narration.toUpperCase()
            .replaceAll("UPI[^\\s]*", "")
            .replaceAll("\\d+", "")
            .replaceAll("[^A-Z\\s]", " ")
            .trim();

        // Take first few significant words
        String[] words = cleaned.split("\\s+");
        if (words.length > 0) {
            return words[0] + (words.length > 1 ? " " + words[1] : "");
        }

        return "UNKNOWN";
    }

    /**
     * Get active patterns for a user
     */
    public List<SpendingPattern> getActivePatterns(User user) {
        return spendingPatternRepository.findByUserAndIsActiveTrue(user);
    }

    /**
     * Refresh patterns for a user (re-analyze and update)
     */
    public void refreshPatterns(User user) {
        // Deactivate old patterns
        List<SpendingPattern> oldPatterns = spendingPatternRepository.findByUser(user);
        for (SpendingPattern pattern : oldPatterns) {
            pattern.setIsActive(false);
            spendingPatternRepository.save(pattern);
        }

        // Detect new patterns
        detectPatterns(user);
    }
}

