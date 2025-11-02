package com.budgetbuddy.mobile.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetbuddy.mobile.data.importer.FileImporter
import com.budgetbuddy.mobile.data.model.Transaction
import com.budgetbuddy.mobile.data.repository.TransactionRepository
import com.budgetbuddy.mobile.ml.PyTorchMobileInferenceService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class UploadState(
    val isProcessing: Boolean = false,
    val importedCount: Int = 0,
    val processedCount: Int = 0,
    val error: String? = null,
    val success: Boolean = false
)

class UploadViewModel(
    private val fileImporter: FileImporter,
    private val transactionRepository: TransactionRepository,
    private val mlService: PyTorchMobileInferenceService,
    private val userId: Long
) : ViewModel() {
    
    private val _state = MutableStateFlow(UploadState())
    val state: StateFlow<UploadState> = _state.asStateFlow()
    
    fun importFile(uri: Uri, isExcel: Boolean) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isProcessing = true,
                    error = null,
                    success = false,
                    importedCount = 0,
                    processedCount = 0
                )
            }
            
            try {
                android.util.Log.d("UploadViewModel", "Starting import: isExcel=$isExcel, userId=$userId")
                // Import file
                val importResult = if (isExcel) {
                    fileImporter.importFromExcel(uri, userId)
                } else {
                    fileImporter.importFromCSV(uri, userId)
                }
                
                android.util.Log.d("UploadViewModel", "Import result: success=${importResult.success}, count=${importResult.transactions.size}, errors=${importResult.errors.size}")
                
                if (!importResult.success) {
                    val errorMsg = if (importResult.errors.isNotEmpty()) {
                        importResult.errors.joinToString("\n")
                    } else {
                        "Import failed"
                    }
                    android.util.Log.e("UploadViewModel", "Import failed: $errorMsg")
                    _state.update {
                        it.copy(
                            isProcessing = false,
                            error = errorMsg
                        )
                    }
                    return@launch
                }
                
                if (importResult.transactions.isEmpty()) {
                    android.util.Log.w("UploadViewModel", "No transactions found in file")
                    _state.update {
                        it.copy(
                            isProcessing = false,
                            error = "No transactions found in file. Check file format."
                        )
                    }
                    return@launch
                }
                
                _state.update { it.copy(importedCount = importResult.transactions.size) }
                
                // Run ML predictions on imported transactions with progress updates
                val totalCount = importResult.transactions.size
                android.util.Log.d("UploadViewModel", "🚀 Processing $totalCount transactions with ML predictions...")
                
                val startTime = System.currentTimeMillis()
                val transactionsWithPredictions = processTransactionsWithProgress(importResult.transactions) { processed ->
                    val progress = (processed * 100 / totalCount)
                    android.util.Log.d("UploadViewModel", "📊 Progress: $processed/$totalCount ($progress%)")
                    // Optionally update UI with progress (if you want to show it)
                }
                val predictionTime = System.currentTimeMillis() - startTime
                android.util.Log.d("UploadViewModel", "✅ ML predictions completed in ${predictionTime}ms (avg: ${predictionTime / totalCount}ms per transaction)")
                
                // Save to database
                val saveStartTime = System.currentTimeMillis()
                android.util.Log.d("UploadViewModel", "💾 Saving ${transactionsWithPredictions.size} transactions to database...")
                transactionRepository.insertTransactions(transactionsWithPredictions)
                val saveTime = System.currentTimeMillis() - saveStartTime
                android.util.Log.d("UploadViewModel", "✅ Transactions saved successfully in ${saveTime}ms")
                
                _state.update {
                    it.copy(
                        isProcessing = false,
                        processedCount = transactionsWithPredictions.size,
                        success = true
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("UploadViewModel", "Import exception: ${e.message}", e)
                android.util.Log.e("UploadViewModel", "Stack trace:", e)
                val errorMsg = when {
                    e.message?.contains("Could not open", ignoreCase = true) == true -> 
                        "Could not open file. Please check file permissions."
                    e.message?.contains("format", ignoreCase = true) == true -> 
                        "Invalid file format. Please use .xlsx, .xls, or .csv files."
                    e.message?.contains("permission", ignoreCase = true) == true -> 
                        "Permission denied. Please grant file access permission."
                    e.message?.contains("No transactions", ignoreCase = true) == true ->
                        "No transactions found. Please check your file format."
                    else -> e.message ?: "Error importing file: ${e.javaClass.simpleName}"
                }
                _state.update {
                    it.copy(
                        isProcessing = false,
                        error = errorMsg
                    )
                }
            }
        }
    }
    
    /**
     * Process transactions with ML predictions, using batch processing when possible
     */
    private suspend fun processTransactionsWithProgress(
        transactions: List<Transaction>,
        onProgress: (processed: Int) -> Unit
    ): List<Transaction> {
        val transactionsToProcess = transactions.filter { 
            it.narration != null && it.narration.isNotEmpty() 
        }
        val transactionsWithoutNarration = transactions.filter { 
            it.narration.isNullOrEmpty() 
        }
        
        android.util.Log.d("UploadViewModel", "Processing ${transactionsToProcess.size} transactions with ML (${transactionsWithoutNarration.size} skipped - no narration)")
        
        if (transactionsToProcess.isEmpty()) {
            return transactions
        }
        
        // Process in smaller batches to avoid memory issues and show progress
        val batchSize = 50 // Process 50 transactions at a time
        val result = mutableListOf<Transaction>()
        var processedCount = 0
        
        transactionsToProcess.chunked(batchSize).forEachIndexed { batchIndex, batch ->
            android.util.Log.d("UploadViewModel", "Processing batch ${batchIndex + 1} (${batch.size} transactions)")
            val batchStartTime = System.currentTimeMillis()
            
            // Get narrations for batch prediction
            val narrations = batch.map { it.narration!! }
            
            try {
                // Use batch prediction if available (more efficient)
                val predictions = mlService.predictBatch(narrations)
                
                // Combine predictions with transactions
                batch.forEachIndexed { index, transaction ->
                    val prediction = predictions[index]
                    val categoryName = matchCategoryKeyword(transaction.narration!!)
                    
                    result.add(
                        transaction.copy(
                            predictedCategory = prediction.predictedCategory,
                            predictedTransactionType = prediction.transactionType,
                            predictedIntent = prediction.intent,
                            predictionConfidence = prediction.confidence,
                            categoryName = categoryName
                        )
                    )
                    processedCount++
                    onProgress(processedCount)
                }
                
                val batchTime = System.currentTimeMillis() - batchStartTime
                android.util.Log.d("UploadViewModel", "Batch ${batchIndex + 1} completed in ${batchTime}ms (${batchTime / batch.size}ms avg per transaction)")
            } catch (e: Exception) {
                android.util.Log.e("UploadViewModel", "Error processing batch ${batchIndex + 1}: ${e.message}", e)
                // Continue with unprocessed transactions (no predictions)
                batch.forEach { transaction ->
                    result.add(transaction)
                    processedCount++
                    onProgress(processedCount)
                }
            }
        }
        
        // Add transactions without narration (no predictions needed)
        result.addAll(transactionsWithoutNarration)
        processedCount += transactionsWithoutNarration.size
        onProgress(processedCount)
        
        return result
    }
    
    private suspend fun processTransactions(
        transactions: List<Transaction>
    ): List<Transaction> {
        return processTransactionsWithProgress(transactions) { }
    }
    
    private suspend fun matchCategoryKeyword(narration: String): String? {
        // This should query CategoryKeywordDao to match keywords
        // Simplified for now
        return null
    }
    
    fun reset() {
        _state.value = UploadState()
    }
}

