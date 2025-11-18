package com.budgetbuddy.mobile.data.dao

import androidx.room.*
import com.budgetbuddy.mobile.data.model.SpendingPattern
import kotlinx.coroutines.flow.Flow

@Dao
interface SpendingPatternDao {
    
    @Query("SELECT * FROM spending_patterns WHERE userId = :userId AND isActive = 1 ORDER BY confidenceScore DESC")
    fun getActivePatternsByUser(userId: Long): Flow<List<SpendingPattern>>
    
    @Query("SELECT * FROM spending_patterns WHERE userId = :userId ORDER BY createdAt DESC")
    fun getAllPatternsByUser(userId: Long): Flow<List<SpendingPattern>>
    
    @Query("SELECT * FROM spending_patterns WHERE id = :id")
    suspend fun getPatternById(id: Long): SpendingPattern?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPattern(pattern: SpendingPattern): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPatterns(patterns: List<SpendingPattern>): List<Long>
    
    @Update
    suspend fun updatePattern(pattern: SpendingPattern)
    
    @Delete
    suspend fun deletePattern(pattern: SpendingPattern)
    
    @Query("UPDATE spending_patterns SET isActive = 0 WHERE userId = :userId")
    suspend fun deactivateAllPatternsForUser(userId: Long)
}

