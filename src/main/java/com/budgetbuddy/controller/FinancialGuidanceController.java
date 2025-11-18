package com.budgetbuddy.controller;

import com.budgetbuddy.model.*;
import com.budgetbuddy.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Controller
@RequestMapping("/guidance")
public class FinancialGuidanceController {

    @Autowired
    private UserService userService;

    @Autowired
    private CategoryOverspendingService categoryOverspendingService;

    @Autowired
    private MoneyLeakService moneyLeakService;

    @Autowired
    private SavingsProjectionService savingsProjectionService;

    @Autowired
    private WeekendOverspendingService weekendOverspendingService;

    @Autowired
    private SalaryWeekService salaryWeekService;

    @Autowired
    private MonthEndScarcityService monthEndScarcityService;

    @Autowired
    private FinancialAnalyticsService financialAnalyticsService;

    /**
     * Main guidance dashboard - shows new 6 financial guidance features
     * Automatically detects/calculates all insights on page load
     */
    @GetMapping("/dashboard")
    public String showGuidanceDashboard(@RequestParam(required = false) Long userId, Model model) {
        User user = getDefaultUser(userId);
        
        // Auto-detect/calculate all insights automatically
        // 1. Category Overspending Alerts
        List<CategoryOverspendingAlert> categoryAlerts = categoryOverspendingService.detectOverspending(user);
        
        // 2. Top 3 Money Leaks (excluding investments)
        List<MoneyLeak> moneyLeaks = moneyLeakService.detectMoneyLeaks(user);
        
        // 2a. Regular Monthly Spending (including investments, shown separately)
        List<MoneyLeak> regularMonthlySpending = moneyLeakService.detectRegularMonthlySpending(user);
        List<MoneyLeak> regularInvestments = regularMonthlySpending.stream()
            .filter(m -> m.getTitle() != null && m.getTitle().startsWith("Monthly Investment"))
            .collect(Collectors.toList());
        List<MoneyLeak> regularExpenses = regularMonthlySpending.stream()
            .filter(m -> m.getTitle() != null && m.getTitle().startsWith("Monthly Expense"))
            .collect(Collectors.toList());
        
        // 2b. New Rule-Based Analytics
        Map<String, Object> groceryVsEatingOut = financialAnalyticsService.analyzeGroceryVsEatingOut(user);
        Map<String, Object> investmentTracking = financialAnalyticsService.trackInvestments(user);
        Map<String, Object> subscriptions = financialAnalyticsService.analyzeSubscriptions(user);
        ObjectNode categoryTrends = financialAnalyticsService.getCategoryTrendVisualization(user);
        
        // 3. Year-End Savings Projection
        SavingsProjection savingsProjection = savingsProjectionService.calculateYearEndSavings(user);
        
        // 4. Weekend Overspending
        List<WeekendOverspending> weekendOverspending = weekendOverspendingService.detectWeekendOverspending(user);
        
        // 5. Salary Week Analysis
        SalaryWeekAnalysis salaryWeekAnalysis = salaryWeekService.analyzeSalaryWeek(user);
        List<SalaryWeekAnalysis> salaryWeekAnomalies = salaryWeekService.getAnomalies(user);
        
        // 6. Month-End Scarcity
        MonthEndScarcity monthEndScarcity = monthEndScarcityService.detectMonthEndScarcity(user);
        
        model.addAttribute("user", user);
        model.addAttribute("categoryAlerts", categoryAlerts);
        model.addAttribute("moneyLeaks", moneyLeaks);
        model.addAttribute("regularInvestments", regularInvestments);
        model.addAttribute("regularExpenses", regularExpenses);
        model.addAttribute("groceryVsEatingOut", groceryVsEatingOut);
        model.addAttribute("investmentTracking", investmentTracking);
        model.addAttribute("subscriptions", subscriptions);
        model.addAttribute("categoryTrends", categoryTrends);
        model.addAttribute("savingsProjection", savingsProjection);
        model.addAttribute("weekendOverspending", weekendOverspending);
        model.addAttribute("salaryWeekAnomalies", salaryWeekAnomalies);
        model.addAttribute("monthEndScarcity", monthEndScarcity);
        
        return "guidance_dashboard";
    }


