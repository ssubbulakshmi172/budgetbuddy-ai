package com.budgetbuddy.controller;

import com.budgetbuddy.model.Transaction;
import com.budgetbuddy.service.TransactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class DashboardController {

    @Autowired
    private TransactionService transactionService;

    @GetMapping("/dashboard")
    public String showDashboard(Model model) {
        // Get all transactions
        List<Transaction> allTransactions = transactionService.getAllTransactions();
        
        // Filter transactions for current month
        LocalDate now = LocalDate.now();
        YearMonth currentMonth = YearMonth.from(now);
        LocalDate firstOfMonth = currentMonth.atDay(1);
        
        List<Transaction> currentMonthTransactions = allTransactions.stream()
            .filter(t -> !t.getDate().isBefore(firstOfMonth))
            .collect(Collectors.toList());
            
        // Get transactions from previous month
        YearMonth lastMonth = currentMonth.minusMonths(1);
        LocalDate firstOfLastMonth = lastMonth.atDay(1);
        LocalDate lastOfLastMonth = lastMonth.atEndOfMonth();
        
        List<Transaction> lastMonthTransactions = allTransactions.stream()
            .filter(t -> !t.getDate().isBefore(firstOfLastMonth) && !t.getDate().isAfter(lastOfLastMonth))
            .collect(Collectors.toList());
        
        // Calculate total spending for current month (only negative amounts for expenses)
        double currentMonthTotal = currentMonthTransactions.stream()
            .filter(t -> t.getAmount() != null && t.getAmount() < 0)
            .mapToDouble(t -> Math.abs(t.getAmount()))
            .sum();
            
        // Calculate total spending for last month
        double lastMonthTotal = lastMonthTransactions.stream()
            .filter(t -> t.getAmount() != null && t.getAmount() < 0)
            .mapToDouble(t -> Math.abs(t.getAmount()))
            .sum();
            
        // Calculate percentage change
        double percentageChange = 0;
        if (lastMonthTotal != 0) {
            percentageChange = ((currentMonthTotal - lastMonthTotal) / lastMonthTotal) * 100;
        }
        
        // Calculate spending by category for last 6 months
        List<YearMonth> last6Months = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            last6Months.add(currentMonth.minusMonths(i));
        }
        Collections.reverse(last6Months); // Oldest to newest
        
        // Create list of month names for the chart
        List<String> monthNames = last6Months.stream()
            .map(ym -> ym.getMonth().toString().substring(0, 3) + " " + ym.getYear())
            .collect(Collectors.toList());
            
        // Get recent transactions (last 5, sorted by date descending)
        List<Transaction> recentTransactions = allTransactions.stream()
            .sorted(Comparator.comparing(Transaction::getDate).reversed())
            .limit(5)
            .collect(Collectors.toList());
            
        // Get recent transactions from last month
        List<Transaction> lastMonthRecentTransactions = lastMonthTransactions.stream()
            .sorted(Comparator.comparing(Transaction::getDate).reversed())
            .limit(5)
            .collect(Collectors.toList());
        
        // Get uncategorized transactions count (using predicted categories)
        long uncategorizedCount = allTransactions.stream()
            .filter(t -> t.getPredictedCategory() == null || t.getPredictedCategory().trim().isEmpty())
            .count();
        
        // Helper function to get category (use ONLY predicted category)
        java.util.function.Function<Transaction, String> getCategory = t -> {
            if (t.getPredictedCategory() != null && !t.getPredictedCategory().trim().isEmpty()) {
                return t.getPredictedCategory();
            }
            return "Uncategorized";
        };
        
        // Calculate spending by category for current month (using ONLY predicted categories)
        Map<String, Double> currentMonthSpendingByCategory = currentMonthTransactions.stream()
            .filter(t -> t.getAmount() != null && t.getAmount() < 0)
            .collect(Collectors.groupingBy(
                getCategory,
                Collectors.summingDouble(t -> Math.abs(t.getAmount()))
            ));
        
        // Calculate spending by category for last month (using ONLY predicted categories)
        Map<String, Double> lastMonthSpendingByCategory = lastMonthTransactions.stream()
            .filter(t -> t.getAmount() != null && t.getAmount() < 0)
            .collect(Collectors.groupingBy(
                getCategory,
                Collectors.summingDouble(t -> Math.abs(t.getAmount()))
            ));
        
        // Create categoryMonthComparison using ONLY predicted categories
        Map<String, Map<String, Double>> categoryMonthComparison = new LinkedHashMap<>();
        java.util.Set<String> allCategories = new HashSet<>();
        
        for (YearMonth month : last6Months) {
            LocalDate firstOfMonthDate = month.atDay(1);
            LocalDate lastOfMonthDate = month.atEndOfMonth();
            
            List<Transaction> monthTransactions = allTransactions.stream()
                .filter(t -> !t.getDate().isBefore(firstOfMonthDate) && !t.getDate().isAfter(lastOfMonthDate))
                .filter(t -> t.getAmount() != null && t.getAmount() < 0)
                .collect(Collectors.toList());
            
            Map<String, Double> monthCategoryData = monthTransactions.stream()
                .collect(Collectors.groupingBy(
                    getCategory,
                    Collectors.summingDouble(t -> Math.abs(t.getAmount()))
                ));
            
            allCategories.addAll(monthCategoryData.keySet());
            String monthKey = month.toString();
            for (String category : monthCategoryData.keySet()) {
                categoryMonthComparison.computeIfAbsent(category, k -> new LinkedHashMap<>())
                    .put(monthKey, monthCategoryData.get(category));
            }
        }
        
        // Ensure all categories have entries for all 6 months
        for (String category : allCategories) {
            Map<String, Double> categoryData = categoryMonthComparison.computeIfAbsent(
                category, k -> new LinkedHashMap<>());
            for (YearMonth month : last6Months) {
                String monthKey = month.toString();
                categoryData.putIfAbsent(monthKey, 0.0);
            }
        }
        
        // Add attributes to model
        boolean hasCurrentMonthData = !currentMonthTransactions.isEmpty();
        
        model.addAttribute("currentMonthTotal", currentMonthTotal);
        model.addAttribute("lastMonthTotal", lastMonthTotal);
        model.addAttribute("totalSpending", hasCurrentMonthData ? currentMonthTotal : lastMonthTotal);
        model.addAttribute("percentageChange", percentageChange);
        // Create sorted lists for display (by value descending)
        List<Map.Entry<String, Double>> currentMonthSorted = currentMonthSpendingByCategory.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .collect(Collectors.toList());
        
        List<Map.Entry<String, Double>> lastMonthSorted = lastMonthSpendingByCategory.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .collect(Collectors.toList());
        
        model.addAttribute("currentMonthSpendingByCategory", currentMonthSpendingByCategory);
        model.addAttribute("currentMonthSpendingByCategorySorted", currentMonthSorted);
        model.addAttribute("lastMonthSpendingByCategory", lastMonthSpendingByCategory);
        model.addAttribute("lastMonthSpendingByCategorySorted", lastMonthSorted);
        model.addAttribute("spendingByCategory", hasCurrentMonthData ? currentMonthSpendingByCategory : lastMonthSpendingByCategory);
        model.addAttribute("categoryMonthComparison", categoryMonthComparison);
        model.addAttribute("monthNames", monthNames);
        model.addAttribute("last6Months", last6Months.stream().map(ym -> ym.toString()).collect(Collectors.toList()));
        model.addAttribute("recentTransactions", hasCurrentMonthData ? recentTransactions : lastMonthRecentTransactions);
        model.addAttribute("uncategorizedCount", uncategorizedCount);
        model.addAttribute("hasCurrentMonthData", hasCurrentMonthData);
        model.addAttribute("currentMonth", currentMonth);
        model.addAttribute("lastMonth", lastMonth);
        model.addAttribute("showingLastMonthData", !hasCurrentMonthData);
        
        return "dashboard_latest";
    }

    @GetMapping({"/dashboard/manual", "/dashboard_manual"})
    public String showDashboardManual(Model model) {
        // Get all transactions
        List<Transaction> allTransactions = transactionService.getAllTransactions();
        
        // Filter transactions for current month
        LocalDate now = LocalDate.now();
        YearMonth currentMonth = YearMonth.from(now);
        LocalDate firstOfMonth = currentMonth.atDay(1);
        
        List<Transaction> currentMonthTransactions = allTransactions.stream()
            .filter(t -> !t.getDate().isBefore(firstOfMonth))
            .collect(Collectors.toList());
            
        // Get transactions from previous month
        YearMonth lastMonth = currentMonth.minusMonths(1);
        LocalDate firstOfLastMonth = lastMonth.atDay(1);
        LocalDate lastOfLastMonth = lastMonth.atEndOfMonth();
        
        List<Transaction> lastMonthTransactions = allTransactions.stream()
            .filter(t -> !t.getDate().isBefore(firstOfLastMonth) && !t.getDate().isAfter(lastOfLastMonth))
            .collect(Collectors.toList());
        
        // Calculate total spending for current month (only negative amounts for expenses)
        double currentMonthTotal = currentMonthTransactions.stream()
            .filter(t -> t.getAmount() != null && t.getAmount() < 0)
            .mapToDouble(t -> Math.abs(t.getAmount()))
            .sum();
            
        // Calculate total spending for last month
        double lastMonthTotal = lastMonthTransactions.stream()
            .filter(t -> t.getAmount() != null && t.getAmount() < 0)
            .mapToDouble(t -> Math.abs(t.getAmount()))
            .sum();
            
        // Calculate percentage change
        double percentageChange = 0;
        if (lastMonthTotal != 0) {
            percentageChange = ((currentMonthTotal - lastMonthTotal) / lastMonthTotal) * 100;
        }
        
        // Calculate spending by category for last 6 months
        List<YearMonth> last6Months = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            last6Months.add(currentMonth.minusMonths(i));
        }
        Collections.reverse(last6Months); // Oldest to newest
        
        // Create list of month names for the chart
        List<String> monthNames = last6Months.stream()
            .map(ym -> ym.getMonth().toString().substring(0, 3) + " " + ym.getYear())
            .collect(Collectors.toList());
            
        // Get recent transactions (last 5, sorted by date descending)
        List<Transaction> recentTransactions = allTransactions.stream()
            .sorted(Comparator.comparing(Transaction::getDate).reversed())
            .limit(5)
            .collect(Collectors.toList());
            
        // Get recent transactions from last month
        List<Transaction> lastMonthRecentTransactions = lastMonthTransactions.stream()
            .sorted(Comparator.comparing(Transaction::getDate).reversed())
            .limit(5)
            .collect(Collectors.toList());
        
        // Get uncategorized transactions count (using manual categories)
        long uncategorizedCount = allTransactions.stream()
            .filter(t -> t.getCategoryName() == null || t.getCategoryName().trim().isEmpty())
            .count();
        
        // Helper function to get category (use ONLY manual categoryName)
        java.util.function.Function<Transaction, String> getCategory = t -> {
            if (t.getCategoryName() != null && !t.getCategoryName().trim().isEmpty()) {
                return t.getCategoryName();
            }
            return "Uncategorized";
        };
        
        // Calculate spending by category for current month (using ONLY manual categories)
        Map<String, Double> currentMonthSpendingByCategory = currentMonthTransactions.stream()
            .filter(t -> t.getAmount() != null && t.getAmount() < 0)
            .collect(Collectors.groupingBy(
                getCategory,
                Collectors.summingDouble(t -> Math.abs(t.getAmount()))
            ));
        
        // Calculate spending by category for last month (using ONLY manual categories)
        Map<String, Double> lastMonthSpendingByCategory = lastMonthTransactions.stream()
            .filter(t -> t.getAmount() != null && t.getAmount() < 0)
            .collect(Collectors.groupingBy(
                getCategory,
                Collectors.summingDouble(t -> Math.abs(t.getAmount()))
            ));
        
        // Create categoryMonthComparison using ONLY manual categories
        Map<String, Map<String, Double>> categoryMonthComparison = new LinkedHashMap<>();
        java.util.Set<String> allCategories = new HashSet<>();
        
        for (YearMonth month : last6Months) {
            LocalDate firstOfMonthDate = month.atDay(1);
            LocalDate lastOfMonthDate = month.atEndOfMonth();
            
            List<Transaction> monthTransactions = allTransactions.stream()
                .filter(t -> !t.getDate().isBefore(firstOfMonthDate) && !t.getDate().isAfter(lastOfMonthDate))
                .filter(t -> t.getAmount() != null && t.getAmount() < 0)
                .collect(Collectors.toList());
            
            Map<String, Double> monthCategoryData = monthTransactions.stream()
                .collect(Collectors.groupingBy(
                    getCategory,
                    Collectors.summingDouble(t -> Math.abs(t.getAmount()))
                ));
            
            allCategories.addAll(monthCategoryData.keySet());
            String monthKey = month.toString();
            for (String category : monthCategoryData.keySet()) {
                categoryMonthComparison.computeIfAbsent(category, k -> new LinkedHashMap<>())
                    .put(monthKey, monthCategoryData.get(category));
            }
        }
        
        // Ensure all categories have entries for all 6 months
        for (String category : allCategories) {
            Map<String, Double> categoryData = categoryMonthComparison.computeIfAbsent(
                category, k -> new LinkedHashMap<>());
            for (YearMonth month : last6Months) {
                String monthKey = month.toString();
                categoryData.putIfAbsent(monthKey, 0.0);
            }
        }
        
        // Add attributes to model
        boolean hasCurrentMonthData = !currentMonthTransactions.isEmpty();
        
        model.addAttribute("currentMonthTotal", currentMonthTotal);
        model.addAttribute("lastMonthTotal", lastMonthTotal);
        model.addAttribute("totalSpending", hasCurrentMonthData ? currentMonthTotal : lastMonthTotal);
        model.addAttribute("percentageChange", percentageChange);
        // Create sorted lists for display (by value descending)
        List<Map.Entry<String, Double>> currentMonthSorted = currentMonthSpendingByCategory.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .collect(Collectors.toList());
        
        List<Map.Entry<String, Double>> lastMonthSorted = lastMonthSpendingByCategory.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .collect(Collectors.toList());
        
        model.addAttribute("currentMonthSpendingByCategory", currentMonthSpendingByCategory);
        model.addAttribute("currentMonthSpendingByCategorySorted", currentMonthSorted);
        model.addAttribute("lastMonthSpendingByCategory", lastMonthSpendingByCategory);
        model.addAttribute("lastMonthSpendingByCategorySorted", lastMonthSorted);
        model.addAttribute("spendingByCategory", hasCurrentMonthData ? currentMonthSpendingByCategory : lastMonthSpendingByCategory);
        model.addAttribute("categoryMonthComparison", categoryMonthComparison);
        model.addAttribute("monthNames", monthNames);
        model.addAttribute("last6Months", last6Months.stream().map(ym -> ym.toString()).collect(Collectors.toList()));
        model.addAttribute("recentTransactions", hasCurrentMonthData ? recentTransactions : lastMonthRecentTransactions);
        model.addAttribute("uncategorizedCount", uncategorizedCount);
        model.addAttribute("hasCurrentMonthData", hasCurrentMonthData);
        model.addAttribute("currentMonth", currentMonth);
        model.addAttribute("lastMonth", lastMonth);
        model.addAttribute("showingLastMonthData", !hasCurrentMonthData);
        model.addAttribute("usingManualCategories", true); // Flag to indicate this is manual categories view
        
        return "dashboard_manual";
    }
}
