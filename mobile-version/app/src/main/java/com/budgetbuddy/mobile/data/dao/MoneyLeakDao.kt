package com.budgetbuddy.mobile.data.dao

import androidx.room.*
import com.budgetbuddy.mobile.data.model.MoneyLeak
import kotlinx.coroutines.flow.Flow

@Dao
interface MoneyLeakDao {
    @Query("SELECT * FROM money_leak WHERE userId = :userId AND isActive = 1 ORDER BY rank ASC, annualAmount DESC")
    fun getActiveMoneyLeaks(userId: Long): Flow<List<MoneyLeak>>
    
    @Query("SELECT * FROM money_leak WHERE userId = :userId AND isActive = 1 ORDER BY rank ASC, annualAmount DESC LIMIT 3")
    suspend fun getTop3MoneyLeaks(userId: Long): List<MoneyLeak>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(moneyLeaks: List<MoneyLeak>)
    
    @Query("DELETE FROM money_leak WHERE userId = :userId")
    suspend fun deleteByUserId(userId: Long)
    
    @Query("UPDATE money_leak SET isActive = 0 WHERE userId = :userId")
    suspend fun deactivateAll(userId: Long)
}

