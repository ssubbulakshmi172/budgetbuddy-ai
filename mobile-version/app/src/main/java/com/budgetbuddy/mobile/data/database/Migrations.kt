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
}

