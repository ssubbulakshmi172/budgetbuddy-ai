package com.budgetbuddy.controller;

import com.budgetbuddy.model.Transaction;
import com.budgetbuddy.service.TransactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.Collectors;

@Controller
public class HomeController {

    @Autowired
    private TransactionService transactionService;

    @GetMapping("/")
    public String home(Model model) {
        List<Transaction> allTransactions = transactionService.getAllTransactions();
        
        // Calculate current month financials
        LocalDate now = LocalDate.now();
        YearMonth currentMonth = YearMonth.from(now);
        LocalDate firstOfMonth = currentMonth.atDay(1);
        
        List<Transaction> currentMonthTransactions = allTransactions.stream()
            .filter(t -> !t.getDate().isBefore(firstOfMonth))
            .collect(Collectors.toList());
        
        // Calculate income (positive amounts)
        double totalIncome = currentMonthTransactions.stream()
            .filter(t -> t.getAmount() != null && t.getAmount() > 0)
            .mapToDouble(Transaction::getAmount)
            .sum();
        
        // Calculate expenses (negative amounts converted to positive)
        double totalExpenses = currentMonthTransactions.stream()
            .filter(t -> t.getAmount() != null && t.getAmount() < 0)
            .mapToDouble(t -> Math.abs(t.getAmount()))
            .sum();
        
        // Calculate savings (income - expenses)
        double totalSavings = totalIncome - totalExpenses;
        
        // Calculate percentage of income saved
        double savingsPercentage = totalIncome > 0 ? (totalSavings / totalIncome) * 100 : 0;
        
        // Transaction count
        long transactionCount = allTransactions.size();
        
        // Add to model
        model.addAttribute("totalIncome", totalIncome);
        model.addAttribute("totalExpenses", totalExpenses);
        model.addAttribute("totalSavings", totalSavings);
        model.addAttribute("savingsPercentage", savingsPercentage);
        model.addAttribute("transactionCount", transactionCount);
        
        return "home";
    }
}
