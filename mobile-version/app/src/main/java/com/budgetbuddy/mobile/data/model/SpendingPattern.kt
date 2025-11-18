package com.budgetbuddy.mobile.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(
    tableName = "spending_patterns",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["userId"]), Index(value = ["isActive"])]
)
data class SpendingPattern(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val userId: Long,
    
    val patternType: PatternType, // DAILY, WEEKLY, MONTHLY
    
    val category: String?,
    
    val subcategory: String?,
    
    val merchantPattern: String?, // Pattern in narration
    
    val dayOfWeek: Int? = null, // 1-7 for weekly patterns (1=Monday)
    
    val dayOfMonth: Int? = null, // 1-31 for monthly patterns
    
    val averageAmount: Double,
    
    val frequency: Int?, // How many times per period
    
    val confidenceScore: Double?, // 0.0 to 1.0
    
    val firstObserved: LocalDate?,
    
    val lastObserved: LocalDate?,
    
    val isActive: Boolean = true,
    
    val createdAt: LocalDate? = null
) {
    enum class PatternType {
        DAILY, WEEKLY, MONTHLY
    }
}