    // ========== New Financial Guidance Features ==========

    /**
     * Detect and get category overspending alerts
     */
    @PostMapping("/category-overspending/detect")
    @ResponseBody
    public List<CategoryOverspendingAlert> detectCategoryOverspending(@RequestParam Long userId) {
        User user = userService.getUserById(userId);
        return categoryOverspendingService.detectOverspending(user);
    }

    @GetMapping("/category-overspending")
    @ResponseBody
    public List<CategoryOverspendingAlert> getCategoryOverspending(@RequestParam Long userId) {
        User user = userService.getUserById(userId);
        return categoryOverspendingService.getActiveAlerts(user);
    }

    /**
     * Detect and get top 3 money leaks
     */
    @PostMapping("/money-leaks/detect")
    @ResponseBody
    public List<MoneyLeak> detectMoneyLeaks(@RequestParam Long userId) {
        User user = userService.getUserById(userId);
        return moneyLeakService.detectMoneyLeaks(user);
    }

    @GetMapping("/money-leaks")
    @ResponseBody
    public List<MoneyLeak> getMoneyLeaks(@RequestParam Long userId) {
        User user = userService.getUserById(userId);
        return moneyLeakService.getTopMoneyLeaks(user);
    }

    /**
     * Calculate and get year-end savings projection
     */
    @PostMapping("/savings-projection/calculate")
    @ResponseBody
    public SavingsProjection calculateSavingsProjection(@RequestParam Long userId) {
        User user = userService.getUserById(userId);
        return savingsProjectionService.calculateYearEndSavings(user);
    }

    @GetMapping("/savings-projection")
    @ResponseBody
    public SavingsProjection getSavingsProjection(@RequestParam Long userId) {
        User user = userService.getUserById(userId);
        return savingsProjectionService.getLatestProjection(user);
    }

    /**
     * Detect and get weekend overspending
     */
    @PostMapping("/weekend-overspending/detect")
    @ResponseBody
    public List<WeekendOverspending> detectWeekendOverspending(@RequestParam Long userId) {
        User user = userService.getUserById(userId);
        return weekendOverspendingService.detectWeekendOverspending(user);
    }

    @GetMapping("/weekend-overspending")
    @ResponseBody
    public List<WeekendOverspending> getWeekendOverspending(@RequestParam Long userId) {
        User user = userService.getUserById(userId);
        return weekendOverspendingService.getActiveAlerts(user);
    }

    /**
     * Analyze salary week spending
     */
    @PostMapping("/salary-week/analyze")
    @ResponseBody
    public SalaryWeekAnalysis analyzeSalaryWeek(@RequestParam Long userId) {
        User user = userService.getUserById(userId);
        return salaryWeekService.analyzeSalaryWeek(user);
    }

    @GetMapping("/salary-week/anomalies")
    @ResponseBody
    public List<SalaryWeekAnalysis> getSalaryWeekAnomalies(@RequestParam Long userId) {
        User user = userService.getUserById(userId);
        return salaryWeekService.getAnomalies(user);
    }

    /**
     * Detect month-end scarcity behavior
     */
    @PostMapping("/month-end-scarcity/detect")
    @ResponseBody
    public MonthEndScarcity detectMonthEndScarcity(@RequestParam Long userId) {
        User user = userService.getUserById(userId);
        return monthEndScarcityService.detectMonthEndScarcity(user);
    }

    @GetMapping("/month-end-scarcity")
    @ResponseBody
    public MonthEndScarcity getMonthEndScarcity(@RequestParam Long userId) {
        User user = userService.getUserById(userId);
        return monthEndScarcityService.getLatestAnalysis(user);
    }

    // Helper method - in production, get from session/authentication
    private User getDefaultUser(Long userId) {
        if (userId != null) {
            User user = userService.getUserById(userId);
            if (user != null) {
                return user;
            }
        }
        // Fallback: get first user (in production, get from authentication context)
        List<User> users = userService.getAllUsers();
        if (users.isEmpty()) {
            throw new RuntimeException("No users found. Please create a user first.");
        }
        return users.get(0);
    }
}

