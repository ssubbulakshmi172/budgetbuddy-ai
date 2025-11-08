package com.budgetbuddy.controller;

import com.budgetbuddy.model.*;
import com.budgetbuddy.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/guidance")
public class FinancialGuidanceController {

    @Autowired
    private SpendingPatternService spendingPatternService;

    @Autowired
    private TrendAnalysisService trendAnalysisService;

    @Autowired
    private SpendingPredictionService spendingPredictionService;

    @Autowired
    private FinancialNudgeService financialNudgeService;

    @Autowired
    private UserService userService;

    /**
     * Main guidance dashboard - shows patterns, predictions, trends, and nudges
     */
    @GetMapping("/dashboard")
    public String showGuidanceDashboard(@RequestParam(required = false) Long userId, Model model) {
        User user = getDefaultUser(userId);
        
        // Detect patterns
        List<SpendingPattern> patterns = spendingPatternService.getActivePatterns(user);
        
        // Analyze trends
        TrendAnalysisService.TrendAnalysisResult trends = trendAnalysisService.analyzeTrends(user);
        
        // Get predictions for next month
        LocalDate nextMonthStart = LocalDate.now().withDayOfMonth(1).plusMonths(1);
        LocalDate nextMonthEnd = nextMonthStart.withDayOfMonth(nextMonthStart.lengthOfMonth());
        List<SpendingPrediction> predictions = spendingPredictionService.predictFutureSpending(
            user, nextMonthStart, nextMonthEnd
        );
        
        // Auto-generate nudges if none exist (first time visit or after refresh)
        List<FinancialNudge> nudges = financialNudgeService.getActiveNudges(user);
        if (nudges.isEmpty() && (!patterns.isEmpty() || !predictions.isEmpty())) {
            // Auto-generate nudges if we have patterns or predictions but no nudges
            financialNudgeService.generateNudges(user);
            nudges = financialNudgeService.getActiveNudges(user);
        }
        
        List<FinancialNudge> unreadNudges = financialNudgeService.getUnreadNudges(user);
        
        model.addAttribute("user", user);
        model.addAttribute("patterns", patterns);
        model.addAttribute("trends", trends.getTrends());
        model.addAttribute("spikes", trends.getSpikes());
        model.addAttribute("dips", trends.getDips());
        model.addAttribute("predictions", predictions);
        model.addAttribute("nudges", nudges);
        model.addAttribute("unreadNudges", unreadNudges);
        model.addAttribute("unreadCount", unreadNudges.size());
        
        return "guidance_dashboard";
    }

    /**
     * API endpoint to refresh patterns
     */
    @PostMapping("/patterns/refresh")
    @ResponseBody
    public String refreshPatterns(@RequestParam Long userId) {
        User user = userService.getUserById(userId);
        spendingPatternService.refreshPatterns(user);
        return "Patterns refreshed successfully";
    }

    /**
     * API endpoint to generate nudges
     */
    @PostMapping("/nudges/generate")
    @ResponseBody
    public String generateNudges(@RequestParam Long userId) {
        User user = userService.getUserById(userId);
        financialNudgeService.generateNudges(user);
        return "Nudges generated successfully";
    }

    /**
     * API endpoint to mark nudge as read
     */
    @PostMapping("/nudges/{nudgeId}/read")
    @ResponseBody
    public String markNudgeAsRead(@PathVariable Long nudgeId) {
        financialNudgeService.markAsRead(nudgeId);
        return "Nudge marked as read";
    }

    /**
     * API endpoint to dismiss nudge
     */
    @PostMapping("/nudges/{nudgeId}/dismiss")
    @ResponseBody
    public String dismissNudge(@PathVariable Long nudgeId) {
        financialNudgeService.dismissNudge(nudgeId);
        return "Nudge dismissed";
    }

    /**
     * Get patterns API
     */
    @GetMapping("/patterns")
    @ResponseBody
    public List<SpendingPattern> getPatterns(@RequestParam Long userId) {
        User user = userService.getUserById(userId);
        return spendingPatternService.getActivePatterns(user);
    }

    /**
     * Get predictions API
     */
    @GetMapping("/predictions")
    @ResponseBody
    public List<SpendingPrediction> getPredictions(
            @RequestParam Long userId,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        User user = userService.getUserById(userId);
        
        if (startDate == null) {
            startDate = LocalDate.now().withDayOfMonth(1).plusMonths(1);
        }
        if (endDate == null) {
            endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
        }
        
        return spendingPredictionService.predictFutureSpending(user, startDate, endDate);
    }

    /**
     * Get nudges API
     */
    @GetMapping("/nudges")
    @ResponseBody
    public List<FinancialNudge> getNudges(@RequestParam Long userId) {
        User user = userService.getUserById(userId);
        return financialNudgeService.getActiveNudges(user);
    }

    /**
     * Get overspending risks API
     */
    @GetMapping("/risks")
    @ResponseBody
    public List<SpendingPrediction> getOverspendingRisks(@RequestParam Long userId) {
        User user = userService.getUserById(userId);
        return spendingPredictionService.getOverspendingRisks(user);
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

