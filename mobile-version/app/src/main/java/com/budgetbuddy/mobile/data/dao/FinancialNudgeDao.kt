package com.budgetbuddy.mobile.data.dao

import androidx.room.*
import com.budgetbuddy.mobile.data.model.FinancialNudge
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface FinancialNudgeDao {
    
    @Query("SELECT * FROM financial_nudges WHERE userId = :userId AND isDismissed = 0 AND (expiresAt IS NULL OR expiresAt >= :today) ORDER BY priority DESC, createdAt DESC")
    fun getActiveNudgesByUser(userId: Long, today: LocalDate = LocalDate.now()): Flow<List<FinancialNudge>>
    
    @Query("SELECT * FROM financial_nudges WHERE userId = :userId AND isDismissed = 0 AND isRead = 0 AND (expiresAt IS NULL OR expiresAt >= :today) ORDER BY priority DESC, createdAt DESC")
    fun getUnreadNudgesByUser(userId: Long, today: LocalDate = LocalDate.now()): Flow<List<FinancialNudge>>
    
    @Query("SELECT * FROM financial_nudges WHERE userId = :userId ORDER BY createdAt DESC")
    fun getAllNudgesByUser(userId: Long): Flow<List<FinancialNudge>>
    
    @Query("SELECT * FROM financial_nudges WHERE id = :id")
    suspend fun getNudgeById(id: Long): FinancialNudge?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNudge(nudge: FinancialNudge): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNudges(nudges: List<FinancialNudge>): List<Long>
    
    @Update
    suspend fun updateNudge(nudge: FinancialNudge)
    
    @Delete
    suspend fun deleteNudge(nudge: FinancialNudge)
    
    @Query("UPDATE financial_nudges SET isRead = 1 WHERE id = :nudgeId")
    suspend fun markAsRead(nudgeId: Long)
    
    @Query("UPDATE financial_nudges SET isDismissed = 1 WHERE id = :nudgeId")
    suspend fun dismissNudge(nudgeId: Long)
}

