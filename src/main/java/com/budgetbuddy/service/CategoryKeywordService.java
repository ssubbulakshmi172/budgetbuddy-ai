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
        return categoryKeywordRepository.findByCategoriesFor("Taxonomy");
    }
    
    public List<CategoryKeyword> getManualCategories() {
        List<CategoryKeyword> manual = categoryKeywordRepository.findByCategoriesFor("Manual");
        // Also include categories where categoriesFor is null, empty, or not "Taxonomy" (legacy data)
        List<CategoryKeyword> all = categoryKeywordRepository.findAll();
        for (CategoryKeyword ck : all) {
            String categoriesFor = ck.getCategoriesFor();
            if (categoriesFor == null || 
                categoriesFor.trim().isEmpty() ||
                (!categoriesFor.equals("Taxonomy") && !categoriesFor.equals("Manual"))) {
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
    
    public List<String> getTaxonomyCategoryNames() {
        return categoryKeywordRepository.findDistinctCategoryNamesByCategoriesFor("Taxonomy");
    }
    
    public List<String> getManualCategoryNames() {
        List<String> allNames = categoryKeywordRepository.findDistinctCategoryNames();
        List<String> taxonomyNames = getTaxonomyCategoryNames();
        allNames.removeAll(taxonomyNames);
        return allNames;
    }

}
