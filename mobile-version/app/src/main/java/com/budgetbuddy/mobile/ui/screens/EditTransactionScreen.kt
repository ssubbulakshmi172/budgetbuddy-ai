package com.budgetbuddy.mobile.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Scaffold
import androidx.navigation.NavController
import com.budgetbuddy.mobile.ui.viewmodel.AddTransactionViewModel
import com.budgetbuddy.mobile.ui.viewmodel.AddTransactionViewModelFactory
import com.budgetbuddy.mobile.ui.viewmodel.ViewModelProvider as VMProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.budgetbuddy.mobile.util.CurrencyFormatter
import java.time.LocalDate
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTransactionScreen(
    transactionId: Long,
    navController: NavController
) {
    val context = LocalContext.current
    val transactionRepository = VMProvider.getTransactionRepository(context)
    val mlService = VMProvider.getMLService(context)
    val userId = VMProvider.getUserId()
    
    val viewModel: AddTransactionViewModel = viewModel(
        factory = AddTransactionViewModelFactory(transactionRepository, mlService, userId)
    )
    val state by viewModel.state.collectAsState()
    
    var transaction by remember { mutableStateOf<com.budgetbuddy.mobile.data.model.Transaction?>(null) }
    var spendFor by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var category by remember { mutableStateOf("") }
    var transactionType by remember { mutableStateOf(TransactionType.WITHDRAWAL) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    // Load transaction data
    LaunchedEffect(transactionId) {
        val loadedTransaction = transactionRepository.getTransactionById(transactionId)
        if (loadedTransaction != null) {
            transaction = loadedTransaction
            spendFor = loadedTransaction.narration?.split(" - ")?.firstOrNull() ?: loadedTransaction.narration ?: ""
            description = loadedTransaction.narration?.split(" - ")?.getOrNull(1) ?: ""
            amount = kotlin.math.abs(loadedTransaction.amount).toString()
            date = loadedTransaction.date.toString()
            category = loadedTransaction.categoryName ?: loadedTransaction.predictedCategory ?: ""
            transactionType = if (loadedTransaction.amount >= 0) TransactionType.DEPOSIT else TransactionType.WITHDRAWAL
        }
    }
    
    // Show success snackbar and navigate back
    LaunchedEffect(state.isSaved) {
        if (state.isSaved) {
            snackbarHostState.showSnackbar("Transaction updated successfully!")
            kotlinx.coroutines.delay(1500)
            navController.popBackStack()
        }
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Edit Transaction",
                        fontWeight = FontWeight.Bold
                    ) 
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        if (transaction == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(paddingValues)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Transaction Type Selection
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TransactionTypeButton(
                                type = TransactionType.WITHDRAWAL,
                                selected = transactionType == TransactionType.WITHDRAWAL,
                                onClick = { transactionType = TransactionType.WITHDRAWAL },
                                modifier = Modifier.weight(1f)
                            )
                            TransactionTypeButton(
                                type = TransactionType.DEPOSIT,
                                selected = transactionType == TransactionType.DEPOSIT,
                                onClick = { transactionType = TransactionType.DEPOSIT },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    
                    // Amount Field
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { if (it.isEmpty() || it.matches(Regex("\\d*(\\.\\d*)?"))) amount = it },
                        label = { Text("Amount *") },
                        placeholder = { Text("0.00") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { 
                            Icon(
                                if (transactionType == TransactionType.DEPOSIT) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                contentDescription = null,
                                tint = if (transactionType == TransactionType.DEPOSIT) 
                                    MaterialTheme.colorScheme.primary 
                                else 
                                    MaterialTheme.colorScheme.error
                            ) 
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                    
                    // Spend For Field
                    OutlinedTextField(
                        value = spendFor,
                        onValueChange = { spendFor = it },
                        label = { Text(if (transactionType == TransactionType.DEPOSIT) "Received From *" else "Spend For *") },
                        placeholder = { Text(if (transactionType == TransactionType.DEPOSIT) "e.g., Salary, Refund" else "e.g., Starbucks Coffee") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { 
                            Icon(
                                if (transactionType == TransactionType.DEPOSIT) Icons.Default.AccountBalanceWallet else Icons.Default.ShoppingBag,
                                contentDescription = null
                            ) 
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                    
                    // Additional Description Field
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Additional Notes") },
                        placeholder = { Text("Optional: Add more details") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null) },
                        singleLine = false,
                        maxLines = 2,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                    
                    // Date Field
                    OutlinedTextField(
                        value = date,
                        onValueChange = { date = it },
                        label = { Text("Date") },
                        placeholder = { Text("YYYY-MM-DD") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Outlined.DateRange, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                    
                    // Category Field
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Category") },
                        placeholder = { Text("e.g., Food & Groceries") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Category, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                    
                    // AI Prediction Button
                    AnimatedVisibility(
                        visible = spendFor.isNotEmpty(),
                        enter = fadeIn() + slideInVertically(),
                        exit = fadeOut() + slideOutVertically()
                    ) {
                        Button(
                            onClick = {
                                val narrationText = if (description.isNotEmpty()) "$spendFor - $description" else spendFor
                                val amountValue = amount.toDoubleOrNull() ?: 0.0
                                val finalAmount = if (transactionType == TransactionType.WITHDRAWAL) -amountValue else amountValue
                                viewModel.predictTransaction(narrationText, finalAmount)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Refresh AI Prediction", fontWeight = FontWeight.SemiBold)
                        }
                    }
                    
                    // Prediction Results
                    AnimatedVisibility(
                        visible = state.prediction != null,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        if (state.prediction != null) {
                            PredictionCard(state.prediction!!)
                            
                            LaunchedEffect(state.prediction) {
                                state.prediction?.let { pred ->
                                    if (pred.predictedCategory.isNotEmpty() && category.isEmpty()) {
                                        category = pred.predictedCategory
                                    }
                                }
                            }
                        }
                    }
                    
                    // Error message
                    state.error?.let { error ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = error,
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Update Button
                    Button(
                        onClick = {
                            val amountValue = amount.toDoubleOrNull() ?: 0.0
                            val finalAmount = if (transactionType == TransactionType.WITHDRAWAL) -amountValue else amountValue
                            val transactionDate = try {
                                LocalDate.parse(date)
                            } catch (e: Exception) {
                                LocalDate.now()
                            }
                            
                            val narrationText = if (description.isNotEmpty()) "$spendFor - $description" else spendFor
                            
                            val updatedTransaction = transaction!!.copy(
                                narration = narrationText,
                                amount = finalAmount,
                                date = transactionDate,
                                categoryName = category.takeIf { it.isNotEmpty() },
                                predictedCategory = state.prediction?.predictedCategory,
                                predictedTransactionType = state.prediction?.transactionType,
                                predictedIntent = state.prediction?.intent,
                                predictionConfidence = state.prediction?.confidence
                            )
                            
                            scope.launch {
                                try {
                                    transactionRepository.updateTransaction(updatedTransaction)
                                    snackbarHostState.showSnackbar("Transaction updated successfully!")
                                    kotlinx.coroutines.delay(1500)
                                    navController.popBackStack()
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar("Error updating transaction: ${e.message}")
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        enabled = spendFor.isNotEmpty() && amount.isNotEmpty() && !state.isSaving,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Update Transaction", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

