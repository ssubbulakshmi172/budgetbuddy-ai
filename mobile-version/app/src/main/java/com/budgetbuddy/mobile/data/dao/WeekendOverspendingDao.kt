package com.budgetbuddy.mobile.data.dao

import androidx.room.*
import com.budgetbuddy.mobile.data.model.WeekendOverspending
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth

@Dao
interface WeekendOverspendingDao {
    @Query("SELECT * FROM weekend_overspending WHERE userId = :userId AND isActive = 1 AND month = :month ORDER BY ratio DESC")
    fun getActiveByMonth(userId: Long, month: YearMonth): Flow<List<WeekendOverspending>>
    
    @Query("SELECT * FROM weekend_overspending WHERE userId = :userId AND isActive = 1 AND month = :month ORDER BY ratio DESC")
    suspend fun getActiveByMonthSync(userId: Long, month: YearMonth): List<WeekendOverspending>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(overspending: List<WeekendOverspending>)
    
    @Query("DELETE FROM weekend_overspending WHERE userId = :userId")
    suspend fun deleteByUserId(userId: Long)
    
    @Query("UPDATE weekend_overspending SET isActive = 0 WHERE userId = :userId")
    suspend fun deactivateAll(userId: Long)
}

