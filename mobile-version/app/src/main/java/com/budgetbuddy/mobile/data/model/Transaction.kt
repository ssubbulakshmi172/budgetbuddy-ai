package com.budgetbuddy.mobile.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["userId"]), Index(value = ["date"]), Index(value = ["categoryName"])]
)
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val date: LocalDate,
    
    val narration: String?,
    
    val chequeRefNo: String?,
    
    val withdrawalAmt: Double?,
    
    val depositAmt: Double?,
    
    val closingBalance: Double?,
    
    val userId: Long,
    
    val predictedCategory: String?,
    
    val predictedSubcategory: String?,
    
    val predictedTransactionType: String?, // P2C, P2P, P2Business
    
    val predictedIntent: String?, // purchase, transfer, refund, subscription, bill_payment, other
    
    val predictionConfidence: Double?,
    
    val categoryName: String?,
    
    val amount: Double
)

