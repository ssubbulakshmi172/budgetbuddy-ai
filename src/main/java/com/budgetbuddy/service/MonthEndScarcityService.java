package com.budgetbuddy.service;

import com.budgetbuddy.model.MonthEndScarcity;
import com.budgetbuddy.model.Transaction;
import com.budgetbuddy.model.User;
import com.budgetbuddy.repository.MonthEndScarcityRepository;
import com.budgetbuddy.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MonthEndScarcityService {

    private static final Logger logger = LoggerFactory.getLogger(MonthEndScarcityService.class);

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private MonthEndScarcityRepository monthEndScarcityRepository;

    private static final int MONTH_END_DAYS = 7; // Last 7 days of month

    /**
     * Detect month-end scarcity behavior
     */
    public MonthEndScarcity detectMonthEndScarcity(User user) {
        logger.info("Detecting month-end scarcity for user: {}", user.getId());

        YearMonth currentMonth = YearMonth.now();
        LocalDate monthStart = currentMonth.atDay(1);
        LocalDate monthEnd = currentMonth.atEndOfMonth();
        LocalDate monthEndStart = monthEnd.minusDays(MONTH_END_DAYS - 1);

        // Get current month transactions
        List<Transaction> transactions = transactionRepository.findAll()
            .stream()
            .filter(t -> t.getUser().getId().equals(user.getId()))
            .filter(t -> t.getAmount() != null && t.getAmount() < 0)
            .filter(t -> !t.getDate().isBefore(monthStart) && !t.getDate().isAfter(monthEnd))
            .collect(Collectors.toList());

        if (transactions.isEmpty()) {
            return null;
        }

        // Separate month-end and rest of month
        List<Transaction> monthEndTxs = transactions.stream()
            .filter(t -> !t.getDate().isBefore(monthEndStart))
            .collect(Collectors.toList());

        List<Transaction> restOfMonthTxs = transactions.stream()
            .filter(t -> t.getDate().isBefore(monthEndStart))
            .collect(Collectors.toList());

        if (restOfMonthTxs.isEmpty()) {
            return null;
        }

        // Calculate daily averages
        long monthEndDays = java.time.temporal.ChronoUnit.DAYS.between(monthEndStart, monthEnd) + 1;
        long restOfMonthDays = java.time.temporal.ChronoUnit.DAYS.between(monthStart, monthEndStart);

        double monthEndSpending = monthEndTxs.stream()
            .mapToDouble(t -> Math.abs(t.getAmount()))
            .sum();

        double restOfMonthSpending = restOfMonthTxs.stream()
            .mapToDouble(t -> Math.abs(t.getAmount()))
            .sum();

        double monthEndDailyAvg = monthEndSpending / monthEndDays;
        double restOfMonthDailyAvg = restOfMonthSpending / restOfMonthDays;

        if (restOfMonthDailyAvg == 0) {
            return null;
        }

        double ratio = monthEndDailyAvg / restOfMonthDailyAvg;

        // Detect indicators
        boolean reducedSpending = ratio < 0.7; // 30%+ reduction
        boolean creditSpike = detectCreditSpike(user, monthEndStart, monthEnd);
        boolean savingsWithdrawal = detectSavingsWithdrawal(user, monthEndStart, monthEnd);
        boolean borrowingIncrease = detectBorrowingIncrease(user, monthEndStart, monthEnd);

        // Classify behavior
        MonthEndScarcity.BehaviorType behaviorType;
        if (ratio < 0.7) {
            behaviorType = MonthEndScarcity.BehaviorType.SCARCITY;
        } else if (ratio > 1.3) {
            behaviorType = MonthEndScarcity.BehaviorType.OVERSPEND;
        } else {
            behaviorType = MonthEndScarcity.BehaviorType.NORMAL;
        }

        // Calculate pattern strength and months detected
        double patternStrength = calculatePatternStrength(user, behaviorType);
        int monthsDetected = countMonthsWithPattern(user, behaviorType);

        // Calculate average reduction percentage
        double averageReductionPct = calculateAverageReduction(user);

        // Create analysis
        MonthEndScarcity scarcity = new MonthEndScarcity();
        scarcity.setUser(user);
        scarcity.setMonth(currentMonth);
        scarcity.setMonthEndSpending(monthEndSpending);
        scarcity.setRestOfMonthAvg(restOfMonthDailyAvg);
        scarcity.setRatio(ratio);
        scarcity.setAverageReductionPct(averageReductionPct);
        scarcity.setBehaviorType(behaviorType);
        scarcity.setReducedSpending(reducedSpending);
        scarcity.setCreditSpike(creditSpike);
        scarcity.setSavingsWithdrawal(savingsWithdrawal);
        scarcity.setBorrowingIncrease(borrowingIncrease);
        scarcity.setPatternStrength(patternStrength);
        scarcity.setMonthsDetected(monthsDetected);

        monthEndScarcityRepository.save(scarcity);

        logger.info("Month-end scarcity detected for user {}: type={}, ratio={}", 
            user.getId(), behaviorType, ratio);
        return scarcity;
    }

    /**
     * Detect credit card spike at month-end
     */
    private boolean detectCreditSpike(User user, LocalDate start, LocalDate end) {
        // This would require credit card transaction detection
        // For now, check for transactions with "CREDIT" in narration
        List<Transaction> monthEndTxs = transactionRepository.findAll()
            .stream()
            .filter(t -> t.getUser().getId().equals(user.getId()))
            .filter(t -> t.getNarration() != null)
            .filter(t -> t.getNarration().toUpperCase().contains("CREDIT"))
            .filter(t -> !t.getDate().isBefore(start) && !t.getDate().isAfter(end))
            .collect(Collectors.toList());

        // Compare to rest of month
        LocalDate monthStart = start.minusDays(20);
        List<Transaction> restOfMonthTxs = transactionRepository.findAll()
            .stream()
            .filter(t -> t.getUser().getId().equals(user.getId()))
            .filter(t -> t.getNarration() != null)
            .filter(t -> t.getNarration().toUpperCase().contains("CREDIT"))
            .filter(t -> t.getDate().isAfter(monthStart) && t.getDate().isBefore(start))
            .collect(Collectors.toList());

        if (restOfMonthTxs.isEmpty()) {
            return monthEndTxs.size() > 0;
        }

        double monthEndCount = monthEndTxs.size();
        double restOfMonthAvg = restOfMonthTxs.size() / 20.0; // Approximate daily average

        return monthEndCount > restOfMonthAvg * 2; // 2x spike
    }

    /**
     * Detect savings withdrawal at month-end
     */
    private boolean detectSavingsWithdrawal(User user, LocalDate start, LocalDate end) {
        List<Transaction> withdrawals = transactionRepository.findAll()
            .stream()
            .filter(t -> t.getUser().getId().equals(user.getId()))
            .filter(t -> t.getNarration() != null)
            .filter(t -> (t.getNarration().toUpperCase().contains("SAVINGS") ||
                         t.getNarration().toUpperCase().contains("FD") ||
                         t.getNarration().toUpperCase().contains("DEPOSIT")) &&
                        t.getAmount() != null && t.getAmount() < 0)
            .filter(t -> !t.getDate().isBefore(start) && !t.getDate().isAfter(end))
            .collect(Collectors.toList());

        return !withdrawals.isEmpty();
    }

    /**
     * Detect borrowing increase at month-end
     */
    private boolean detectBorrowingIncrease(User user, LocalDate start, LocalDate end) {
        // Check for loan transactions, credit increases, etc.
        List<Transaction> borrowings = transactionRepository.findAll()
            .stream()
            .filter(t -> t.getUser().getId().equals(user.getId()))
            .filter(t -> t.getNarration() != null)
            .filter(t -> (t.getNarration().toUpperCase().contains("LOAN") ||
                         t.getNarration().toUpperCase().contains("BORROW") ||
                         t.getNarration().toUpperCase().contains("CREDIT LIMIT")))
            .filter(t -> !t.getDate().isBefore(start) && !t.getDate().isAfter(end))
            .collect(Collectors.toList());

        return !borrowings.isEmpty();
    }

    /**
     * Calculate pattern strength (0.0 to 1.0)
     */
    private double calculatePatternStrength(User user, MonthEndScarcity.BehaviorType behaviorType) {
        List<MonthEndScarcity> pastAnalyses = monthEndScarcityRepository.findByUserOrderByMonthDesc(user);
        
        if (pastAnalyses.isEmpty()) {
            return 0.5; // Default
        }

        long matchingCount = pastAnalyses.stream()
            .filter(a -> a.getBehaviorType() == behaviorType)
            .count();

        return Math.min(1.0, matchingCount / (double) Math.min(pastAnalyses.size(), 6));
    }

    /**
     * Count months with this pattern
     */
    private int countMonthsWithPattern(User user, MonthEndScarcity.BehaviorType behaviorType) {
        return (int) monthEndScarcityRepository.findByUserOrderByMonthDesc(user).stream()
            .filter(a -> a.getBehaviorType() == behaviorType)
            .count();
    }

    /**
     * Calculate average reduction percentage
     */
    private double calculateAverageReduction(User user) {
        List<MonthEndScarcity> pastAnalyses = monthEndScarcityRepository.findByUserOrderByMonthDesc(user);
        
        return pastAnalyses.stream()
            .filter(a -> a.getBehaviorType() == MonthEndScarcity.BehaviorType.SCARCITY)
            .filter(a -> a.getRatio() < 1.0)
            .mapToDouble(a -> (1.0 - a.getRatio()) * 100)
            .average()
            .orElse(0.0);
    }

    /**
     * Get latest scarcity analysis
     */
    public MonthEndScarcity getLatestAnalysis(User user) {
        return monthEndScarcityRepository.findByUserOrderByMonthDesc(user).stream()
            .findFirst()
            .orElse(null);
    }

}

