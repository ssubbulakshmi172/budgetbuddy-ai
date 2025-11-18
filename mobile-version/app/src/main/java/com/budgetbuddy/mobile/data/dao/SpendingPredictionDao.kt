package com.budgetbuddy.mobile.data.dao

import androidx.room.*
import com.budgetbuddy.mobile.data.model.SpendingPrediction
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface SpendingPredictionDao {
    
    @Query("SELECT * FROM spending_predictions WHERE userId = :userId ORDER BY forecastStartDate DESC")
    fun getPredictionsByUser(userId: Long): Flow<List<SpendingPrediction>>
    
    @Query("SELECT * FROM spending_predictions WHERE userId = :userId AND forecastStartDate >= :startDate AND forecastEndDate <= :endDate ORDER BY forecastStartDate DESC")
    fun getPredictionsByDateRange(userId: Long, startDate: LocalDate, endDate: LocalDate): Flow<List<SpendingPrediction>>
    
    @Query("SELECT * FROM spending_predictions WHERE userId = :userId AND isOverspendingRisk = 1 ORDER BY riskLevel DESC, predictedAmount DESC")
    fun getOverspendingRisksByUser(userId: Long): Flow<List<SpendingPrediction>>
    
    @Query("SELECT * FROM spending_predictions WHERE id = :id")
    suspend fun getPredictionById(id: Long): SpendingPrediction?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrediction(prediction: SpendingPrediction): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPredictions(predictions: List<SpendingPrediction>): List<Long>
    
    @Update
    suspend fun updatePrediction(prediction: SpendingPrediction)
    
    @Delete
    suspend fun deletePrediction(prediction: SpendingPrediction)
    
    @Query("DELETE FROM spending_predictions WHERE userId = :userId")
    suspend fun deleteAllPredictionsForUser(userId: Long)
}

