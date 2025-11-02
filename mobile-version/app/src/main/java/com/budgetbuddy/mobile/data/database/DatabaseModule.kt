package com.budgetbuddy.mobile.data.database

import android.content.Context
import androidx.room.Room

object DatabaseModule {
    
    private const val DATABASE_PASSWORD_KEY = "db_password_key"
    
    /**
     * Creates Room database (encryption can be added later with SQLCipher)
     */
    fun createDatabase(context: Context): BudgetBuddyDatabase {
        return Room.databaseBuilder(
            context,
            BudgetBuddyDatabase::class.java,
            BudgetBuddyDatabase.DATABASE_NAME
        )
        .fallbackToDestructiveMigration() // Remove in production - add proper migrations
        .build()
    }
    
    /**
     * Creates encrypted Room database using SQLCipher (for future use)
     */
    fun createEncryptedDatabase(context: Context, password: String): BudgetBuddyDatabase {
        // TODO: Add SQLCipher encryption when library is properly configured
        // For now, use regular database
        return createDatabase(context)
    }
    
    /**
     * Gets database password from Keystore or generates a new one
     */
    suspend fun getDatabasePassword(context: Context): String {
        val keystoreHelper = KeystoreHelper(context)
        return keystoreHelper.getOrCreateKey(DATABASE_PASSWORD_KEY) 
            ?: generateAndStorePassword(context, keystoreHelper)
    }
    
    private suspend fun generateAndStorePassword(
        context: Context,
        keystoreHelper: KeystoreHelper
    ): String {
        val password = generateSecurePassword()
        keystoreHelper.storeKey(DATABASE_PASSWORD_KEY, password)
        return password
    }
    
    private fun generateSecurePassword(): String {
        // Generate a secure random password
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*"
        return (1..32)
            .map { chars.random() }
            .joinToString("")
    }
}

