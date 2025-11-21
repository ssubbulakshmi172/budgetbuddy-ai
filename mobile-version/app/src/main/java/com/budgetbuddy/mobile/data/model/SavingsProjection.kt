package com.budgetbuddy.mobile.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(
    tableName = "savings_projection",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["userId"]), Index(value = ["year"])]
)
data class SavingsProjection(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: Long,
    val projectionDate: LocalDate = LocalDate.now(),
    val currentMonth: Int = LocalDate.now().monthValue,
    val currentSavings: Double,
    val monthlyIncomeAvg: Double,
    val monthlyExpenseAvg: Double,
    val monthlySavingsRate: Double,
    val remainingMonths: Int,
    val projectedAdditionalSavings: Double,
    val projectedYearEnd: Double,
    val confidenceScore: Double? = null,
    val trendAdjustmentFactor: Double? = null,
    val year: Int = LocalDate.now().year,
    val createdAt: LocalDate = LocalDate.now()
)

