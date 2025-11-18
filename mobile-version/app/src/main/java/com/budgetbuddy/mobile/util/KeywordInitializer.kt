package com.budgetbuddy.mobile.util

import android.content.Context
import com.budgetbuddy.mobile.data.dao.CategoryKeywordDao
import com.budgetbuddy.mobile.data.model.CategoryKeyword
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream

/**
 * Initialize category keywords from JSON file
 * 
 * This reads a keywords.json file from assets and populates the database.
 * The JSON file should be generated from categories.yml
 */
object KeywordInitializer {
    
    /**
     * Populate keywords from JSON file in assets
     */
    suspend fun populateFromAssets(
        context: Context,
        categoryKeywordDao: CategoryKeywordDao
    ) = withContext(Dispatchers.IO) {
        try {
            val inputStream: InputStream = context.assets.open("keywords.json")
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)
            
            val keywordsToInsert = mutableListOf<CategoryKeyword>()
            
            for (i in 0 until jsonArray.length()) {
                val keywordObj = jsonArray.getJSONObject(i)
                val categoryName = keywordObj.getString("categoryName")
                val keyword = keywordObj.getString("keyword")
                val categoriesFor = keywordObj.optString("categoriesFor", "Taxonomy")
                
                keywordsToInsert.add(
                    CategoryKeyword(
                        categoryName = categoryName,
                        keyword = keyword.lowercase().trim(),
                        categoriesFor = categoriesFor
                    )
                )
            }
            
            // Insert all keywords
            if (keywordsToInsert.isNotEmpty()) {
                categoryKeywordDao.insertCategoryKeywords(keywordsToInsert)
                android.util.Log.d("KeywordInitializer", "✅ Inserted ${keywordsToInsert.size} keywords from assets")
            } else {
                android.util.Log.w("KeywordInitializer", "⚠️ No keywords found to insert")
            }
            
            keywordsToInsert.size
        } catch (e: Exception) {
            android.util.Log.e("KeywordInitializer", "❌ Failed to populate keywords from assets", e)
            // Don't throw - app should work without keywords
            0
        }
    }
}

