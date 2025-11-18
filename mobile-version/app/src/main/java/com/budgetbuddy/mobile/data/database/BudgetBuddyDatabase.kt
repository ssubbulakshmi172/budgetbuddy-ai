package com.budgetbuddy.mobile.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.budgetbuddy.mobile.data.dao.CategoryKeywordDao
import com.budgetbuddy.mobile.data.dao.TransactionDao
import com.budgetbuddy.mobile.data.dao.UserDao
import com.budgetbuddy.mobile.data.dao.SpendingPatternDao
import com.budgetbuddy.mobile.data.dao.SpendingPredictionDao
import com.budgetbuddy.mobile.data.dao.FinancialNudgeDao
import com.budgetbuddy.mobile.data.model.CategoryKeyword
import com.budgetbuddy.mobile.data.model.Transaction
import com.budgetbuddy.mobile.data.model.User
import com.budgetbuddy.mobile.data.model.SpendingPattern
import com.budgetbuddy.mobile.data.model.SpendingPrediction
import com.budgetbuddy.mobile.data.model.FinancialNudge
import java.time.LocalDate

@Database(
    entities = [
        Transaction::class, 
        CategoryKeyword::class, 
        User::class,
        SpendingPattern::class,
        SpendingPrediction::class,
        FinancialNudge::class
    ],
    version = 2,  // Incremented for new entities
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class BudgetBuddyDatabase : RoomDatabase() {
    
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryKeywordDao(): CategoryKeywordDao
    abstract fun userDao(): UserDao
    abstract fun spendingPatternDao(): SpendingPatternDao
    abstract fun spendingPredictionDao(): SpendingPredictionDao
    abstract fun financialNudgeDao(): FinancialNudgeDao
    
    companion object {
        const val DATABASE_NAME = "budgetbuddy_db"
    }
}

// Type converters for Room
class Converters {
    @TypeConverter
    fun fromLocalDate(value: LocalDate?): String? {
        return value?.toString()
    }
    
    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? {
        return value?.let { LocalDate.parse(it) }
    }
}

