package com.budgetbuddy.service;

import com.budgetbuddy.model.Transaction;
import com.budgetbuddy.model.User;
import com.budgetbuddy.repository.TransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Rule-Based Financial Analytics Service
 * Aggregates transactions and generates insights without LLM
 */
@Service
public class FinancialAnalyticsService {

    private static final Logger logger = LoggerFactory.getLogger(FinancialAnalyticsService.class);

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private MoneyLeakService moneyLeakService;

    /**
     * Grocery vs Eating-Out Pattern
     * Compare monthly spend in Groceries vs Food & Dining
     */
    public Map<String, Object> analyzeGroceryVsEatingOut(User user) {
        logger.info("Analyzing grocery vs eating-out pattern for user: {}", user.getId());

        LocalDate threeMonthsAgo = LocalDate.now().minusMonths(3);
        List<Transaction> transactions = transactionRepository.findAll()
            .stream()
            .filter(t -> t.getUser().getId().equals(user.getId()))
            .filter(t -> t.getAmount() != null && t.getAmount() < 0)
            .filter(t -> t.getDate().isAfter(threeMonthsAgo))
            .collect(Collectors.toList());

        // Group by month and category
        Map<YearMonth, Map<String, Double>> monthlyByCategory = new HashMap<>();
        
        for (Transaction t : transactions) {
            String category = t.getCategoryName() != null ? t.getCategoryName() : 
                            (t.getPredictedCategory() != null ? t.getPredictedCategory() : "Unknown");
            YearMonth month = YearMonth.from(t.getDate());
            
            monthlyByCategory.putIfAbsent(month, new HashMap<>());
            Map<String, Double> categoryMap = monthlyByCategory.get(month);
            
            if (category.toLowerCase().contains("grocery") || category.toLowerCase().contains("groceries")) {
                categoryMap.put("grocery", categoryMap.getOrDefault("grocery", 0.0) + Math.abs(t.getAmount()));
            } else if (category.toLowerCase().contains("dining") || category.toLowerCase().contains("food")) {
                categoryMap.put("eating_out", categoryMap.getOrDefault("eating_out", 0.0) + Math.abs(t.getAmount()));
            }
        }

        // Calculate weekly split
        List<Map<String, Object>> weeklySplits = new ArrayList<>();
        double totalGrocery = 0.0;
        double totalEatingOut = 0.0;

        for (Map.Entry<YearMonth, Map<String, Double>> entry : monthlyByCategory.entrySet()) {
            double grocery = entry.getValue().getOrDefault("grocery", 0.0);
            double eatingOut = entry.getValue().getOrDefault("eating_out", 0.0);
            double total = grocery + eatingOut;
            
            if (total > 0) {
                double groceryPercent = (grocery / total) * 100;
                double eatingOutPercent = (eatingOut / total) * 100;
                
                Map<String, Object> weekData = new HashMap<>();
                weekData.put("month", entry.getKey().toString());
                weekData.put("grocery_percent", Math.round(groceryPercent));
                weekData.put("eating_out_percent", Math.round(eatingOutPercent));
                weekData.put("grocery_amount", Math.round(grocery));
                weekData.put("eating_out_amount", Math.round(eatingOut));
                weekData.put("unhealthy_shift", eatingOutPercent > 40);
                
                weeklySplits.add(weekData);
                
                totalGrocery += grocery;
                totalEatingOut += eatingOut;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("weekly_splits", weeklySplits);
        result.put("total_grocery", Math.round(totalGrocery));
        result.put("total_eating_out", Math.round(totalEatingOut));
        result.put("overall_grocery_percent", totalGrocery + totalEatingOut > 0 ? 
                   Math.round((totalGrocery / (totalGrocery + totalEatingOut)) * 100) : 0);
        result.put("overall_eating_out_percent", totalGrocery + totalEatingOut > 0 ? 
                   Math.round((totalEatingOut / (totalGrocery + totalEatingOut)) * 100) : 0);
        result.put("unhealthy_shift_detected", totalEatingOut > totalGrocery * 0.67); // >40% threshold
        result.put("improvement_suggestion", totalEatingOut > totalGrocery * 0.67 ? 
                   "Eating out exceeds 40% of food budget. Try meal planning and grocery shopping to reduce by 20%." :
                   "Food spending is balanced. Maintain current grocery-to-dining ratio.");

        return result;
    }

    /**
     * Investment Till Date
     * Sum all Investment transactions, track monthly and cumulative totals
     */
    public Map<String, Object> trackInvestments(User user) {
        logger.info("Tracking investments for user: {}", user.getId());

        List<Transaction> transactions = transactionRepository.findAll()
            .stream()
            .filter(t -> t.getUser().getId().equals(user.getId()))
            .filter(t -> t.getAmount() != null && t.getAmount() < 0)
            .filter(t -> isInvestmentTransaction(t))
            .collect(Collectors.toList());

        // Group by month
        Map<YearMonth, List<Transaction>> byMonth = transactions.stream()
            .collect(Collectors.groupingBy(t -> YearMonth.from(t.getDate())));

        List<Map<String, Object>> monthlyTotals = new ArrayList<>();
        double cumulativeTotal = 0.0;

        for (Map.Entry<YearMonth, List<Transaction>> entry : byMonth.entrySet()) {
            double monthlyTotal = entry.getValue().stream()
                .mapToDouble(t -> Math.abs(t.getAmount()))
                .sum();
            
            cumulativeTotal += monthlyTotal;
            
            Map<String, Object> monthData = new HashMap<>();
            monthData.put("month", entry.getKey().toString());
            monthData.put("monthly_total", Math.round(monthlyTotal));
            monthData.put("cumulative_total", Math.round(cumulativeTotal));
            monthData.put("transaction_count", entry.getValue().size());
            
            monthlyTotals.add(monthData);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("monthly_totals", monthlyTotals);
        result.put("total_investment", Math.round(cumulativeTotal));
        result.put("average_monthly", monthlyTotals.size() > 0 ? 
                   Math.round(cumulativeTotal / monthlyTotals.size()) : 0);
        result.put("total_months", monthlyTotals.size());

        return result;
    }

    /**
     * Subscriptions (non-investment)
     * Sum subscriptions that are not tagged Investment
     */
    public Map<String, Object> analyzeSubscriptions(User user) {
        logger.info("Analyzing subscriptions for user: {}", user.getId());

        LocalDate sixMonthsAgo = LocalDate.now().minusMonths(6);
        List<Transaction> transactions = transactionRepository.findAll()
            .stream()
            .filter(t -> t.getUser().getId().equals(user.getId()))
            .filter(t -> t.getAmount() != null && t.getAmount() < 0)
            .filter(t -> t.getDate().isAfter(sixMonthsAgo))
            .filter(t -> !isInvestmentTransaction(t))
            .filter(t -> {
                String intent = t.getPredictedIntent();
                String narration = t.getNarration() != null ? t.getNarration().toLowerCase() : "";
                return "subscription".equals(intent) || 
                       narration.contains("subscription") ||
                       narration.contains("auto debit") ||
                       narration.contains("recurring");
            })
            .collect(Collectors.toList());

        // Group by merchant/amount (recurring pattern)
        Map<String, List<Transaction>> grouped = transactions.stream()
            .collect(Collectors.groupingBy(t -> {
                String merchant = extractMerchantPattern(t.getNarration());
                double amount = Math.abs(t.getAmount());
                return merchant + "|" + String.format("%.0f", amount);
            }));

        List<Map<String, Object>> subscriptions = new ArrayList<>();
        double totalMonthly = 0.0;

        for (Map.Entry<String, List<Transaction>> entry : grouped.entrySet()) {
            List<Transaction> txs = entry.getValue();
            if (txs.size() >= 3) { // Recurring pattern
                String[] parts = entry.getKey().split("\\|");
                String merchant = parts[0];
                double amount = Double.parseDouble(parts[1]);
                
                Map<String, Object> sub = new HashMap<>();
                sub.put("merchant", merchant);
                sub.put("monthly_amount", Math.round(amount));
                sub.put("annual_amount", Math.round(amount * 12));
                sub.put("occurrences", txs.size());
                sub.put("status", "active");
                
                subscriptions.add(sub);
                totalMonthly += amount;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("subscriptions", subscriptions);
        result.put("total_monthly", Math.round(totalMonthly));
        result.put("total_annual", Math.round(totalMonthly * 12));
        result.put("count", subscriptions.size());

        return result;
    }

    /**
     * Category Trend Visualization
     * Prepare JSON for charts with monthly totals, category totals, weekend vs weekday, etc.
     */
    public ObjectNode getCategoryTrendVisualization(User user) {
        logger.info("Generating category trend visualization for user: {}", user.getId());

        LocalDate ninetyDaysAgo = LocalDate.now().minusDays(90);
        List<Transaction> transactions = transactionRepository.findAll()
            .stream()
            .filter(t -> t.getUser().getId().equals(user.getId()))
            .filter(t -> t.getAmount() != null && t.getAmount() < 0)
            .filter(t -> t.getDate().isAfter(ninetyDaysAgo))
            .filter(t -> !isInvestmentTransaction(t))
            .collect(Collectors.toList());

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode result = mapper.createObjectNode();
        
        // Monthly totals
        Map<YearMonth, Double> monthlyTotals = transactions.stream()
            .collect(Collectors.groupingBy(
                t -> YearMonth.from(t.getDate()),
                Collectors.summingDouble(t -> Math.abs(t.getAmount()))
            ));
        
        ArrayNode monthlyArray = mapper.createArrayNode();
        monthlyTotals.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> monthlyArray.add(entry.getValue()));
        result.set("monthly_totals", monthlyArray);

        // Category totals
        Map<String, Double> categoryTotals = transactions.stream()
            .filter(t -> {
                String cat = t.getCategoryName() != null ? t.getCategoryName() : 
                           (t.getPredictedCategory() != null ? t.getPredictedCategory() : "Unknown");
                return cat != null && !cat.equals("Unknown");
            })
            .collect(Collectors.groupingBy(
                t -> {
                    String cat = t.getCategoryName() != null ? t.getCategoryName() : 
                               t.getPredictedCategory();
                    return cat != null ? cat : "Unknown";
                },
                Collectors.summingDouble(t -> Math.abs(t.getAmount()))
            ));
        
        ObjectNode categoryNode = mapper.createObjectNode();
        categoryTotals.forEach((cat, total) -> categoryNode.put(cat, total));
        result.set("category_totals", categoryNode);

        // Weekend vs Weekday
        double weekendTotal = transactions.stream()
            .filter(t -> {
                DayOfWeek dow = t.getDate().getDayOfWeek();
                return dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
            })
            .mapToDouble(t -> Math.abs(t.getAmount()))
            .sum();
        
        double weekdayTotal = transactions.stream()
            .filter(t -> {
                DayOfWeek dow = t.getDate().getDayOfWeek();
                return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY;
            })
            .mapToDouble(t -> Math.abs(t.getAmount()))
            .sum();
        
        ObjectNode weekendNode = mapper.createObjectNode();
        weekendNode.put("weekend", weekendTotal);
        weekendNode.put("weekday", weekdayTotal);
        weekendNode.put("difference_percent", weekdayTotal > 0 ? 
                       Math.round(((weekendTotal - weekdayTotal) / weekdayTotal) * 100) : 0);
        result.set("weekend_vs_weekday", weekendNode);

        // Grocery vs Dining
        double groceryTotal = transactions.stream()
            .filter(t -> {
                String cat = t.getCategoryName() != null ? t.getCategoryName() : 
                           (t.getPredictedCategory() != null ? t.getPredictedCategory() : "");
                return cat.toLowerCase().contains("grocery");
            })
            .mapToDouble(t -> Math.abs(t.getAmount()))
            .sum();
        
        double diningTotal = transactions.stream()
            .filter(t -> {
                String cat = t.getCategoryName() != null ? t.getCategoryName() : 
                           (t.getPredictedCategory() != null ? t.getPredictedCategory() : "");
                return cat.toLowerCase().contains("dining") || cat.toLowerCase().contains("food");
            })
            .mapToDouble(t -> Math.abs(t.getAmount()))
            .sum();
        
        ObjectNode groceryNode = mapper.createObjectNode();
        groceryNode.put("grocery", groceryTotal);
        groceryNode.put("dining", diningTotal);
        groceryNode.put("grocery_percent", groceryTotal + diningTotal > 0 ? 
                       Math.round((groceryTotal / (groceryTotal + diningTotal)) * 100) : 0);
        result.set("grocery_vs_dining", groceryNode);

        // Top spike days (days with highest spending)
        Map<LocalDate, Double> dailyTotals = transactions.stream()
            .collect(Collectors.groupingBy(
                Transaction::getDate,
                Collectors.summingDouble(t -> Math.abs(t.getAmount()))
            ));
        
        ArrayNode spikeDays = mapper.createArrayNode();
        dailyTotals.entrySet().stream()
            .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
            .limit(10)
            .forEach(entry -> {
                ObjectNode dayNode = mapper.createObjectNode();
                dayNode.put("date", entry.getKey().toString());
                dayNode.put("amount", entry.getValue());
                spikeDays.add(dayNode);
            });
        result.set("top_spike_days", spikeDays);

        return result;
    }

    /**
     * Extract merchant pattern from narration (delegates to MoneyLeakService)
     */
    private String extractMerchantPattern(String narration) {
        return moneyLeakService.extractMerchantPattern(narration);
    }

    /**
     * Check if transaction is investment (delegates to MoneyLeakService)
     */
    private boolean isInvestmentTransaction(Transaction transaction) {
        // Access via reflection or make method public in MoneyLeakService
        // For now, duplicate the logic
        String category = transaction.getCategoryName() != null ? transaction.getCategoryName() : 
                         (transaction.getPredictedCategory() != null ? transaction.getPredictedCategory() : "");
        if (category == null || category.trim().isEmpty()) {
            return false;
        }
        String categoryLower = category.toLowerCase().trim();
        return categoryLower.equals("investments") || categoryLower.startsWith("investments /");
    }
}

