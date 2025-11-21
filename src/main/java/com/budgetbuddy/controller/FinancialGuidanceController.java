package com.budgetbuddy.controller;

import com.budgetbuddy.model.*;
import com.budgetbuddy.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
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
    private FinancialAnalyticsService financialAnalyticsService;

    @Autowired
    private com.budgetbuddy.repository.TransactionRepository transactionRepository;

    @Autowired
    private com.budgetbuddy.event.FinancialGuidanceUpdateListener financialGuidanceUpdateListener;

    /**
     * Main guidance dashboard - shows new 6 financial guidance features
     * Automatically detects/calculates all insights on page load
     */
    @GetMapping("/dashboard")
    public String showGuidanceDashboard(@RequestParam(required = false) Long userId, Model model) {
        try {
            User user = getDefaultUser(userId);
            
            // Initialize all lists to avoid null pointer exceptions
            List<CategoryOverspendingAlert> categoryAlerts = new ArrayList<>();
            List<MoneyLeak> moneyLeaks = new ArrayList<>();
            List<Map<String, Object>> anomalies = new ArrayList<>();
            List<MoneyLeak> regularInvestments = new ArrayList<>();
            List<MoneyLeak> regularExpenses = new ArrayList<>();
            List<WeekendOverspending> weekendOverspending = new ArrayList<>();
            
            Map<String, Object> groceryVsEatingOut = new java.util.HashMap<>();
            Map<String, Object> investmentTracking = new java.util.HashMap<>();
            Map<String, Object> subscriptions = new java.util.HashMap<>();
            ObjectNode categoryTrends = null;
            SavingsProjection savingsProjection = null;
            
            String errorMessage = null;
            
            try {
                // 1. Category Overspending Alerts - Load existing data (not recalculate)
                categoryAlerts = categoryOverspendingService.getActiveAlerts(user);
                if (categoryAlerts == null) categoryAlerts = new ArrayList<>();
            } catch (Exception e) {
                errorMessage = "Error loading category alerts: " + e.getMessage();
                categoryAlerts = new ArrayList<>();
            }
            
            try {
                // 2. Top 3 Money Leaks (excluding investments)
                moneyLeaks = moneyLeakService.detectMoneyLeaks(user);
                if (moneyLeaks == null) moneyLeaks = new ArrayList<>();
            } catch (Exception e) {
                if (errorMessage == null) errorMessage = "";
                errorMessage += " Error loading money leaks: " + e.getMessage();
                moneyLeaks = new ArrayList<>();
            }
            
            try {
                // 2c. ML-based Anomaly Detection - Auto-load if user has enough data (>50 transactions)
                long transactionCount = transactionRepository.findAll().stream()
                    .filter(t -> t.getUser() != null && t.getUser().getId().equals(user.getId()))
                    .count();
                if (transactionCount > 50) {
                    anomalies = moneyLeakService.detectAnomalies(user);
                    if (anomalies == null) anomalies = new ArrayList<>();
                }
            } catch (Exception e) {
                // Silently skip anomaly detection on page load if it fails
                anomalies = new ArrayList<>();
            }
            
            try {
                // 2a. Regular Monthly Spending (including investments, shown separately)
                List<MoneyLeak> regularMonthlySpending = moneyLeakService.detectRegularMonthlySpending(user);
                if (regularMonthlySpending != null) {
                    regularInvestments = regularMonthlySpending.stream()
                        .filter(m -> m != null && m.getTitle() != null && m.getTitle().startsWith("Monthly Investment"))
                        .collect(Collectors.toList());
                    regularExpenses = regularMonthlySpending.stream()
                        .filter(m -> m != null && m.getTitle() != null && m.getTitle().startsWith("Monthly Expense"))
                        .collect(Collectors.toList());
                }
            } catch (Exception e) {
                if (errorMessage == null) errorMessage = "";
                errorMessage += " Error loading regular spending: " + e.getMessage();
            }
            
            try {
                // 2b. New Rule-Based Analytics
                groceryVsEatingOut = financialAnalyticsService.analyzeGroceryVsEatingOut(user);
                if (groceryVsEatingOut == null) groceryVsEatingOut = new java.util.HashMap<>();
            } catch (Exception e) {
                if (errorMessage == null) errorMessage = "";
                errorMessage += " Error loading grocery analysis: " + e.getMessage();
            }
            
            try {
                investmentTracking = financialAnalyticsService.trackInvestments(user);
                if (investmentTracking == null) investmentTracking = new java.util.HashMap<>();
            } catch (Exception e) {
                if (errorMessage == null) errorMessage = "";
                errorMessage += " Error loading investment tracking: " + e.getMessage();
            }
            
            try {
                subscriptions = financialAnalyticsService.analyzeSubscriptions(user);
                if (subscriptions == null) subscriptions = new java.util.HashMap<>();
            } catch (Exception e) {
                if (errorMessage == null) errorMessage = "";
                errorMessage += " Error loading subscriptions: " + e.getMessage();
            }
            
            try {
                categoryTrends = financialAnalyticsService.getCategoryTrendVisualization(user);
            } catch (Exception e) {
                if (errorMessage == null) errorMessage = "";
                errorMessage += " Error loading category trends: " + e.getMessage();
            }
            
            try {
                // 3. Year-End Savings Projection - Load existing data (not recalculate)
                savingsProjection = savingsProjectionService.getLatestProjection(user);
            } catch (Exception e) {
                if (errorMessage == null) errorMessage = "";
                errorMessage += " Error loading savings projection: " + e.getMessage();
            }
            
            try {
                // 4. Weekend Overspending - Load existing data (not recalculate)
                weekendOverspending = weekendOverspendingService.getActiveAlerts(user);
                if (weekendOverspending == null) weekendOverspending = new ArrayList<>();
            } catch (Exception e) {
                if (errorMessage == null) errorMessage = "";
                errorMessage += " Error loading weekend overspending: " + e.getMessage();
            }
            
            // Add all attributes to model
            model.addAttribute("user", user);
            model.addAttribute("categoryAlerts", categoryAlerts);
            model.addAttribute("moneyLeaks", moneyLeaks);
            model.addAttribute("anomalies", anomalies);
            model.addAttribute("regularInvestments", regularInvestments);
            model.addAttribute("regularExpenses", regularExpenses);
            model.addAttribute("groceryVsEatingOut", groceryVsEatingOut);
            model.addAttribute("investmentTracking", investmentTracking);
            model.addAttribute("subscriptions", subscriptions);
            model.addAttribute("categoryTrends", categoryTrends);
            model.addAttribute("savingsProjection", savingsProjection);
            model.addAttribute("weekendOverspending", weekendOverspending);
            model.addAttribute("errorMessage", errorMessage);
            model.addAttribute("hasError", errorMessage != null && !errorMessage.isEmpty());
            
            return "guidance_dashboard";
            
        } catch (Exception e) {
            // Handle critical errors
            model.addAttribute("errorMessage", "Failed to load financial guidance: " + e.getMessage());
            model.addAttribute("hasError", true);
            model.addAttribute("categoryAlerts", new ArrayList<>());
            model.addAttribute("moneyLeaks", new ArrayList<>());
            model.addAttribute("anomalies", new ArrayList<>());
            model.addAttribute("regularInvestments", new ArrayList<>());
            model.addAttribute("regularExpenses", new ArrayList<>());
            model.addAttribute("weekendOverspending", new ArrayList<>());
            return "guidance_dashboard";
        }
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
     * Detect anomalies using ML (Isolation Forest)
     */
    @PostMapping("/anomalies/detect")
    @ResponseBody
    public List<Map<String, Object>> detectAnomalies(@RequestParam Long userId) {
        User user = userService.getUserById(userId);
        return moneyLeakService.detectAnomalies(user);
    }

    @GetMapping("/anomalies")
    @ResponseBody
    public List<Map<String, Object>> getAnomalies(@RequestParam Long userId) {
        User user = userService.getUserById(userId);
        return moneyLeakService.detectAnomalies(user);
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
     * Manual reload/refresh endpoint - triggers all financial guidance updates
     * Useful for testing or forcing a refresh without adding a transaction
     */
    @PostMapping("/reload")
    @ResponseBody
    public Map<String, Object> reloadFinancialGuidance(@RequestParam(required = false) Long userId) {
        User user = getDefaultUser(userId);
        
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("status", "started");
        result.put("userId", user.getId());
        result.put("timestamp", java.time.LocalDateTime.now().toString());
        
        try {
            // Force update (bypasses debounce) for manual reload
            financialGuidanceUpdateListener.forceUpdateFinancialGuidance(user);
            
            result.put("status", "completed");
            result.put("message", "✅ All financial guidance tables updated successfully");
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "❌ Error: " + e.getMessage());
            result.put("error", e.getClass().getSimpleName());
        }
        
        return result;
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

