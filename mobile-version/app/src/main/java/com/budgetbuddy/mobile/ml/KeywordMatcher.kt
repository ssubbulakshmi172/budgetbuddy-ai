package com.budgetbuddy.mobile.ml

import com.budgetbuddy.mobile.data.dao.CategoryKeywordDao
import com.budgetbuddy.mobile.data.model.CategoryKeyword
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

/**
 * Keyword matching service for transaction categorization
 * 
 * Matches transaction narrations against category keywords.
 * Keyword matching takes precedence over model predictions (same as Python model).
 */
class KeywordMatcher(private val categoryKeywordDao: CategoryKeywordDao) {
    
    private var keywordMappings: List<CategoryKeyword>? = null
    private var isLoaded = false
    
    /**
     * Load keyword mappings from database
     */
    suspend fun loadKeywords() = withContext(Dispatchers.IO) {
        try {
            keywordMappings = categoryKeywordDao.getAllCategories()
            isLoaded = true
            android.util.Log.d("KeywordMatcher", "Loaded ${keywordMappings?.size ?: 0} keyword mappings")
        } catch (e: Exception) {
            android.util.Log.e("KeywordMatcher", "Failed to load keywords", e)
            keywordMappings = emptyList()
            isLoaded = false
        }
    }
    
    /**
     * Match narration against keywords and return category if match found.
     * Keyword matching takes precedence over model predictions.
     * 
     * @param narration Transaction narration text (can be original or cleaned)
     * @return Category name if keyword match found, null otherwise
     */
    suspend fun matchKeywords(narration: String?): String? = withContext(Dispatchers.Default) {
        if (narration.isNullOrBlank() || !isLoaded) {
            return@withContext null
        }
        
        val mappings = keywordMappings ?: return@withContext null
        if (mappings.isEmpty()) {
            return@withContext null
        }
        
        val narrationLower = narration.lowercase()
        
        // Sort by keyword length (longest first) to match "mutual fund" before "fund"
        val sortedMappings = mappings.sortedByDescending { it.keyword.length }
        
        for (mapping in sortedMappings) {
            val keyword = mapping.keyword.lowercase().trim()
            if (keyword.isEmpty()) continue
            
            // Use word boundary matching for better precision
            // Pattern: \b matches word boundaries
            val pattern = Pattern.compile("\\b${Pattern.quote(keyword)}\\b", Pattern.CASE_INSENSITIVE)
            if (pattern.matcher(narrationLower).find()) {
                android.util.Log.d("KeywordMatcher", "✅ Keyword match found: '$keyword' -> '${mapping.categoryName}' in narration: '${narration.take(100)}'")
                return@withContext mapping.categoryName
            }
        }
        
        return@withContext null
    }
    
    /**
     * Check if keywords are loaded
     */
    fun isKeywordsLoaded(): Boolean = isLoaded
}

