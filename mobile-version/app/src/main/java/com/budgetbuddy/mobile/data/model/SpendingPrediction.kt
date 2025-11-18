package com.budgetbuddy.mobile.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(
    tableName = "spending_predictions",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["userId"]), Index(value = ["forecastStartDate", "forecastEndDate"])]
)
data class SpendingPrediction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val userId: Long,
    
    val predictionDate: LocalDate, // When this prediction was made
    
    val forecastStartDate: LocalDate, // Start of forecast period
    
    val forecastEndDate: LocalDate, // End of forecast period
    
    val category: String?,
    
    val subcategory: String?,
    
    val predictedAmount: Double,
    
    val confidenceScore: Double?, // 0.0 to 1.0
    
    val predictionMethod: String?, // "PATTERN_BASED", "TREND_BASED", "HISTORICAL_AVERAGE"
    
    val riskLevel: RiskLevel?, // LOW, MEDIUM, HIGH
    
    val isOverspendingRisk: Boolean = false,
    
    val createdAt: LocalDate? = null
) {
    enum class RiskLevel {
        LOW, MEDIUM, HIGH
    }
}

