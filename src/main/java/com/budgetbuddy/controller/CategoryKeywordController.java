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

        // Set default categoriesFor to "Manual" if not provided
        if (categoryKeyword.getCategoriesFor() == null || categoryKeyword.getCategoriesFor().trim().isEmpty()) {
            categoryKeyword.setCategoriesFor("Manual");
        }

        categoryKeywordService.saveCategoryKeyword(categoryKeyword);  // Save category keyword to database
        return "redirect:/categories";  // Redirect to the list of categories after saving
    }

    @GetMapping("/list")
    public String listCategories(Model model) {
        List<CategoryKeyword> categoryKeywords = categoryKeywordService.getAllCategories(); // Fetch from DB
        model.addAttribute("categoryKeywords", categoryKeywords);
        return "categories/list";
    }

    @GetMapping("/edit/{id}")
    public String showEditCategoryForm(@PathVariable Long id, Model model) {
        CategoryKeyword categoryKeyword = categoryKeywordService.getCategoryKeywordById(id);
        if (categoryKeyword == null) {
            return "redirect:/categories?error=notfound";
        }
        model.addAttribute("categoryKeyword", categoryKeyword);
        return "categories/edit";
    }

    @PostMapping("/edit/{id}")
    public String updateCategoryKeyword(@PathVariable Long id, 
                                        @ModelAttribute CategoryKeyword categoryKeyword) {
        CategoryKeyword existing = categoryKeywordService.getCategoryKeywordById(id);
        if (existing == null) {
            return "redirect:/categories?error=notfound";
        }

        // Check if keyword changed and if new keyword already exists
        String newKeyword = categoryKeyword.getKeyword().toLowerCase();
        if (!existing.getKeyword().equalsIgnoreCase(newKeyword) && 
            categoryKeywordService.existsByKeyword(newKeyword)) {
            return "redirect:/categories/edit/" + id + "?error=duplicate";
        }

        // Update fields
        existing.setCategoryName(categoryKeyword.getCategoryName());
        existing.setKeyword(newKeyword);
        
        // Update categoriesFor if provided, otherwise keep existing
        if (categoryKeyword.getCategoriesFor() != null && 
            !categoryKeyword.getCategoriesFor().trim().isEmpty()) {
            existing.setCategoriesFor(categoryKeyword.getCategoriesFor());
        } else if (existing.getCategoriesFor() == null) {
            existing.setCategoriesFor("Manual");
        }

        categoryKeywordService.saveCategoryKeyword(existing);
        return "redirect:/categories?success=updated";
    }

    @GetMapping("/{categoryName}")
    public List<CategoryKeyword> getKeywordsByCategory(@PathVariable String categoryName) {
        return categoryKeywordService.getKeywordsByCategory(categoryName);
    }
}
