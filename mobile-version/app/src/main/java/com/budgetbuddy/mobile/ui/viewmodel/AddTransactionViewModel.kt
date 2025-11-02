package com.budgetbuddy.mobile.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetbuddy.mobile.data.model.Transaction
import com.budgetbuddy.mobile.data.repository.TransactionRepository
import com.budgetbuddy.mobile.ml.PyTorchMobileInferenceService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class AddTransactionState(
    val isPredicting: Boolean = false,
    val prediction: PyTorchMobileInferenceService.PredictionResult? = null,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null
)

class AddTransactionViewModel(
    private val transactionRepository: TransactionRepository,
    private val mlService: PyTorchMobileInferenceService,
    private val userId: Long
) : ViewModel() {
    
    private val _state = MutableStateFlow(AddTransactionState())
    val state: StateFlow<AddTransactionState> = _state.asStateFlow()
    
    fun predictTransaction(narration: String, amount: Double) {
        viewModelScope.launch {
            _state.update { it.copy(isPredicting = true, error = null, prediction = null) }
            
            try {
                android.util.Log.d("AddTransactionViewModel", "📊 Starting ML prediction...")
                android.util.Log.d("AddTransactionViewModel", "   Input: '$narration'")
                android.util.Log.d("AddTransactionViewModel", "   Amount: $amount")
                
                val prediction = mlService.predict(narration)
                
                android.util.Log.d("AddTransactionViewModel", "✅ Prediction complete!")
                android.util.Log.d("AddTransactionViewModel", "   Category: ${prediction.predictedCategory}")
                android.util.Log.d("AddTransactionViewModel", "   Type: ${prediction.transactionType}")
                android.util.Log.d("AddTransactionViewModel", "   Intent: ${prediction.intent}")
                android.util.Log.d("AddTransactionViewModel", "   Confidence: ${prediction.confidence}")
                
                _state.update {
                    it.copy(
                        isPredicting = false,
                        prediction = prediction
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("AddTransactionViewModel", "❌ Prediction failed", e)
                _state.update {
                    it.copy(
                        isPredicting = false,
                        error = "Prediction failed: ${e.message}"
                    )
                }
            }
        }
    }
    
    fun addTransaction(
        narration: String,
        amount: Double,
        date: LocalDate,
        category: String?,
        transactionType: com.budgetbuddy.mobile.ui.screens.TransactionType
    ) {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            
            try {
                var prediction = _state.value.prediction
                
                // Auto-predict if narration is not empty and no prediction exists yet
                if (narration.isNotBlank() && prediction == null) {
                    android.util.Log.d("AddTransactionViewModel", "🤖 Auto-predicting transaction on save...")
                    try {
                        prediction = mlService.predict(narration)
                        android.util.Log.d("AddTransactionViewModel", "✅ Auto-prediction complete!")
                        android.util.Log.d("AddTransactionViewModel", "   Category: ${prediction.predictedCategory}")
                        android.util.Log.d("AddTransactionViewModel", "   Type: ${prediction.transactionType}")
                        android.util.Log.d("AddTransactionViewModel", "   Confidence: ${prediction.confidence}")
                        
                        // Update state with prediction
                        _state.update { 
                            it.copy(
                                prediction = prediction,
                                isPredicting = false
                            )
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("AddTransactionViewModel", "⚠️ Auto-prediction failed (continuing without prediction): ${e.message}")
                        // Continue without prediction if it fails
                    }
                }
                
                // Match Spring Boot logic: Calculate amount if not set (deposit - withdrawal)
                val withdrawalAmt = if (transactionType == com.budgetbuddy.mobile.ui.screens.TransactionType.WITHDRAWAL) Math.abs(amount) else null
                val depositAmt = if (transactionType == com.budgetbuddy.mobile.ui.screens.TransactionType.DEPOSIT) amount else null
                
                // Match Spring Boot amount calculation: deposit - withdrawal
                val calculatedAmount = (depositAmt ?: 0.0) - (withdrawalAmt ?: 0.0)
                
                val transaction = Transaction(
                    date = date,
                    narration = narration,
                    chequeRefNo = null,
                    withdrawalAmt = withdrawalAmt,
                    depositAmt = depositAmt,
                    closingBalance = null,
                    userId = userId,
                    predictedCategory = prediction?.predictedCategory,
                    predictedTransactionType = prediction?.transactionType,
                    predictedIntent = prediction?.intent,
                    predictionConfidence = prediction?.confidence,
                    categoryName = category ?: prediction?.predictedCategory,
                    amount = calculatedAmount  // Match Spring Boot: deposit - withdrawal
                )
                
                android.util.Log.d("AddTransactionViewModel", "💾 Saving transaction to database...")
                android.util.Log.d("AddTransactionViewModel", "   ID: ${transaction.id}")
                android.util.Log.d("AddTransactionViewModel", "   Date: ${transaction.date}")
                android.util.Log.d("AddTransactionViewModel", "   Narration: ${transaction.narration}")
                android.util.Log.d("AddTransactionViewModel", "   Amount: ${transaction.amount}")
                android.util.Log.d("AddTransactionViewModel", "   Category: ${transaction.categoryName}")
                android.util.Log.d("AddTransactionViewModel", "   Predicted Category: ${transaction.predictedCategory}")
                
                val transactionId = transactionRepository.insertTransaction(transaction)
                
                android.util.Log.d("AddTransactionViewModel", "✅ Transaction saved! ID: $transactionId")
                
                _state.update {
                    it.copy(
                        isSaving = false,
                        isSaved = true
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("AddTransactionViewModel", "❌ Save failed", e)
                _state.update {
                    it.copy(
                        isSaving = false,
                        error = "Failed to save: ${e.message}"
                    )
                }
            }
        }
    }
}

