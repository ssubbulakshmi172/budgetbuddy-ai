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
        
        // Group transactions by month and category
        Map<String, Map<String, Double>> categoryMonthComparison = new LinkedHashMap<>();
        java.util.Set<String> allCategories = new HashSet<>();
        
        // Process each of the last 6 months
        for (YearMonth month : last6Months) {
            LocalDate firstOfMonthDate = month.atDay(1);
            LocalDate lastOfMonthDate = month.atEndOfMonth();
            
            List<Transaction> monthTransactions = allTransactions.stream()
                .filter(t -> !t.getDate().isBefore(firstOfMonthDate) && !t.getDate().isAfter(lastOfMonthDate))
                .filter(t -> t.getAmount() != null && t.getAmount() < 0)
                .collect(Collectors.toList());
            
            // Group by category for this month
            Map<String, Double> monthCategoryData = monthTransactions.stream()
                .filter(t -> t.getCategoryName() != null && !t.getCategoryName().isEmpty())
                .collect(Collectors.groupingBy(
                    Transaction::getCategoryName,
                    Collectors.summingDouble(t -> Math.abs(t.getAmount()))
                ));
            
            // Track all categories found
            allCategories.addAll(monthCategoryData.keySet());
            
            // Store this month's data
            String monthKey = month.toString();
            for (String category : monthCategoryData.keySet()) {
                categoryMonthComparison.computeIfAbsent(category, k -> new LinkedHashMap<>())
                    .put(monthKey, monthCategoryData.get(category));
            }
        }
        
        // Ensure all categories have entries for all 6 months (with 0 if missing)
        for (String category : allCategories) {
            Map<String, Double> categoryData = categoryMonthComparison.computeIfAbsent(
                category, k -> new LinkedHashMap<>());
            for (YearMonth month : last6Months) {
                String monthKey = month.toString();
                categoryData.putIfAbsent(monthKey, 0.0);
            }
        }
        
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
        
        // Get uncategorized transactions count
        long uncategorizedCount = allTransactions.stream()
            .filter(t -> t.getCategoryName() == null || t.getCategoryName().trim().isEmpty())
            .count();
        
        // Add attributes to model
        boolean hasCurrentMonthData = !currentMonthTransactions.isEmpty();
        
        model.addAttribute("totalSpending", hasCurrentMonthData ? currentMonthTotal : lastMonthTotal);
        model.addAttribute("percentageChange", percentageChange);
        model.addAttribute("spendingByCategory", hasCurrentMonthData ? 
            currentMonthTransactions.stream()
                .filter(t -> t.getCategoryName() != null && !t.getCategoryName().isEmpty() && t.getAmount() < 0)
                .collect(Collectors.groupingBy(
                    Transaction::getCategoryName,
                    Collectors.summingDouble(t -> Math.abs(t.getAmount()))
                )) : 
            lastMonthTransactions.stream()
                .filter(t -> t.getCategoryName() != null && !t.getCategoryName().isEmpty() && t.getAmount() < 0)
                .collect(Collectors.groupingBy(
                    Transaction::getCategoryName,
                    Collectors.summingDouble(t -> Math.abs(t.getAmount()))
                )));
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
}
