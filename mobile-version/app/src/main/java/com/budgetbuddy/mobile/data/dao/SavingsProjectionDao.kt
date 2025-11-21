package com.budgetbuddy.mobile.data.dao

import androidx.room.*
import com.budgetbuddy.mobile.data.model.SavingsProjection
import kotlinx.coroutines.flow.Flow

@Dao
interface SavingsProjectionDao {
    @Query("SELECT * FROM savings_projection WHERE userId = :userId AND year = :year ORDER BY projectionDate DESC LIMIT 1")
    fun getLatestProjection(userId: Long, year: Int): Flow<SavingsProjection?>
    
    @Query("SELECT * FROM savings_projection WHERE userId = :userId AND year = :year ORDER BY projectionDate DESC LIMIT 1")
    suspend fun getLatestProjectionSync(userId: Long, year: Int): SavingsProjection?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(projection: SavingsProjection)
    
    @Query("DELETE FROM savings_projection WHERE userId = :userId")
    suspend fun deleteByUserId(userId: Long)
}

