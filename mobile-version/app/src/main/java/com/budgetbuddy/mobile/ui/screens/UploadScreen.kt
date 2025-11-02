package com.budgetbuddy.mobile.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.budgetbuddy.mobile.ui.viewmodel.UploadViewModel
import com.budgetbuddy.mobile.ui.viewmodel.UploadViewModelFactory
import com.budgetbuddy.mobile.ui.viewmodel.ViewModelProvider as VMProvider
import com.budgetbuddy.mobile.BudgetBuddyApplication
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadScreen(
    navController: NavController
) {
    // Log when screen is composed
    LaunchedEffect(Unit) {
        android.util.Log.d("UploadScreen", "📱 UploadScreen composed/loaded")
    }
    
    val context = LocalContext.current
    val app = context.applicationContext as BudgetBuddyApplication
    val transactionRepository = VMProvider.getTransactionRepository(context)
    val fileImporter = VMProvider.getFileImporter(context)
    val userId = VMProvider.getUserId()
    val viewModel: UploadViewModel = viewModel(
        factory = UploadViewModelFactory(
            fileImporter,
            transactionRepository,
            app.mlService,
            userId
        )
    )
    val state by viewModel.state.collectAsState()
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var isExcel by remember { mutableStateOf(false) }
    
    // MIME types for file selection
    val excelMimeTypes = arrayOf(
        "application/vnd.ms-excel",                    // .xls (Excel 97-2003)
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" // .xlsx (Excel 2007+)
    )
    val csvMimeTypes = arrayOf(
        "text/csv",
        "application/csv"
    )
    
    // Use OpenDocument for multiple MIME types support (better for Excel files)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            selectedFileUri = it
            viewModel.importFile(it, isExcel)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Upload Transactions",
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
                            imageVector = androidx.compose.material.icons.Icons.Default.ArrowBack,
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
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        Icon(
            imageVector = Icons.Default.Upload,
            contentDescription = "Upload",
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Upload Transactions",
            style = MaterialTheme.typography.headlineMedium
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Select a CSV or Microsoft Excel file to upload transactions",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // File Format Requirements (matching Spring Boot)
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "File Format Requirements:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "• CSV files should have headers in the first row\n" +
                           "• Required columns: Date, Narration/Description, Amount\n" +
                           "• Dates should be in YYYY-MM-DD format (or dd/MM/yy for Excel)\n" +
                           "• Excel: Date(0), Narration(1), ChequeRefNo(2), Withdrawal(4), Deposit(5), ClosingBalance(6)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // File Type Selection
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilterChip(
                selected = !isExcel,
                onClick = { isExcel = false },
                label = { Text("CSV") },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = isExcel,
                onClick = { isExcel = true },
                label = { Text("Excel (.xlsx, .xls)") },
                modifier = Modifier.weight(1f)
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = { 
                // Launch file picker with appropriate MIME types based on selection
                // OpenDocument accepts an array of MIME types
                val mimeTypes = if (isExcel) {
                    excelMimeTypes
                } else {
                    csvMimeTypes
                }
                filePickerLauncher.launch(mimeTypes)
            },
            enabled = !state.isProcessing,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Processing...")
            } else {
                Text("Select File")
            }
        }
        
        // Status Messages
        if (state.success) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Upload Successful!",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Uploaded: ${state.importedCount} transactions\n" +
                                "Processed: ${state.processedCount} transactions",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
        
        val errorMessage = state.error
        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = errorMessage,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
        
        if (state.success) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    viewModel.reset()
                    navController.navigate("dashboard") {
                        popUpTo("dashboard") { inclusive = false }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Go to Dashboard")
            }
        }
        }
    }
}

