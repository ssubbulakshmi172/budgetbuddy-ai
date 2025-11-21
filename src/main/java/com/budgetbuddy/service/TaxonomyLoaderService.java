package com.budgetbuddy.service;

import com.budgetbuddy.model.CategoryKeyword;
import com.budgetbuddy.util.NarrationPreprocessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;
import jakarta.annotation.PostConstruct;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.*;

/**
 * Service to load taxonomy categories from categories.yml and corrected categories from user_corrections.json into the database.
 * Runs on application startup to populate category_keyword table with categoriesFor = "Taxonomy" and "Corrected".
 */
@Service
public class TaxonomyLoaderService {

    private static final Logger logger = LoggerFactory.getLogger(TaxonomyLoaderService.class);

    @Autowired
    private CategoryKeywordService categoryKeywordService;

    @PostConstruct
    public void loadTaxonomyOnStartup() {
        logger.info("🔄 Starting taxonomy loader...");
        try {
            loadTaxonomyFromYaml();
        } catch (Exception e) {
            logger.error("❌ Error loading taxonomy on startup: {}", e.getMessage(), e);
        }
        
        logger.info("🔄 Starting corrections loader...");
        try {
            loadCorrectionsFromJson();
        } catch (Exception e) {
            logger.error("❌ Error loading corrections on startup: {}", e.getMessage(), e);
        }
    }

    /**
     * Load taxonomy categories from categories.yml into the database.
     * Parses YAML directly in Java using SnakeYAML (included with Spring Boot).
     */
    @SuppressWarnings("unchecked")
    public void loadTaxonomyFromYaml() {
        try {
            String projectRoot = System.getProperty("user.dir");
            String yamlPath = "mybudget-ai/categories.yml";
            File yamlFile = new File(projectRoot, yamlPath);
            
            if (!yamlFile.exists()) {
                logger.warn("⚠️ Categories YAML file not found: {}", yamlFile.getAbsolutePath());
                return;
            }

            logger.info("📝 Parsing YAML file: {}", yamlFile.getAbsolutePath());

            // Parse YAML using SnakeYAML (included with Spring Boot)
            Yaml yaml = new Yaml();
            Map<String, Object> data;
            try (InputStream inputStream = new FileInputStream(yamlFile)) {
                data = yaml.load(inputStream);
            }

            if (data == null || !data.containsKey("categories")) {
                logger.warn("⚠️ No 'categories' section found in YAML file");
                return;
            }

            List<Map<String, Object>> categories = (List<Map<String, Object>>) data.get("categories");
            List<Map<String, String>> keywordsList = new ArrayList<>();

            // Extract keywords from categories and subcategories
            for (Map<String, Object> category : categories) {
                String categoryName = (String) category.get("name");
                if (categoryName == null || categoryName.trim().isEmpty()) {
                    continue;
                }

                List<Map<String, Object>> subcategories = (List<Map<String, Object>>) category.get("subcategories");
                if (subcategories == null || subcategories.isEmpty()) {
                    // If no subcategories, check if category has keywords directly
                    List<String> keywords = (List<String>) category.get("keywords");
                    if (keywords != null) {
                        for (String keyword : keywords) {
                            if (keyword != null && !keyword.trim().isEmpty()) {
                                keywordsList.add(createKeywordEntry(categoryName, keyword));
                            }
                        }
                    }
                } else {
                    // Process each subcategory
                    for (Map<String, Object> subcategory : subcategories) {
                        String subcategoryName = (String) subcategory.get("name");
                        List<String> keywords = (List<String>) subcategory.get("keywords");
                        
                        if (keywords == null) {
                            continue;
                        }

                        // Build full category name: "Category / Subcategory"
                        String fullCategoryName = subcategoryName != null && !subcategoryName.trim().isEmpty()
                            ? categoryName + " / " + subcategoryName
                            : categoryName;

                        for (String keyword : keywords) {
                            if (keyword != null && !keyword.trim().isEmpty()) {
                                keywordsList.add(createKeywordEntry(fullCategoryName, keyword));
                            }
                        }
                    }
                }
            }

            logger.info("✅ Loaded {} keywords from YAML", keywordsList.size());

            // Save to database
            int saved = 0;
            int updated = 0;
            int skipped = 0;

            for (Map<String, String> keywordData : keywordsList) {
                String categoryName = keywordData.get("categoryName");
                String keyword = keywordData.get("keyword");

                if (categoryName == null || keyword == null || categoryName.trim().isEmpty() || keyword.trim().isEmpty()) {
                    skipped++;
                    continue;
                }

                // Check if keyword already exists
                Optional<CategoryKeyword> existing = categoryKeywordService.getCategoryKeywordRepository()
                    .findByKeyword(keyword.toLowerCase().trim());

                if (existing.isPresent()) {
                    // Update existing keyword to ensure it has categoriesFor = "Taxonomy"
                    CategoryKeyword existingKeyword = existing.get();
                    if (!"Taxonomy".equals(existingKeyword.getCategoriesFor())) {
                        existingKeyword.setCategoriesFor("Taxonomy");
                        existingKeyword.setCategoryName(categoryName);
                        categoryKeywordService.saveCategoryKeyword(existingKeyword);
                        updated++;
                    } else {
                        skipped++;
                    }
                } else {
                    // Create new keyword
                    CategoryKeyword newKeyword = new CategoryKeyword();
                    newKeyword.setCategoryName(categoryName);
                    newKeyword.setKeyword(keyword.toLowerCase().trim());
                    newKeyword.setCategoriesFor("Taxonomy");
                    categoryKeywordService.saveCategoryKeyword(newKeyword);
                    saved++;
                }
            }

            logger.info("✅ Taxonomy loading complete: {} saved, {} updated, {} skipped", saved, updated, skipped);
        } catch (Exception e) {
            logger.error("❌ Error loading taxonomy from YAML: {}", e.getMessage(), e);
        }
    }

