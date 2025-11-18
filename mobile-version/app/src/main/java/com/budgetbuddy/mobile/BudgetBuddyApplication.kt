package com.budgetbuddy.mobile

import androidx.multidex.MultiDexApplication
import com.budgetbuddy.mobile.data.database.BudgetBuddyDatabase
import com.budgetbuddy.mobile.data.database.DatabaseModule
import com.budgetbuddy.mobile.data.preferences.PreferencesDataStore
import com.budgetbuddy.mobile.ml.PyTorchMobileInferenceService
import com.budgetbuddy.mobile.ml.KeywordMatcher
import com.budgetbuddy.mobile.util.KeywordInitializer
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
    
    // Keyword Matcher instance
    lateinit var keywordMatcher: KeywordMatcher
        private set
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize DataStore
        preferencesDataStore = PreferencesDataStore(this)
        
        // Initialize database synchronously (encryption can be added later)
        // For now, use regular database
        try {
            database = DatabaseModule.createDatabase(this)
            android.util.Log.d("BudgetBuddyApp", "✅ Database initialized successfully")
            
            // Create default user if it doesn't exist
            applicationScope.launch {
                try {
                    val userDao = database.userDao()
                    val existingUser = userDao.getUserById(1L)
                    if (existingUser == null) {
                        val defaultUser = com.budgetbuddy.mobile.data.model.User(
                            id = 0,
                            name = "Default User",
                            email = "user@budgetbuddy.com",
                            password = "default" // Should be hashed in production
                        )
                        userDao.insertUser(defaultUser)
                        android.util.Log.d("BudgetBuddyApp", "✅ Created default user")
                    } else {
                        android.util.Log.d("BudgetBuddyApp", "✅ Default user already exists")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("BudgetBuddyApp", "❌ Error creating default user: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("BudgetBuddyApp", "❌ Error initializing database: ${e.message}", e)
            // Re-throw to prevent app from starting with broken database
            throw e
        }
        
        
        // Initialize Keyword Matcher and populate keywords if database is empty
        keywordMatcher = KeywordMatcher(database.categoryKeywordDao())
        applicationScope.launch {
            // Check if keywords exist, if not populate from assets
            val existingKeywords = database.categoryKeywordDao().getAllCategories()
            if (existingKeywords.isEmpty()) {
                android.util.Log.d("BudgetBuddyApp", "📝 No keywords found, populating from assets...")
                try {
                    val count = KeywordInitializer.populateFromAssets(this@BudgetBuddyApplication, database.categoryKeywordDao())
                    android.util.Log.d("BudgetBuddyApp", "✅ Populated $count keywords from assets")
                } catch (e: Exception) {
                    android.util.Log.e("BudgetBuddyApp", "❌ Failed to populate keywords", e)
                }
            } else {
                android.util.Log.d("BudgetBuddyApp", "✅ Found ${existingKeywords.size} existing keywords")
            }
            
            // Load keywords into matcher
            keywordMatcher.loadKeywords()
            android.util.Log.d("BudgetBuddyApp", "✅ Keyword matcher initialized")
        }
        
        // Initialize ML service (PyTorch Mobile) with keyword matcher
        mlService = PyTorchMobileInferenceService(this, keywordMatcher)
        applicationScope.launch {
            val initialized = mlService.initialize()
            if (initialized) {
                android.util.Log.d("BudgetBuddyApp", "✅ PyTorch Mobile model initialized successfully")
            } else {
                android.util.Log.e("BudgetBuddyApp", "❌ Failed to initialize PyTorch Mobile model")
            }
        }
    }
}

