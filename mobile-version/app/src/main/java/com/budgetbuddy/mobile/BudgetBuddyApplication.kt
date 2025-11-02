package com.budgetbuddy.mobile

import android.app.Application
import androidx.multidex.MultiDexApplication
import com.budgetbuddy.mobile.data.database.BudgetBuddyDatabase
import com.budgetbuddy.mobile.data.database.DatabaseModule
import com.budgetbuddy.mobile.data.preferences.PreferencesDataStore
import com.budgetbuddy.mobile.ml.PyTorchMobileInferenceService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BudgetBuddyApplication : MultiDexApplication() {
    
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    // Database instance
    lateinit var database: BudgetBuddyDatabase
        private set
    
    // DataStore instance
    lateinit var preferencesDataStore: PreferencesDataStore
        private set
    
    // ML Service instance (PyTorch Mobile)
    lateinit var mlService: PyTorchMobileInferenceService
        private set
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize DataStore
        preferencesDataStore = PreferencesDataStore(this)
        
        // Initialize database synchronously (encryption can be added later)
        // For now, use regular database
        database = DatabaseModule.createDatabase(this)
        
        // Create default user if it doesn't exist
        applicationScope.launch {
            val userDao = database.userDao()
            if (userDao.getUserById(1L) == null) {
                val defaultUser = com.budgetbuddy.mobile.data.model.User(
                    id = 0,
                    name = "Default User",
                    email = "user@budgetbuddy.com",
                    password = "default" // Should be hashed in production
                )
                userDao.insertUser(defaultUser)
                android.util.Log.d("BudgetBuddyApp", "Created default user")
            }
        }
        
        // TODO: Add encryption later with SQLCipher
        // val password = DatabaseModule.getDatabasePassword(this)
        // database = DatabaseModule.createEncryptedDatabase(this, password)
        
        // Initialize ML service (PyTorch Mobile)
        mlService = PyTorchMobileInferenceService(this)
        applicationScope.launch {
            val initialized = mlService.initialize()
            if (initialized) {
                android.util.Log.d("BudgetBuddyApp", "PyTorch Mobile model initialized successfully")
            } else {
                android.util.Log.e("BudgetBuddyApp", "Failed to initialize PyTorch Mobile model")
            }
        }
    }
}

