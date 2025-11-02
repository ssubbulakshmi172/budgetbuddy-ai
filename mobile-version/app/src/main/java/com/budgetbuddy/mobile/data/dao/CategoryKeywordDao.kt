package com.budgetbuddy.mobile.data.dao

import androidx.room.*
import com.budgetbuddy.mobile.data.model.CategoryKeyword
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryKeywordDao {
    
    @Query("SELECT * FROM category_keywords ORDER BY categoryName, keyword")
    fun getAllCategoryKeywords(): Flow<List<CategoryKeyword>>
    
    @Query("SELECT * FROM category_keywords WHERE categoryName = :categoryName")
    fun getKeywordsByCategory(categoryName: String): Flow<List<CategoryKeyword>>
    
    @Query("SELECT DISTINCT categoryName FROM category_keywords ORDER BY categoryName")
    fun getAllCategoryNames(): Flow<List<String>>
    
    @Query("SELECT * FROM category_keywords WHERE categoriesFor = 'Taxonomy' ORDER BY categoryName, keyword")
    suspend fun getTaxonomyCategories(): List<CategoryKeyword>
    
    @Query("SELECT * FROM category_keywords WHERE categoriesFor = 'Manual' OR categoriesFor IS NULL ORDER BY categoryName, keyword")
    suspend fun getManualCategories(): List<CategoryKeyword>
    
    @Query("SELECT * FROM category_keywords ORDER BY categoryName, keyword")
    suspend fun getAllCategories(): List<CategoryKeyword>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategoryKeyword(keyword: CategoryKeyword): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategoryKeywords(keywords: List<CategoryKeyword>): List<Long>
    
    @Update
    suspend fun updateCategoryKeyword(keyword: CategoryKeyword)
    
    @Delete
    suspend fun deleteCategoryKeyword(keyword: CategoryKeyword)
    
    @Query("DELETE FROM category_keywords")
    suspend fun deleteAll()
}