    /**
     * Create a keyword entry map for database insertion
     */
    private Map<String, String> createKeywordEntry(String categoryName, String keyword) {
        Map<String, String> entry = new HashMap<>();
        entry.put("categoryName", categoryName);
        entry.put("keyword", keyword);
        entry.put("categoriesFor", "Taxonomy");
        return entry;
    }

    /**
     * Load corrected categories from user_corrections.json into the database.
     * Parses JSON and saves cleaned narrations as keywords with categoriesFor = "Corrected".
     */
    public void loadCorrectionsFromJson() {
        try {
            String projectRoot = System.getProperty("user.dir");
            String jsonPath = "mybudget-ai/user_corrections.json";
            File jsonFile = new File(projectRoot, jsonPath);
            
            if (!jsonFile.exists()) {
                logger.warn("⚠️ Corrections JSON file not found: {}", jsonFile.getAbsolutePath());
                return;
            }

            logger.info("📝 Parsing corrections JSON file: {}", jsonFile.getAbsolutePath());

            // Parse JSON using Jackson (included with Spring Boot)
            ObjectMapper mapper = new ObjectMapper();
            List<Map<String, Object>> corrections;
            try (InputStream inputStream = new FileInputStream(jsonFile)) {
                corrections = mapper.readValue(inputStream, new TypeReference<List<Map<String, Object>>>() {});
            }

            if (corrections == null || corrections.isEmpty()) {
                logger.info("ℹ️ No corrections found in JSON file");
                return;
            }

            logger.info("✅ Loaded {} corrections from JSON", corrections.size());

            // Save to database
            int saved = 0;
            int updated = 0;
            int skipped = 0;

            for (Map<String, Object> correction : corrections) {
                String narration = (String) correction.get("narration");
                String category = (String) correction.get("category");

                if (narration == null || narration.trim().isEmpty() || 
                    category == null || category.trim().isEmpty()) {
                    skipped++;
                    continue;
                }

                // Clean narration using the same logic as TransactionService
                String cleanedNarration = NarrationPreprocessor.cleanNarration(narration);
                if (cleanedNarration == null || cleanedNarration.trim().isEmpty()) {
                    skipped++;
                    continue;
                }

                // Normalize narration for use as keyword
                String narrationKeyword = NarrationPreprocessor.normalizeForKeyword(cleanedNarration);

                // Save ONLY the narration as a keyword entry (not the category name - that's redundant)
                Optional<CategoryKeyword> existingNarration = categoryKeywordService.getCategoryKeywordRepository()
                    .findByKeyword(narrationKeyword);

                if (existingNarration.isPresent()) {
                    // Update existing keyword to ensure it has categoriesFor = "Corrected"
                    CategoryKeyword existingKeyword = existingNarration.get();
                    if (!"Corrected".equals(existingKeyword.getCategoriesFor()) || 
                        !category.equals(existingKeyword.getCategoryName())) {
                        existingKeyword.setCategoriesFor("Corrected");
                        existingKeyword.setCategoryName(category);
                        categoryKeywordService.saveCategoryKeyword(existingKeyword);
                        updated++;
                    } else {
                        skipped++;
                    }
                } else {
                    // Create new keyword entry with the narration
                    CategoryKeyword newKeyword = new CategoryKeyword();
                    newKeyword.setCategoryName(category);
                    newKeyword.setKeyword(narrationKeyword);
                    newKeyword.setCategoriesFor("Corrected");
                    categoryKeywordService.saveCategoryKeyword(newKeyword);
                    saved++;
                }
                
                // NOTE: We do NOT save the category name itself as a keyword because:
                // 1. It's redundant (category name is already in categoryName field)
                // 2. It creates unwanted duplicate entries in the UI
                // 3. We only need narration keywords for matching transactions
            }

            logger.info("✅ Corrections loading complete: {} saved, {} updated, {} skipped", saved, updated, skipped);
        } catch (Exception e) {
            logger.error("❌ Error loading corrections from JSON: {}", e.getMessage(), e);
        }
    }
}

