package com.budgetbuddy.mobile.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(
    tableName = "money_leak",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["userId"])]
)
data class MoneyLeak(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: Long,
    val leakType: LeakType,
    val title: String,
    val description: String? = null,
    val merchantPattern: String? = null,
    val monthlyAmount: Double,
    val annualAmount: Double,
    val transactionCount: Int? = null,
    val averageTransactionAmount: Double? = null,
    val suggestion: String? = null,
    val rank: Int? = null, // 1, 2, 3 for top 3
    val isActive: Boolean = true,
    val detectedAt: LocalDate = LocalDate.now()
) {
    enum class LeakType {
        REPEATING_SUBSCRIPTION,
        COFFEE_EFFECT,
        ATM_WITHDRAWAL_SPIKE,
        UNUSED_SERVICE,
        AUTO_DEBIT_MISALIGNED
    }
}

