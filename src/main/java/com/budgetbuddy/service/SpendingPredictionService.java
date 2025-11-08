package com.budgetbuddy.service;

import com.budgetbuddy.model.SpendingPattern;
import com.budgetbuddy.model.SpendingPrediction;
import com.budgetbuddy.model.Transaction;
import com.budgetbuddy.model.User;
import com.budgetbuddy.repository.SpendingPatternRepository;
import com.budgetbuddy.repository.SpendingPredictionRepository;
import com.budgetbuddy.repository.TransactionRepository;
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
public class SpendingPredictionService {

    private static final Logger logger = LoggerFactory.getLogger(SpendingPredictionService.class);

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private SpendingPatternRepository spendingPatternRepository;

    @Autowired
    private SpendingPredictionRepository spendingPredictionRepository;

    @Autowired
    private TrendAnalysisService trendAnalysisService;

    /**
     * Predict future spending for the next period (default: next month)
     */
    public List<SpendingPrediction> predictFutureSpending(User user, LocalDate startDate, LocalDate endDate) {
        logger.info("Predicting future spending for user {} from {} to {}", user.getId(), startDate, endDate);

        List<SpendingPrediction> predictions = new ArrayList<>();

        // Get historical transactions
        List<Transaction> historicalTransactions = transactionRepository.findAll()
            .stream()
            .filter(t -> t.getUser().getId().equals(user.getId()))
            .filter(t -> t.getAmount() != null && t.getAmount() < 0) // Only expenses
            .filter(t -> t.getDate().isBefore(startDate))
            .filter(t -> t.getDate().isAfter(LocalDate.now().minusMonths(12))) // Last 12 months
            .collect(Collectors.toList());

        // Get active patterns
        List<SpendingPattern> patterns = spendingPatternRepository.findByUserAndIsActiveTrue(user);

        // Get trends
        TrendAnalysisService.TrendAnalysisResult trends = trendAnalysisService.analyzeTrends(user);

        // Group historical transactions by category
        Map<String, List<Transaction>> byCategory = historicalTransactions.stream()
            .filter(t -> t.getPredictedCategory() != null)
            .collect(Collectors.groupingBy(
                t -> t.getPredictedCategory() != null ? t.getPredictedCategory() : "Uncategorized"
            ));

        // Predict for each category
        for (Map.Entry<String, List<Transaction>> entry : byCategory.entrySet()) {
            String category = entry.getKey();
            List<Transaction> categoryTransactions = entry.getValue();

            // Find matching pattern
            SpendingPattern matchingPattern = patterns.stream()
                .filter(p -> category.equals(p.getCategory()))
                .findFirst()
                .orElse(null);

            // Find trend
            TrendAnalysisService.Trend trend = trends.getTrends().stream()
                .filter(t -> category.equals(t.getCategory()))
                .findFirst()
                .orElse(null);

            // Predict amount
            SpendingPrediction prediction = predictCategorySpending(
                user, category, categoryTransactions, matchingPattern, trend, startDate, endDate
            );

            if (prediction != null) {
                predictions.add(prediction);
            }
        }

        // Save predictions
        for (SpendingPrediction prediction : predictions) {
            spendingPredictionRepository.save(prediction);
        }

        logger.info("Generated {} spending predictions for user {}", predictions.size(), user.getId());
        return predictions;
    }

    /**
     * Predict spending for a specific category
     */
    private SpendingPrediction predictCategorySpending(
        User user, String category, List<Transaction> historicalTransactions,
        SpendingPattern pattern, TrendAnalysisService.Trend trend,
        LocalDate startDate, LocalDate endDate
    ) {
        if (historicalTransactions.isEmpty()) {
            return null;
        }

        // Calculate base prediction using historical average
        double historicalAverage = calculateHistoricalAverage(historicalTransactions, startDate, endDate);

        double predictedAmount = historicalAverage;
        String predictionMethod = "HISTORICAL_AVERAGE";
        double confidence = 0.5;

        // Adjust based on pattern if available
        if (pattern != null) {
            predictedAmount = adjustForPattern(predictedAmount, pattern, startDate, endDate);
            predictionMethod = "PATTERN_BASED";
            confidence = Math.max(confidence, pattern.getConfidenceScore() != null ? pattern.getConfidenceScore() : 0.5);
        }

        // Adjust based on trend if available
        if (trend != null) {
            predictedAmount = adjustForTrend(predictedAmount, trend, historicalAverage);
            if (!predictionMethod.equals("PATTERN_BASED")) {
                predictionMethod = "TREND_BASED";
            }
            confidence = Math.max(confidence, trend.getStrength());
        }

        // Determine risk level
        SpendingPrediction.RiskLevel riskLevel = determineRiskLevel(predictedAmount, historicalAverage);
        boolean isOverspendingRisk = predictedAmount > historicalAverage * 1.2; // 20% increase

        SpendingPrediction prediction = new SpendingPrediction();
        prediction.setUser(user);
        prediction.setPredictionDate(LocalDate.now());
        prediction.setForecastStartDate(startDate);
        prediction.setForecastEndDate(endDate);
        prediction.setCategory(category);
        prediction.setPredictedAmount(predictedAmount);
        prediction.setConfidenceScore(confidence);
        prediction.setPredictionMethod(predictionMethod);
        prediction.setRiskLevel(riskLevel);
        prediction.setIsOverspendingRisk(isOverspendingRisk);

        return prediction;
    }

