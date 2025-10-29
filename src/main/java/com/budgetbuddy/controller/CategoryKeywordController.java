package com.budgetbuddy.controller;

import com.budgetbuddy.model.CategoryKeyword;
import com.budgetbuddy.model.Transaction;
import com.budgetbuddy.service.CategoryKeywordService;
import com.budgetbuddy.service.TransactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.ui.Model;


import java.util.List;

@Controller
@RequestMapping("/categories")
public class CategoryKeywordController {

    @Autowired
    private CategoryKeywordService categoryKeywordService;

    @Autowired
    private TransactionService transactionService;

    @GetMapping({"", "/"})
    public String listAllCategories(Model model) {
        List<CategoryKeyword> categoryKeywords = categoryKeywordService.getAllCategories();  // Fetch categories from service
        model.addAttribute("categoryKeywords", categoryKeywords);  // Add categories to the model
        return "categories/list";  // Return the list view (Thymeleaf template)
    }

    @PostMapping("/delete/{id}")
    public String deleteCategoryKeyword(@PathVariable Long id) {
        categoryKeywordService.deleteCategoryKeywordById(id); // Delete the keyword by ID
        return "redirect:/categories/"; // Redirect back to the category keywords list
    }

    @GetMapping("/{categoryName}")
    public List<CategoryKeyword> getKeywordsByCategory(@PathVariable String categoryName) {
        return categoryKeywordService.getKeywordsByCategory(categoryName);
    }

 
    @GetMapping("/add")
    public String showAddCategoryForm(Model model) {
        model.addAttribute("categoryKeyword", new CategoryKeyword());  // Ensure you're using 'categoryKeyword' as the model attribute

        List<Transaction> uncategorizedTransactions = transactionService.getUncategorizedTransactions();

        // Add the uncategorized transactions to the model
        model.addAttribute("uncategorizedTransactions", uncategorizedTransactions);

        return "categories/add";  // Ensure it's returning the correct template name
    }


    @PostMapping("/add")
    public String addCategoryKeyword(@ModelAttribute CategoryKeyword categoryKeyword) {

        String keyword = categoryKeyword.getKeyword().toLowerCase();

        // Check if the keyword already exists in the database
        if (categoryKeywordService.existsByKeyword(keyword)) {
            // Optionally, you can add an error message to indicate that the keyword already exists
            return "redirect:/categories/list?error=duplicate";  // Redirect with error message if duplicate found
        }

        categoryKeywordService.saveCategoryKeyword(categoryKeyword);  // Save category keyword to database
        return "redirect:/categories/list";  // Redirect to the list of categories after saving
    }

    @GetMapping("/list")
    public String listCategories(Model model) {
        List<CategoryKeyword> categoryKeywords = categoryKeywordService.getAllCategories(); // Fetch from DB
        model.addAttribute("categoryKeywords", categoryKeywords);
        return "categories/list";
    }
}
