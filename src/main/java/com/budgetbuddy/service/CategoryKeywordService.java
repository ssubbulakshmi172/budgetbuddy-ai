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

}
