package com.budgetbuddy.mobile.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Database migrations for BudgetBuddy database
 */
object Migrations {
    
    /**
     * Migration from version 1 to 2
     * Adds: spending_patterns, spending_predictions, financial_nudges tables
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Create spending_patterns table
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS spending_patterns (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    userId INTEGER NOT NULL,
                    patternType TEXT NOT NULL,
                    category TEXT,
                    subcategory TEXT,
                    merchantPattern TEXT,
                    dayOfWeek INTEGER,
                    dayOfMonth INTEGER,
                    averageAmount REAL NOT NULL,
                    frequency INTEGER,
                    confidenceScore REAL,
                    firstObserved TEXT,
                    lastObserved TEXT,
                    isActive INTEGER NOT NULL DEFAULT 1,
                    createdAt TEXT,
                    FOREIGN KEY(userId) REFERENCES users(id) ON DELETE CASCADE
                )
            """.trimIndent())
            
            // Create index on spending_patterns
            database.execSQL("CREATE INDEX IF NOT EXISTS index_spending_patterns_userId ON spending_patterns(userId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_spending_patterns_isActive ON spending_patterns(isActive)")
            
            // Create spending_predictions table
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS spending_predictions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    userId INTEGER NOT NULL,
                    predictionDate TEXT NOT NULL,
                    forecastStartDate TEXT NOT NULL,
                    forecastEndDate TEXT NOT NULL,
                    category TEXT,
                    subcategory TEXT,
                    predictedAmount REAL NOT NULL,
                    confidenceScore REAL,
                    predictionMethod TEXT,
                    riskLevel TEXT,
                    isOverspendingRisk INTEGER NOT NULL DEFAULT 0,
                    createdAt TEXT,
                    FOREIGN KEY(userId) REFERENCES users(id) ON DELETE CASCADE
                )
            """.trimIndent())
            
            // Create index on spending_predictions
            database.execSQL("CREATE INDEX IF NOT EXISTS index_spending_predictions_userId ON spending_predictions(userId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_spending_predictions_dates ON spending_predictions(forecastStartDate, forecastEndDate)")
            
            // Create financial_nudges table
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS financial_nudges (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    userId INTEGER NOT NULL,
                    nudgeType TEXT NOT NULL,
                    title TEXT NOT NULL,
                    message TEXT NOT NULL,
                    suggestion TEXT,
                    category TEXT,
                    subcategory TEXT,
                    relatedAmount REAL,
                    priority TEXT NOT NULL,
                    isRead INTEGER NOT NULL DEFAULT 0,
                    isDismissed INTEGER NOT NULL DEFAULT 0,
                    createdAt TEXT,
                    expiresAt TEXT,
                    FOREIGN KEY(userId) REFERENCES users(id) ON DELETE CASCADE
                )
            """.trimIndent())
            
            // Create index on financial_nudges
            database.execSQL("CREATE INDEX IF NOT EXISTS index_financial_nudges_userId ON financial_nudges(userId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_financial_nudges_status ON financial_nudges(isDismissed, isRead)")
        }
    }
    
    /**
     * Migration from version 2 to 3
     * Adds: money_leak, category_overspending_alert, savings_projection, weekend_overspending tables
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Create money_leak table
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS money_leak (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    userId INTEGER NOT NULL,
                    leakType TEXT NOT NULL,
                    title TEXT NOT NULL,
                    description TEXT,
                    merchantPattern TEXT,
                    monthlyAmount REAL NOT NULL,
                    annualAmount REAL NOT NULL,
                    transactionCount INTEGER,
                    averageTransactionAmount REAL,
                    suggestion TEXT,
                    rank INTEGER,
                    isActive INTEGER NOT NULL DEFAULT 1,
                    detectedAt TEXT NOT NULL,
                    FOREIGN KEY(userId) REFERENCES users(id) ON DELETE CASCADE
                )
            """.trimIndent())
            database.execSQL("CREATE INDEX IF NOT EXISTS index_money_leak_userId ON money_leak(userId)")
            
            // Create category_overspending_alert table
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS category_overspending_alert (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    userId INTEGER NOT NULL,
                    category TEXT NOT NULL,
                    alertLevel TEXT NOT NULL,
                    currentAmount REAL NOT NULL,
                    historicalAvg REAL NOT NULL,
                    standardDeviation REAL,
                    percentageIncrease REAL,
                    projectedMonthly REAL,
                    month TEXT NOT NULL,
                    daysElapsed INTEGER,
                    isActive INTEGER NOT NULL DEFAULT 1,
                    createdAt TEXT NOT NULL,
                    FOREIGN KEY(userId) REFERENCES users(id) ON DELETE CASCADE
                )
            """.trimIndent())
            database.execSQL("CREATE INDEX IF NOT EXISTS index_category_overspending_alert_userId ON category_overspending_alert(userId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_category_overspending_alert_category_month ON category_overspending_alert(category, month)")
            
            // Create savings_projection table
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS savings_projection (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    userId INTEGER NOT NULL,
                    projectionDate TEXT NOT NULL,
                    currentMonth INTEGER NOT NULL,
                    currentSavings REAL NOT NULL,
                    monthlyIncomeAvg REAL NOT NULL,
                    monthlyExpenseAvg REAL NOT NULL,
                    monthlySavingsRate REAL NOT NULL,
                    remainingMonths INTEGER NOT NULL,
                    projectedAdditionalSavings REAL NOT NULL,
                    projectedYearEnd REAL NOT NULL,
                    confidenceScore REAL,
                    trendAdjustmentFactor REAL,
                    year INTEGER NOT NULL,
                    createdAt TEXT NOT NULL,
                    FOREIGN KEY(userId) REFERENCES users(id) ON DELETE CASCADE
                )
            """.trimIndent())
            database.execSQL("CREATE INDEX IF NOT EXISTS index_savings_projection_userId ON savings_projection(userId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_savings_projection_year ON savings_projection(year)")
            
            // Create weekend_overspending table
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS weekend_overspending (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    userId INTEGER NOT NULL,
                    category TEXT,
                    weekendAvg REAL NOT NULL,
                    weekendSpending REAL,
                    weekdayAvg REAL NOT NULL,
                    weekdaySpending REAL,
                    ratio REAL NOT NULL,
                    percentageIncrease REAL,
                    month TEXT NOT NULL,
                    year INTEGER NOT NULL,
                    trend TEXT,
                    alertLevel TEXT,
                    isActive INTEGER NOT NULL DEFAULT 1,
                    createdAt TEXT NOT NULL,
                    FOREIGN KEY(userId) REFERENCES users(id) ON DELETE CASCADE
                )
            """.trimIndent())
            database.execSQL("CREATE INDEX IF NOT EXISTS index_weekend_overspending_userId ON weekend_overspending(userId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_weekend_overspending_category_month ON weekend_overspending(category, month)")
        }
    }
}

