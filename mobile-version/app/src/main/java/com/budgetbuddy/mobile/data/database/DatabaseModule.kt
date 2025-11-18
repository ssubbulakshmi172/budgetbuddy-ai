package com.budgetbuddy.mobile.data.database

import android.content.Context
import androidx.room.Room
import com.budgetbuddy.mobile.data.database.Migrations

object DatabaseModule {
    
    /**
     * Creates Room database
     * Note: Encryption can be added later with SQLCipher if needed
     */
    fun createDatabase(context: Context): BudgetBuddyDatabase {
        return Room.databaseBuilder(
            context,
            BudgetBuddyDatabase::class.java,
            BudgetBuddyDatabase.DATABASE_NAME
        )
        .addMigrations(Migrations.MIGRATION_1_2)
        .fallbackToDestructiveMigration() // Fallback for development - remove in production
        .build()
    }
    
}

