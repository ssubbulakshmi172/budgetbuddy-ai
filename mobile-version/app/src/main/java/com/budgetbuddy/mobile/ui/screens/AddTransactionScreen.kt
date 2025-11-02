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
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

enum class TransactionType {
    DEPOSIT, WITHDRAWAL
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
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
    
    var spendFor by remember { mutableStateOf("") }  // What you spent on
    var description by remember { mutableStateOf("") }  // Additional notes/description
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var category by remember { mutableStateOf("") }
    var transactionType by remember { mutableStateOf(TransactionType.WITHDRAWAL) }
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Show success snackbar and navigate back
    LaunchedEffect(state.isSaved) {
        if (state.isSaved) {
            snackbarHostState.showSnackbar("Transaction saved successfully!")
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
                        "Add Transaction",
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
            // Transaction Type Selection (Withdraw/Deposit)
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
            
            // Spend For Field (Primary description)
            OutlinedTextField(
                value = spendFor,
                onValueChange = { spendFor = it },
                label = { Text(if (transactionType == TransactionType.DEPOSIT) "Received From *" else "Spend For *") },
                placeholder = { Text(if (transactionType == TransactionType.DEPOSIT) "e.g., Salary, Refund, Transfer" else "e.g., Starbucks Coffee, Groceries, Uber") },
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
            
            // Additional Description Field (Optional)
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Additional Notes") },
                placeholder = { Text("Optional: Add more details, location, etc.") },
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
            
            // AI Prediction Button
            AnimatedVisibility(
                visible = spendFor.isNotEmpty(),
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                Button(
                    onClick = {
                        val narrationText = if (description.isNotEmpty()) "$spendFor - $description" else spendFor
                        android.util.Log.d("AddTransaction", "🔍 Testing ML prediction for: $narrationText")
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
                    Text("Get AI Prediction", fontWeight = FontWeight.SemiBold)
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
            
            // Loading indicator (for manual prediction button)
            AnimatedVisibility(
                visible = state.isPredicting && !state.isSaving,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            // Saving indicator (includes auto-prediction)
            AnimatedVisibility(
                visible = state.isSaving,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Predicting and saving...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
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
            
            // Save Button
            Button(
                onClick = {
                    val amountValue = amount.toDoubleOrNull() ?: 0.0
                    val finalAmount = if (transactionType == TransactionType.WITHDRAWAL) -amountValue else amountValue
                    val transactionDate = try {
                        LocalDate.parse(date)
                    } catch (e: Exception) {
                        LocalDate.now()
                    }
                    
                    // Combine spendFor and description for narration
                    val narrationText = if (description.isNotEmpty()) "$spendFor - $description" else spendFor
                    
                    android.util.Log.d("AddTransaction", "💾 Saving transaction: type=$transactionType, amount=$finalAmount, narration=$narrationText")
                    
                    viewModel.addTransaction(
                        narration = narrationText,
                        amount = finalAmount,
                        date = transactionDate,
                        category = category.takeIf { it.isNotEmpty() },
                        transactionType = transactionType
                    )
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
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Saving...", fontWeight = FontWeight.SemiBold)
                } else {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save Transaction", fontWeight = FontWeight.SemiBold)
                }
            }
            }
        }
    }
}

@Composable
fun TransactionTypeButton(
    type: TransactionType,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val targetColor = if (selected) {
        if (type == TransactionType.DEPOSIT) 
            MaterialTheme.colorScheme.primary 
        else 
            MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    }
    
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = targetColor
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Icon(
            imageVector = if (type == TransactionType.DEPOSIT) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
            contentDescription = null
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (type == TransactionType.DEPOSIT) "Deposit" else "Withdraw",
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun PredictionCard(prediction: com.budgetbuddy.mobile.ml.PyTorchMobileInferenceService.PredictionResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Text(
                    text = "AI Prediction",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            
            HorizontalDivider(color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.3f))
            
            PredictionRow("Category", prediction.predictedCategory)
            PredictionRow("Type", prediction.transactionType)
            PredictionRow("Intent", prediction.intent)
            PredictionRow(
                "Confidence",
                "${String.format("%.1f", prediction.confidence * 100)}%"
            )
        }
    }
}

@Composable
fun PredictionRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}
