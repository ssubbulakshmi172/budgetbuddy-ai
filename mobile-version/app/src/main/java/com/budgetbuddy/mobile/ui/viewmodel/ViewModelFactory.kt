package com.budgetbuddy.mobile.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.budgetbuddy.mobile.BudgetBuddyApplication
import com.budgetbuddy.mobile.data.importer.FileImporter
import com.budgetbuddy.mobile.data.repository.TransactionRepository

class DashboardViewModelFactory(
    private val transactionRepository: TransactionRepository,
    private val userId: Long
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DashboardViewModel(transactionRepository, userId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class TransactionViewModelFactory(
    private val transactionRepository: TransactionRepository,
    private val userId: Long
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TransactionViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TransactionViewModel(transactionRepository, userId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class UploadViewModelFactory(
    private val fileImporter: FileImporter,
    private val transactionRepository: TransactionRepository,
    private val mlService: com.budgetbuddy.mobile.ml.PyTorchMobileInferenceService,
    private val userId: Long
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(UploadViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return UploadViewModel(fileImporter, transactionRepository, mlService, userId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class AddTransactionViewModelFactory(
    private val transactionRepository: TransactionRepository,
    private val mlService: com.budgetbuddy.mobile.ml.PyTorchMobileInferenceService,
    private val userId: Long
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AddTransactionViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AddTransactionViewModel(transactionRepository, mlService, userId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

// Helper to get dependencies from Application
object ViewModelProvider {
    fun getTransactionRepository(context: android.content.Context): TransactionRepository {
        val app = context.applicationContext as BudgetBuddyApplication
        return com.budgetbuddy.mobile.data.repository.TransactionRepository(
            app.database.transactionDao()
        )
    }
    
    fun getFileImporter(context: android.content.Context): FileImporter {
        return com.budgetbuddy.mobile.data.importer.FileImporter(context)
    }
    
    fun getUserId(): Long {
        // For now, use a default user ID (1)
        // TODO: Get from preferences or user management
        return 1L
    }
    
    fun getMLService(context: android.content.Context): com.budgetbuddy.mobile.ml.PyTorchMobileInferenceService {
        val app = context.applicationContext as BudgetBuddyApplication
        return app.mlService
    }
}

