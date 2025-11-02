package com.budgetbuddy.mobile.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetbuddy.mobile.data.repository.TransactionRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class DashboardState(
    val totalIncome: Double = 0.0,
    val totalExpenses: Double = 0.0,
    val balance: Double = 0.0,
    val expensesByCategory: Map<String, Double> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class DashboardViewModel(
    private val transactionRepository: TransactionRepository,
    private val userId: Long
) : ViewModel() {
    
    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()
    
    init {
        loadDashboardData()
    }
    
    private fun loadDashboardData() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            
            try {
                val income = transactionRepository.getTotalIncome(userId)
                val expenses = transactionRepository.getTotalExpenses(userId)
                val balance = income - expenses
                
                // Get expenses by predicted category (like website)
                val allTransactions = transactionRepository.getTransactionsByUser(userId)
                    .first() // Get first emission from Flow
                
                val categoryExpenses = allTransactions
                    .filter { it.amount < 0 } // Only expenses
                    .groupBy { 
                        // Use predicted category if available, otherwise "Uncategorized"
                        it.predictedCategory?.takeIf { cat -> cat.isNotEmpty() } ?: "Uncategorized"
                    }
                    .mapValues { (_, transactions) ->
                        transactions.sumOf { kotlin.math.abs(it.amount) }
                    }
                
                _state.update {
                    it.copy(
                        totalIncome = income,
                        totalExpenses = expenses,
                        balance = balance,
                        expensesByCategory = categoryExpenses,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Error loading dashboard"
                    )
                }
            }
        }
    }
    
    fun refresh() {
        loadDashboardData()
    }
}

