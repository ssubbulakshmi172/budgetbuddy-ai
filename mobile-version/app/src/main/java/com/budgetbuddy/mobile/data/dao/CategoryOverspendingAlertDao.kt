package com.budgetbuddy.mobile.data.dao

import androidx.room.*
import com.budgetbuddy.mobile.data.model.CategoryOverspendingAlert
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth

@Dao
interface CategoryOverspendingAlertDao {
    @Query("SELECT * FROM category_overspending_alert WHERE userId = :userId AND isActive = 1 ORDER BY alertLevel DESC, percentageIncrease DESC")
    fun getActiveAlerts(userId: Long): Flow<List<CategoryOverspendingAlert>>
    
    @Query("SELECT * FROM category_overspending_alert WHERE userId = :userId AND isActive = 1 ORDER BY alertLevel DESC, percentageIncrease DESC")
    suspend fun getActiveAlertsSync(userId: Long): List<CategoryOverspendingAlert>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(alerts: List<CategoryOverspendingAlert>)
    
    @Query("DELETE FROM category_overspending_alert WHERE userId = :userId")
    suspend fun deleteByUserId(userId: Long)
    
    @Query("UPDATE category_overspending_alert SET isActive = 0 WHERE userId = :userId")
    suspend fun deactivateAll(userId: Long)
    
    @Query("SELECT * FROM category_overspending_alert WHERE userId = :userId AND category = :category AND month = :month")
    suspend fun findByCategoryAndMonth(userId: Long, category: String, month: YearMonth): CategoryOverspendingAlert?
}

