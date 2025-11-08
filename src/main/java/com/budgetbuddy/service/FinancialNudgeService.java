package com.budgetbuddy.service;

import com.budgetbuddy.model.FinancialNudge;
import com.budgetbuddy.model.SpendingPattern;
import com.budgetbuddy.model.SpendingPrediction;
import com.budgetbuddy.model.User;
import com.budgetbuddy.repository.FinancialNudgeRepository;
import com.budgetbuddy.repository.SpendingPatternRepository;
import com.budgetbuddy.repository.SpendingPredictionRepository;
import com.budgetbuddy.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class FinancialNudgeService {

    private static final Logger logger = LoggerFactory.getLogger(FinancialNudgeService.class);

    @Autowired
    private FinancialNudgeRepository financialNudgeRepository;

    @Autowired
    private SpendingPredictionRepository spendingPredictionRepository;

    @Autowired
    private SpendingPatternRepository spendingPatternRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private SpendingPredictionService spendingPredictionService;

    /**
     * Generate proactive nudges for a user based on patterns, predictions, and trends
     */
    public List<FinancialNudge> generateNudges(User user) {
        logger.info("Generating financial nudges for user: {}", user.getId());

        List<FinancialNudge> nudges = new ArrayList<>();

        // 1. Check for overspending risks
        nudges.addAll(generateOverspendingNudges(user));

        // 2. Notify about new patterns detected
        nudges.addAll(generatePatternNudges(user));

        // 3. Alert about unusual trends
        nudges.addAll(generateTrendNudges(user));

        // 4. Provide savings opportunities
        nudges.addAll(generateSavingsOpportunityNudges(user));

        // Save nudges
        for (FinancialNudge nudge : nudges) {
            financialNudgeRepository.save(nudge);
        }

        logger.info("Generated {} financial nudges for user {}", nudges.size(), user.getId());
        return nudges;
    }

    /**
     * Generate nudges for overspending risks
     */
    private List<FinancialNudge> generateOverspendingNudges(User user) {
        List<FinancialNudge> nudges = new ArrayList<>();

        // Get predictions for next month
        LocalDate nextMonthStart = LocalDate.now().withDayOfMonth(1).plusMonths(1);
        LocalDate nextMonthEnd = nextMonthStart.withDayOfMonth(nextMonthStart.lengthOfMonth());

        List<SpendingPrediction> predictions = spendingPredictionService.predictFutureSpending(
            user, nextMonthStart, nextMonthEnd
        );

        for (SpendingPrediction prediction : predictions) {
            if (prediction.getIsOverspendingRisk() != null && prediction.getIsOverspendingRisk()) {
                FinancialNudge nudge = new FinancialNudge();
                nudge.setUser(user);
                nudge.setNudgeType(FinancialNudge.NudgeType.OVERSPENDING_RISK);
                nudge.setTitle("Overspending Alert: " + prediction.getCategory());
                nudge.setMessage(String.format(
                    "Your spending in %s is predicted to increase by %.1f%% next month. " +
                    "Based on your patterns, you're likely to spend ₹%.0f.",
                    prediction.getCategory(),
                    ((prediction.getPredictedAmount() / getHistoricalAverage(user, prediction.getCategory())) - 1) * 100,
                    prediction.getPredictedAmount()
                ));
                nudge.setSuggestion(generateOverspendingSuggestion(prediction));
                nudge.setCategory(prediction.getCategory());
                nudge.setSubcategory(prediction.getSubcategory());
                nudge.setRelatedAmount(prediction.getPredictedAmount());
                nudge.setPriority(prediction.getRiskLevel() == SpendingPrediction.RiskLevel.HIGH 
                    ? FinancialNudge.Priority.HIGH 
                    : FinancialNudge.Priority.MEDIUM);
                nudge.setIsRead(false);
                nudge.setIsDismissed(false);

                nudges.add(nudge);
            }
        }

        return nudges;
    }

    /**
     * Generate nudges for newly detected patterns
     */
    private List<FinancialNudge> generatePatternNudges(User user) {
        List<FinancialNudge> nudges = new ArrayList<>();

        List<SpendingPattern> recentPatterns = spendingPatternRepository.findByUserAndIsActiveTrue(user)
            .stream()
            .filter(p -> p.getCreatedAt() != null && p.getCreatedAt().isAfter(LocalDate.now().minusDays(7)))
            .collect(Collectors.toList());

        for (SpendingPattern pattern : recentPatterns) {
            FinancialNudge nudge = new FinancialNudge();
            nudge.setUser(user);
            nudge.setNudgeType(FinancialNudge.NudgeType.PATTERN_DETECTED);
            nudge.setTitle("New Spending Pattern Detected");
            nudge.setMessage(String.format(
                "We noticed you regularly spend ₹%.0f on %s%s. " +
                "This happens %s.",
                pattern.getAverageAmount(),
                pattern.getCategory(),
                pattern.getSubcategory() != null ? " - " + pattern.getSubcategory() : "",
                formatPatternFrequency(pattern)
            ));
            nudge.setSuggestion("This pattern helps us predict your future spending. Keep an eye on it!");
            nudge.setCategory(pattern.getCategory());
            nudge.setSubcategory(pattern.getSubcategory());
            nudge.setRelatedAmount(pattern.getAverageAmount());
            nudge.setPriority(FinancialNudge.Priority.LOW);
            nudge.setIsRead(false);
            nudge.setIsDismissed(false);

            nudges.add(nudge);
        }

        return nudges;
    }

    /**
     * Generate nudges for unusual trends
     */
    private List<FinancialNudge> generateTrendNudges(User user) {
        List<FinancialNudge> nudges = new ArrayList<>();

        // This would integrate with TrendAnalysisService
        // For now, we'll check predictions for trend-based warnings

        List<SpendingPrediction> trendBasedPredictions = spendingPredictionRepository.findByUser(user)
            .stream()
            .filter(p -> "TREND_BASED".equals(p.getPredictionMethod()))
            .filter(p -> p.getRiskLevel() == SpendingPrediction.RiskLevel.HIGH)
            .collect(Collectors.toList());

        for (SpendingPrediction prediction : trendBasedPredictions) {
            FinancialNudge nudge = new FinancialNudge();
            nudge.setUser(user);
            nudge.setNudgeType(FinancialNudge.NudgeType.TREND_WARNING);
            nudge.setTitle("Unusual Spending Trend: " + prediction.getCategory());
            nudge.setMessage(String.format(
                "We've detected an unusual trend in your %s spending. " +
                "Your spending is increasing faster than usual.",
                prediction.getCategory()
            ));
            nudge.setSuggestion("Consider reviewing your recent transactions in this category to understand the increase.");
            nudge.setCategory(prediction.getCategory());
            nudge.setRelatedAmount(prediction.getPredictedAmount());
            nudge.setPriority(FinancialNudge.Priority.MEDIUM);
            nudge.setIsRead(false);
            nudge.setIsDismissed(false);

            nudges.add(nudge);
        }

        return nudges;
    }

    /**
     * Generate nudges for savings opportunities
     */
    private List<FinancialNudge> generateSavingsOpportunityNudges(User user) {
        List<FinancialNudge> nudges = new ArrayList<>();

        // Find categories with high spending that could be optimized
        // This is a simplified version - could be enhanced with more sophisticated analysis

        List<SpendingPattern> highFrequencyPatterns = spendingPatternRepository.findByUserAndIsActiveTrue(user)
            .stream()
            .filter(p -> p.getFrequency() != null && p.getFrequency() > 10)
            .filter(p -> p.getAverageAmount() != null && p.getAverageAmount() > 100)
            .collect(Collectors.toList());

        for (SpendingPattern pattern : highFrequencyPatterns) {
            double monthlySpend = pattern.getAverageAmount() * pattern.getFrequency();
            if (monthlySpend > 1000) {
                FinancialNudge nudge = new FinancialNudge();
                nudge.setUser(user);
                nudge.setNudgeType(FinancialNudge.NudgeType.SAVINGS_OPPORTUNITY);
                nudge.setTitle("Savings Opportunity: " + pattern.getCategory());
                nudge.setMessage(String.format(
                    "You spend about ₹%.0f per month on %s. " +
                    "Small changes here could add up to significant savings.",
                    monthlySpend,
                    pattern.getCategory()
                ));
                nudge.setSuggestion("Consider setting a monthly budget for this category or looking for alternatives.");
                nudge.setCategory(pattern.getCategory());
                nudge.setRelatedAmount(monthlySpend);
                nudge.setPriority(FinancialNudge.Priority.LOW);
                nudge.setIsRead(false);
                nudge.setIsDismissed(false);

                nudges.add(nudge);
            }
        }

        return nudges;
    }

    /**
     * Get active nudges for a user
     */
    public List<FinancialNudge> getActiveNudges(User user) {
        return financialNudgeRepository.findByUserAndIsDismissedFalseAndExpiresAtGreaterThanEqual(
            user, LocalDate.now()
        );
    }

    /**
     * Get unread nudges for a user
     */
    public List<FinancialNudge> getUnreadNudges(User user) {
        return financialNudgeRepository.findByUserAndIsDismissedFalseAndIsReadFalse(user);
    }

    /**
     * Mark nudge as read
     */
    public void markAsRead(Long nudgeId) {
        financialNudgeRepository.findById(nudgeId).ifPresent(nudge -> {
            nudge.setIsRead(true);
            financialNudgeRepository.save(nudge);
        });
    }

    /**
     * Dismiss nudge
     */
    public void dismissNudge(Long nudgeId) {
        financialNudgeRepository.findById(nudgeId).ifPresent(nudge -> {
            nudge.setIsDismissed(true);
            financialNudgeRepository.save(nudge);
        });
    }

    // Helper methods

    private String formatPatternFrequency(SpendingPattern pattern) {
        switch (pattern.getPatternType()) {
            case DAILY:
                return "almost daily";
            case WEEKLY:
                return pattern.getDayOfWeek() != null 
                    ? "every " + pattern.getDayOfWeek().toString().toLowerCase()
                    : "weekly";
            case MONTHLY:
                return pattern.getDayOfMonth() != null
                    ? "on the " + pattern.getDayOfMonth() + "th of each month"
                    : "monthly";
            default:
                return "regularly";
        }
    }

    private String generateOverspendingSuggestion(SpendingPrediction prediction) {
        if (prediction.getCategory() != null) {
            if (prediction.getCategory().contains("Dining") || prediction.getCategory().contains("Food")) {
                return "Try meal planning or cooking at home more often to reduce dining expenses.";
            } else if (prediction.getCategory().contains("Transport")) {
                return "Consider carpooling or using public transport to save on transportation costs.";
            } else if (prediction.getCategory().contains("Shopping")) {
                return "Wait 24 hours before making non-essential purchases to avoid impulse buying.";
            }
        }
        return "Review your recent transactions in this category and identify areas where you can cut back.";
    }

    private double getHistoricalAverage(User user, String category) {
        return transactionRepository.findAll()
            .stream()
            .filter(t -> t.getUser().getId().equals(user.getId()))
            .filter(t -> category.equals(t.getPredictedCategory()))
            .filter(t -> t.getAmount() != null && t.getAmount() < 0)
            .filter(t -> t.getDate().isAfter(LocalDate.now().minusMonths(3)))
            .mapToDouble(t -> Math.abs(t.getAmount()))
            .average()
            .orElse(0.0);
    }
}

