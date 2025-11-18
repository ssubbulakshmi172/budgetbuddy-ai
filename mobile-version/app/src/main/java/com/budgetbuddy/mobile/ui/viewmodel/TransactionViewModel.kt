package com.budgetbuddy.mobile.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetbuddy.mobile.data.model.Transaction
import com.budgetbuddy.mobile.data.repository.TransactionRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class TransactionListState(
    val transactions: List<Transaction> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    val selectedCategory: String? = null
)

class TransactionViewModel(
    private val transactionRepository: TransactionRepository,
    private val userId: Long
) : ViewModel() {
    
    private val _state = MutableStateFlow(TransactionListState())
    val state: StateFlow<TransactionListState> = _state.asStateFlow()
    
    private var collectionJob: kotlinx.coroutines.Job? = null
    
    init {
        loadTransactions()
    }
    
    private fun loadTransactions() {
        // Cancel previous collection if any
        collectionJob?.cancel()
        
        collectionJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            
            try {
                val flow = when {
                    _state.value.searchQuery.isNotEmpty() -> {
                        transactionRepository.searchTransactions(userId, _state.value.searchQuery)
                    }
                    _state.value.selectedCategory != null -> {
                        transactionRepository.getTransactionsByCategory(
                            userId,
                            _state.value.selectedCategory!!
                        )
                    }
                    else -> {
                        transactionRepository.getTransactionsByUser(userId)
                    }
                }
                
                flow.collect { transactions ->
                    _state.update {
                        it.copy(
                            transactions = transactions,
                            isLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Error loading transactions"
                    )
                }
            }
        }
    }
    
    fun setSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
        loadTransactions()
    }
    
    fun setSelectedCategory(category: String?) {
        _state.update { it.copy(selectedCategory = category) }
        loadTransactions()
    }
    
    fun refresh() {
        loadTransactions()
    }
    
    fun deleteAllTransactions() {
        viewModelScope.launch {
            try {
                _state.update { it.copy(isLoading = true, error = null) }
                
                // Delete all transactions for this user
                transactionRepository.deleteAllTransactions(userId)
                
                android.util.Log.d("TransactionViewModel", "✅ Deleted all transactions for user $userId")
                
                // Manually update state to empty list immediately
                _state.update { 
                    it.copy(
                        transactions = emptyList(),
                        isLoading = false,
                        error = null
                    )
                }
                
                // Also reload from database to ensure consistency
                loadTransactions()
            } catch (e: Exception) {
                android.util.Log.e("TransactionViewModel", "❌ Error deleting all transactions: ${e.message}", e)
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Error deleting transactions"
                    )
                }
            }
        }
    }
    
    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            try {
                _state.update { it.copy(isLoading = true, error = null) }
                
                transactionRepository.deleteTransaction(transaction)
                
                android.util.Log.d("TransactionViewModel", "✅ Deleted transaction ${transaction.id}")
                
                // Reload transactions
                loadTransactions()
            } catch (e: Exception) {
                android.util.Log.e("TransactionViewModel", "❌ Error deleting transaction: ${e.message}", e)
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Error deleting transaction"
                    )
                }
            }
        }
    }
}

