package com.budgetbuddy.mobile.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.budgetbuddy.mobile.ui.screens.DashboardScreen
import com.budgetbuddy.mobile.ui.screens.TransactionListScreen
import com.budgetbuddy.mobile.ui.screens.UploadScreen
import com.budgetbuddy.mobile.ui.screens.CategoriesScreen
import com.budgetbuddy.mobile.ui.screens.HomeScreen
import com.budgetbuddy.mobile.ui.screens.AddTransactionScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Dashboard : Screen("dashboard")
    object Transactions : Screen("transactions")
    object Upload : Screen("upload")
    object Categories : Screen("categories")
    object AddTransaction : Screen("add_transaction")
}

@Composable
fun Navigation(navController: NavHostController = rememberNavController()) {
    android.util.Log.d("Navigation", "🧭 Navigation composable called, startDestination: ${Screen.Home.route}")
    
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            android.util.Log.d("Navigation", "📍 Navigating to HomeScreen")
            HomeScreen(navController = navController)
        }
        composable(Screen.Dashboard.route) {
            DashboardScreen(navController = navController)
        }
        composable(Screen.Transactions.route) {
            TransactionListScreen(navController = navController)
        }
        composable(Screen.Upload.route) {
            UploadScreen(navController = navController)
        }
        composable(Screen.Categories.route) {
            CategoriesScreen(navController = navController)
        }
        composable(Screen.AddTransaction.route) {
            AddTransactionScreen(navController = navController)
        }
    }
}

