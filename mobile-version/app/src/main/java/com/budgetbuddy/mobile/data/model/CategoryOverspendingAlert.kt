package com.budgetbuddy.mobile.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.YearMonth

@Entity(
    tableName = "category_overspending_alert",
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
data class CategoryOverspendingAlert(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: Long,
    val category: String,
    val alertLevel: AlertLevel,
    val currentAmount: Double,
    val historicalAvg: Double,
    val standardDeviation: Double? = null,
    val percentageIncrease: Double? = null,
    val projectedMonthly: Double? = null,
    val month: YearMonth = YearMonth.now(),
    val daysElapsed: Int? = null,
    val isActive: Boolean = true,
    val createdAt: LocalDate = LocalDate.now()
) {
    enum class AlertLevel {
        LOW,        // < 10% increase
        MEDIUM,     // 10-25% increase
        HIGH,       // 25-50% increase
        CRITICAL    // > 50% increase OR exceeds 2× std_dev
    }
}

