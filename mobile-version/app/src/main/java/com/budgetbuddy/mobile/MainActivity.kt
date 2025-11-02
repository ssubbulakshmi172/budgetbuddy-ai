package com.budgetbuddy.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.budgetbuddy.mobile.ui.navigation.Navigation
import com.budgetbuddy.mobile.ui.theme.BudgetBuddyTheme

class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.d("MainActivity", "🚀 MainActivity.onCreate() called")
        
        setContent {
            android.util.Log.d("MainActivity", "🎨 Setting Compose content")
            BudgetBuddyTheme {
                android.util.Log.d("MainActivity", "✅ Theme applied")
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    android.util.Log.d("MainActivity", "📱 Creating Navigation")
                    Navigation()
                }
            }
        }
    }
}

