package com.budgetbuddy.service;

import com.budgetbuddy.model.CategoryKeyword;
import com.budgetbuddy.repository.CategoryKeywordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CategoryKeywordService {

    @Autowired
    private CategoryKeywordRepository categoryKeywordRepository;

    public List<CategoryKeyword> getAllCategories() {
        return categoryKeywordRepository.findAll();
    }

    public List<CategoryKeyword> getKeywordsByCategory(String categoryName) {
        return categoryKeywordRepository.findByCategoryName(categoryName);
    }

    public void saveCategoryKeyword(CategoryKeyword categoryKeyword) {
        categoryKeywordRepository.save(categoryKeyword);
    }

    public boolean existsByKeyword(String keyword) {
        return categoryKeywordRepository.existsByKeyword(keyword);
    }
    public void deleteCategoryKeywordById(Long id) {
        categoryKeywordRepository.deleteById(id); // Deletes the record by ID
    }
    public List<String> getDistinctCategories() {
        return categoryKeywordRepository.findDistinctCategoryNames();
    }

    public CategoryKeyword getCategoryKeywordById(Long id) {
        return categoryKeywordRepository.findById(id).orElse(null);
    }
    
    public List<CategoryKeyword> getTaxonomyCategories() {
        // Get categories from category_keyword table where categoriesFor = 'Taxonomy'
        List<CategoryKeyword> taxonomy = categoryKeywordRepository.findByCategoriesFor("Taxonomy");
        // Return empty list if null (defensive coding)
        return taxonomy != null ? taxonomy : new java.util.ArrayList<>();
    }
    
    public List<CategoryKeyword> getManualCategories() {
        // Get categories from category_keyword table where categoriesFor = 'Manual'
        List<CategoryKeyword> manual = categoryKeywordRepository.findByCategoriesFor("Manual");
        // Return empty list if null (defensive coding)
        if (manual == null) {
            manual = new java.util.ArrayList<>();
        }
        // Also include categories where categoriesFor is null, empty, or not "Taxonomy"/"Manual"/"Corrected" (legacy data)
        List<CategoryKeyword> all = categoryKeywordRepository.findAll();
        for (CategoryKeyword ck : all) {
            String categoriesFor = ck.getCategoriesFor();
            if (categoriesFor == null || 
                categoriesFor.trim().isEmpty() ||
                (!categoriesFor.equals("Taxonomy") && !categoriesFor.equals("Manual") && !categoriesFor.equals("Corrected"))) {
                // Check if not already in manual list
                boolean found = false;
                for (CategoryKeyword existing : manual) {
                    if (existing.getId().equals(ck.getId())) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    manual.add(ck);
                }
            }
        }
        return manual;
    }
    
    public List<CategoryKeyword> getCorrectedCategories() {
        // Get categories from category_keyword table where categoriesFor = 'Corrected'
        List<CategoryKeyword> corrected = categoryKeywordRepository.findByCategoriesFor("Corrected");
        // Return empty list if null (defensive coding)
        return corrected != null ? corrected : new java.util.ArrayList<>();
    }
    
    // Expose repository for TaxonomyLoaderService
    public CategoryKeywordRepository getCategoryKeywordRepository() {
        return categoryKeywordRepository;
    }
}
