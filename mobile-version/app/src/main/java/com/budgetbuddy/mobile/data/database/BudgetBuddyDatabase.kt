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
import com.budgetbuddy.mobile.data.dao.MoneyLeakDao
import com.budgetbuddy.mobile.data.dao.CategoryOverspendingAlertDao
import com.budgetbuddy.mobile.data.dao.SavingsProjectionDao
import com.budgetbuddy.mobile.data.dao.WeekendOverspendingDao
import com.budgetbuddy.mobile.data.model.CategoryKeyword
import com.budgetbuddy.mobile.data.model.Transaction
import com.budgetbuddy.mobile.data.model.User
import com.budgetbuddy.mobile.data.model.SpendingPattern
import com.budgetbuddy.mobile.data.model.SpendingPrediction
import com.budgetbuddy.mobile.data.model.FinancialNudge
import com.budgetbuddy.mobile.data.model.MoneyLeak
import com.budgetbuddy.mobile.data.model.CategoryOverspendingAlert
import com.budgetbuddy.mobile.data.model.SavingsProjection
import com.budgetbuddy.mobile.data.model.WeekendOverspending
import java.time.LocalDate

@Database(
    entities = [
        Transaction::class, 
        CategoryKeyword::class, 
        User::class,
        SpendingPattern::class,
        SpendingPrediction::class,
        FinancialNudge::class,
        MoneyLeak::class,
        CategoryOverspendingAlert::class,
        SavingsProjection::class,
        WeekendOverspending::class
    ],
    version = 3,  // Incremented for financial guidance entities
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
    abstract fun moneyLeakDao(): MoneyLeakDao
    abstract fun categoryOverspendingAlertDao(): CategoryOverspendingAlertDao
    abstract fun savingsProjectionDao(): SavingsProjectionDao
    abstract fun weekendOverspendingDao(): WeekendOverspendingDao
    
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
    
    @TypeConverter
    fun fromYearMonth(value: java.time.YearMonth?): String? {
        return value?.toString()
    }
    
    @TypeConverter
    fun toYearMonth(value: String?): java.time.YearMonth? {
        return value?.let { java.time.YearMonth.parse(it) }
    }
}

