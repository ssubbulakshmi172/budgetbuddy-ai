package com.budgetbuddy.service;

import com.budgetbuddy.model.Transaction;
import com.budgetbuddy.model.User;
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
public class TrendAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(TrendAnalysisService.class);

    @Autowired
    private TransactionRepository transactionRepository;

    /**
     * Analyze spending trends for a user
     */
    public TrendAnalysisResult analyzeTrends(User user) {
        logger.info("Analyzing spending trends for user: {}", user.getId());

        List<Transaction> transactions = transactionRepository.findAll()
            .stream()
            .filter(t -> t.getUser().getId().equals(user.getId()))
            .filter(t -> t.getAmount() != null && t.getAmount() < 0) // Only expenses
            .filter(t -> t.getDate().isAfter(LocalDate.now().minusMonths(6))) // Last 6 months
            .collect(Collectors.toList());

        if (transactions.isEmpty()) {
            return new TrendAnalysisResult(Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        }

        List<Trend> trends = new ArrayList<>();
        List<Spike> spikes = new ArrayList<>();
        List<Dip> dips = new ArrayList<>();

        // Analyze by category
        Map<String, List<Transaction>> byCategory = transactions.stream()
            .filter(t -> t.getPredictedCategory() != null)
            .collect(Collectors.groupingBy(
                t -> t.getPredictedCategory() != null ? t.getPredictedCategory() : "Uncategorized"
            ));

        for (Map.Entry<String, List<Transaction>> entry : byCategory.entrySet()) {
            String category = entry.getKey();
            List<Transaction> categoryTransactions = entry.getValue();

            // Group by month
            Map<YearMonth, Double> monthlySpending = categoryTransactions.stream()
                .collect(Collectors.groupingBy(
                    t -> YearMonth.from(t.getDate()),
                    Collectors.summingDouble(t -> Math.abs(t.getAmount()))
                ));

            if (monthlySpending.size() < 3) continue; // Need at least 3 months

            List<YearMonth> sortedMonths = new ArrayList<>(monthlySpending.keySet());
            Collections.sort(sortedMonths);

            // Calculate trend (increasing, decreasing, stable)
            double[] amounts = sortedMonths.stream()
                .mapToDouble(monthlySpending::get)
                .toArray();

            Trend trend = calculateTrend(category, amounts, sortedMonths);
            if (trend != null) {
                trends.add(trend);
            }

            // Detect spikes (unusual increases)
            spikes.addAll(detectSpikes(category, monthlySpending, sortedMonths));

            // Detect dips (unusual decreases)
            dips.addAll(detectDips(category, monthlySpending, sortedMonths));
        }

        logger.info("Detected {} trends, {} spikes, {} dips", trends.size(), spikes.size(), dips.size());
        return new TrendAnalysisResult(trends, spikes, dips);
    }

    /**
     * Calculate trend direction and strength
     */
    private Trend calculateTrend(String category, double[] amounts, List<YearMonth> months) {
        if (amounts.length < 3) return null;

        // Simple linear regression to determine trend
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        int n = amounts.length;

        for (int i = 0; i < n; i++) {
            sumX += i;
            sumY += amounts[i];
            sumXY += i * amounts[i];
            sumX2 += i * i;
        }

        double slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
        double avgAmount = sumY / n;

        Trend.TrendDirection direction;
        if (slope > avgAmount * 0.05) {
            direction = Trend.TrendDirection.INCREASING;
        } else if (slope < -avgAmount * 0.05) {
            direction = Trend.TrendDirection.DECREASING;
        } else {
            direction = Trend.TrendDirection.STABLE;
        }

        double strength = Math.abs(slope) / Math.max(avgAmount, 1.0);
        strength = Math.min(strength, 1.0); // Cap at 1.0

        return new Trend(category, direction, strength, amounts[0], amounts[n - 1]);
    }

    /**
     * Detect unusual spikes in spending
     */
    private List<Spike> detectSpikes(String category, Map<YearMonth, Double> monthlySpending, List<YearMonth> sortedMonths) {
        List<Spike> spikes = new ArrayList<>();

        if (sortedMonths.size() < 3) return spikes;

        double[] amounts = sortedMonths.stream()
            .mapToDouble(monthlySpending::get)
            .toArray();

        double mean = Arrays.stream(amounts).average().orElse(0.0);
        double stdDev = calculateStandardDeviation(amounts, mean);

        for (int i = 0; i < amounts.length; i++) {
            // Spike if amount is more than 2 standard deviations above mean
            if (amounts[i] > mean + 2 * stdDev && amounts[i] > mean * 1.5) {
                YearMonth month = sortedMonths.get(i);
                double increase = amounts[i] - mean;
                double percentageIncrease = (increase / mean) * 100;

                spikes.add(new Spike(category, month, amounts[i], increase, percentageIncrease));
            }
        }

        return spikes;
    }

    /**
     * Detect unusual dips in spending
     */
    private List<Dip> detectDips(String category, Map<YearMonth, Double> monthlySpending, List<YearMonth> sortedMonths) {
        List<Dip> dips = new ArrayList<>();

        if (sortedMonths.size() < 3) return dips;

        double[] amounts = sortedMonths.stream()
            .mapToDouble(monthlySpending::get)
            .toArray();

        double mean = Arrays.stream(amounts).average().orElse(0.0);
        double stdDev = calculateStandardDeviation(amounts, mean);

        for (int i = 0; i < amounts.length; i++) {
            // Dip if amount is more than 2 standard deviations below mean
            if (amounts[i] < mean - 2 * stdDev && amounts[i] < mean * 0.5) {
                YearMonth month = sortedMonths.get(i);
                double decrease = mean - amounts[i];
                double percentageDecrease = (decrease / mean) * 100;

                dips.add(new Dip(category, month, amounts[i], decrease, percentageDecrease));
            }
        }

        return dips;
    }

    private double calculateStandardDeviation(double[] values, double mean) {
        double sumSquaredDiff = 0;
        for (double value : values) {
            sumSquaredDiff += Math.pow(value - mean, 2);
        }
        return Math.sqrt(sumSquaredDiff / values.length);
    }

    // Inner classes for results
    public static class TrendAnalysisResult {
        private final List<Trend> trends;
        private final List<Spike> spikes;
        private final List<Dip> dips;

        public TrendAnalysisResult(List<Trend> trends, List<Spike> spikes, List<Dip> dips) {
            this.trends = trends;
            this.spikes = spikes;
            this.dips = dips;
        }

        public List<Trend> getTrends() { return trends; }
        public List<Spike> getSpikes() { return spikes; }
        public List<Dip> getDips() { return dips; }
    }

    public static class Trend {
        private final String category;
        private final TrendDirection direction;
        private final double strength; // 0.0 to 1.0
        private final double startAmount;
        private final double endAmount;

        public Trend(String category, TrendDirection direction, double strength, double startAmount, double endAmount) {
            this.category = category;
            this.direction = direction;
            this.strength = strength;
            this.startAmount = startAmount;
            this.endAmount = endAmount;
        }

        public enum TrendDirection {
            INCREASING, DECREASING, STABLE
        }

        public String getCategory() { return category; }
        public TrendDirection getDirection() { return direction; }
        public double getStrength() { return strength; }
        public double getStartAmount() { return startAmount; }
        public double getEndAmount() { return endAmount; }
    }

    public static class Spike {
        private final String category;
        private final YearMonth month;
        private final double amount;
        private final double increase;
        private final double percentageIncrease;

        public Spike(String category, YearMonth month, double amount, double increase, double percentageIncrease) {
            this.category = category;
            this.month = month;
            this.amount = amount;
            this.increase = increase;
            this.percentageIncrease = percentageIncrease;
        }

        public String getCategory() { return category; }
        public YearMonth getMonth() { return month; }
        public double getAmount() { return amount; }
        public double getIncrease() { return increase; }
        public double getPercentageIncrease() { return percentageIncrease; }
    }

    public static class Dip {
        private final String category;
        private final YearMonth month;
        private final double amount;
        private final double decrease;
        private final double percentageDecrease;

        public Dip(String category, YearMonth month, double amount, double decrease, double percentageDecrease) {
            this.category = category;
            this.month = month;
            this.amount = amount;
            this.decrease = decrease;
            this.percentageDecrease = percentageDecrease;
        }

        public String getCategory() { return category; }
        public YearMonth getMonth() { return month; }
        public double getAmount() { return amount; }
        public double getDecrease() { return decrease; }
        public double getPercentageDecrease() { return percentageDecrease; }
    }
}