    /**
     * Calculate historical average for the period
     */
    private double calculateHistoricalAverage(List<Transaction> transactions, LocalDate startDate, LocalDate endDate) {
        long daysInPeriod = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1;
        
        // Group by month to get monthly averages
        Map<YearMonth, Double> monthlySpending = transactions.stream()
            .collect(Collectors.groupingBy(
                t -> YearMonth.from(t.getDate()),
                Collectors.summingDouble(t -> Math.abs(t.getAmount()))
            ));

        if (monthlySpending.isEmpty()) {
            return 0.0;
        }

        double avgMonthlySpending = monthlySpending.values().stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.0);

        // Scale to period length
        return avgMonthlySpending * (daysInPeriod / 30.0);
    }

    /**
     * Adjust prediction based on spending pattern
     */
    private double adjustForPattern(double baseAmount, SpendingPattern pattern, LocalDate startDate, LocalDate endDate) {
        if (pattern.getAverageAmount() == null) {
            return baseAmount;
        }

        long daysInPeriod = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1;

        switch (pattern.getPatternType()) {
            case DAILY:
                // Daily pattern: multiply by days
                return pattern.getAverageAmount() * daysInPeriod * (pattern.getFrequency() != null ? pattern.getFrequency() / 30.0 : 1.0);
            
            case WEEKLY:
                // Weekly pattern: count weeks and multiply
                long weeks = daysInPeriod / 7;
                return pattern.getAverageAmount() * weeks;
            
            case MONTHLY:
                // Monthly pattern: count months
                long months = daysInPeriod / 30;
                return pattern.getAverageAmount() * months;
            
            default:
                return baseAmount;
        }
    }

    /**
     * Adjust prediction based on trend
     */
    private double adjustForTrend(double baseAmount, TrendAnalysisService.Trend trend, double historicalAverage) {
        if (trend.getDirection() == TrendAnalysisService.Trend.TrendDirection.INCREASING) {
            // If increasing, project forward
            double growthRate = (trend.getEndAmount() - trend.getStartAmount()) / Math.max(trend.getStartAmount(), 1.0);
            return baseAmount * (1 + growthRate * trend.getStrength());
        } else if (trend.getDirection() == TrendAnalysisService.Trend.TrendDirection.DECREASING) {
            // If decreasing, reduce projection
            double declineRate = (trend.getStartAmount() - trend.getEndAmount()) / Math.max(trend.getStartAmount(), 1.0);
            return baseAmount * (1 - declineRate * trend.getStrength());
        }
        return baseAmount;
    }

    /**
     * Determine risk level based on predicted vs historical spending
     */
    private SpendingPrediction.RiskLevel determineRiskLevel(double predictedAmount, double historicalAverage) {
        if (historicalAverage == 0) {
            return SpendingPrediction.RiskLevel.MEDIUM;
        }

        double ratio = predictedAmount / historicalAverage;

        if (ratio > 1.3) {
            return SpendingPrediction.RiskLevel.HIGH;
        } else if (ratio > 1.1) {
            return SpendingPrediction.RiskLevel.MEDIUM;
        } else {
            return SpendingPrediction.RiskLevel.LOW;
        }
    }

    /**
     * Get predictions for a user
     */
    public List<SpendingPrediction> getPredictions(User user) {
        return spendingPredictionRepository.findByUser(user);
    }

    /**
     * Get overspending risk predictions
     */
    public List<SpendingPrediction> getOverspendingRisks(User user) {
        return spendingPredictionRepository.findByUserAndIsOverspendingRiskTrue(user);
    }
}

