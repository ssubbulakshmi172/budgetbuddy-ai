package com.budgetbuddy.mobile.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(
    tableName = "financial_nudges",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["userId"]), Index(value = ["isDismissed", "isRead"])]
)
data class FinancialNudge(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val userId: Long,
    
    val nudgeType: NudgeType,
    
    val title: String,
    
    val message: String,
    
    val suggestion: String?, // Actionable recommendation
    
    val category: String?,
    
    val subcategory: String?,
    
    val relatedAmount: Double?,
    
    val priority: Priority, // LOW, MEDIUM, HIGH
    
    val isRead: Boolean = false,
    
    val isDismissed: Boolean = false,
    
    val createdAt: LocalDate? = null,
    
    val expiresAt: LocalDate? = null
) {
    enum class NudgeType {
        SPENDING_ALERT,        // Early warning about overspending
        PATTERN_DETECTED,       // New spending pattern identified
        OVERSPENDING_RISK,      // High risk of overspending
        TREND_WARNING,          // Unusual trend detected
        BUDGET_MILESTONE,       // Approaching budget limit
        SAVINGS_OPPORTUNITY     // Opportunity to save
    }
    
    enum class Priority {
        LOW, MEDIUM, HIGH
    }
}

