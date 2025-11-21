package com.budgetbuddy.mobile.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.YearMonth

@Entity(
    tableName = "weekend_overspending",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["userId"]), Index(value = ["category", "month"])]
)
data class WeekendOverspending(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: Long,
    val category: String? = null,
    val weekendAvg: Double,
    val weekendSpending: Double? = null,
    val weekdayAvg: Double,
    val weekdaySpending: Double? = null,
    val ratio: Double, // weekend_avg / weekday_avg
    val percentageIncrease: Double? = null,
    val month: YearMonth = YearMonth.now(),
    val year: Int = YearMonth.now().year,
    val trend: Trend? = null,
    val alertLevel: AlertLevel? = null,
    val isActive: Boolean = true,
    val createdAt: LocalDate = LocalDate.now()
) {
    enum class Trend {
        INCREASING, DECREASING, STABLE
    }
    
    enum class AlertLevel {
        LOW,    // Ratio 1.1-1.3
        MEDIUM, // Ratio 1.3-1.5
        HIGH    // Ratio > 1.5
    }
}

