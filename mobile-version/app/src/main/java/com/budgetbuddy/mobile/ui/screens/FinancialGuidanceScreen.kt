package com.budgetbuddy.mobile.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.budgetbuddy.mobile.data.model.*
import com.budgetbuddy.mobile.ui.navigation.Screen
import com.budgetbuddy.mobile.util.CurrencyFormatter
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/**
 * Financial Guidance Screen - Equivalent to Spring Boot guidance dashboard
 * 
 * Features:
 * 1. Year-End Savings Projection
 * 2. Top 3 Money Leaks
 * 3. Category Overspending Alerts
 * 4. Weekend Overspending
 * 5. Regular Monthly Spending (Expenses & Investments)
 * 6. Grocery vs Eating-Out
 * 7. Investment Tracking
 * 8. Subscriptions Analysis
 * 9. Unusual Spending Patterns (ML-based, optional)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinancialGuidanceScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Get database and DAOs
    val app = context.applicationContext as com.budgetbuddy.mobile.BudgetBuddyApplication
    val database = app.database
    val userId = com.budgetbuddy.mobile.ui.viewmodel.ViewModelProvider.getUserId()
    
    // Initialize services
    val categoryOverspendingService = com.budgetbuddy.mobile.service.CategoryOverspendingService(
        database.transactionDao(),
        database.categoryOverspendingAlertDao()
    )
    val savingsProjectionService = com.budgetbuddy.mobile.service.SavingsProjectionService(
        database.transactionDao(),
        database.savingsProjectionDao()
    )
    val weekendOverspendingService = com.budgetbuddy.mobile.service.WeekendOverspendingService(
        database.transactionDao(),
        database.weekendOverspendingDao()
    )
    val moneyLeakService = com.budgetbuddy.mobile.service.MoneyLeakService(
        database.transactionDao(),
        database.moneyLeakDao()
    )
    val financialAnalyticsService = com.budgetbuddy.mobile.service.FinancialAnalyticsService(
        database.transactionDao()
    )
    
    // State for all financial guidance data
    var savingsProjection by remember { mutableStateOf<SavingsProjection?>(null) }
    var moneyLeaks by remember { mutableStateOf<List<MoneyLeak>>(emptyList()) }
    var categoryAlerts by remember { mutableStateOf<List<CategoryOverspendingAlert>>(emptyList()) }
    var weekendOverspending by remember { mutableStateOf<List<WeekendOverspending>>(emptyList()) }
    var regularSpending by remember { mutableStateOf<List<MoneyLeak>>(emptyList()) }
    var groceryVsEatingOut by remember { mutableStateOf<Map<String, Any>?>(null) }
    var investmentTracking by remember { mutableStateOf<Map<String, Any>?>(null) }
    var subscriptions by remember { mutableStateOf<Map<String, Any>?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    
    // Load data
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                isLoading = true
                
                // Calculate and load savings projection
                savingsProjection = savingsProjectionService.calculateYearEndSavings(userId)
                
                // Detect and load top 3 money leaks
                moneyLeaks = moneyLeakService.detectMoneyLeaks(userId)
                
                // Detect and load category overspending alerts
                categoryAlerts = categoryOverspendingService.detectOverspending(userId)
                
                // Detect and load weekend overspending
                weekendOverspending = weekendOverspendingService.detectWeekendOverspending(userId)
                
                // Load regular monthly spending
                regularSpending = moneyLeakService.detectRegularMonthlySpending(userId)
                
                // Load financial analytics
                groceryVsEatingOut = financialAnalyticsService.analyzeGroceryVsEatingOut(userId)
                investmentTracking = financialAnalyticsService.trackInvestments(userId)
                subscriptions = financialAnalyticsService.analyzeSubscriptions(userId)
                
                isLoading = false
            } catch (e: Exception) {
                error = e.message
                isLoading = false
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Financial Insights",
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
                },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                try {
                                    isLoading = true
                                    
                                    // Recalculate all guidance features
                                    savingsProjection = savingsProjectionService.calculateYearEndSavings(userId)
                                    moneyLeaks = moneyLeakService.detectMoneyLeaks(userId)
                                    categoryAlerts = categoryOverspendingService.detectOverspending(userId)
                                    weekendOverspending = weekendOverspendingService.detectWeekendOverspending(userId)
                                    regularSpending = moneyLeakService.detectRegularMonthlySpending(userId)
                                    groceryVsEatingOut = financialAnalyticsService.analyzeGroceryVsEatingOut(userId)
                                    investmentTracking = financialAnalyticsService.trackInvestments(userId)
                                    subscriptions = financialAnalyticsService.analyzeSubscriptions(userId)
                                    
                                    isLoading = false
                                } catch (e: Exception) {
                                    error = e.message
                                    isLoading = false
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (error != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Error loading guidance",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = error ?: "Unknown error",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(paddingValues)
            ) {
                // Section Header: Overview
                SectionHeader(
                    title = "Overview",
                    icon = Icons.Default.Speed
                )
                
                // 1. Year-End Savings Projection
                savingsProjection?.let { projection ->
                    SavingsProjectionCard(projection = projection)
                }
                
                // Section Header: Alerts & Issues
                SectionHeader(
                    title = "Alerts & Issues",
                    icon = Icons.Default.Warning
                )
                
                // 2. Top 3 Money Leaks
                if (moneyLeaks.isNotEmpty()) {
                    Text(
                        text = "Top 3 Money Leaks",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    moneyLeaks.forEach { leak ->
                        MoneyLeakCard(
                            leak = leak,
                            onClick = {
                                // Navigate to filtered transactions
                                // TODO: Implement navigation to transaction list with filter
                            }
                        )
                    }
                }
                
                // 3. Category Overspending Alerts
                if (categoryAlerts.isNotEmpty()) {
                    Text(
                        text = "Category Overspending Alerts",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    categoryAlerts.forEach { alert ->
                        CategoryOverspendingCard(
                            alert = alert,
                            onClick = {
                                // Navigate to filtered transactions
                                // TODO: Implement navigation to transaction list with filter
                            }
                        )
                    }
                }
                
                // Section Header: Patterns & Trends
                SectionHeader(
                    title = "Patterns & Trends",
                    icon = Icons.Default.TrendingUp
                )
                
                // 4. Weekend Overspending
                if (weekendOverspending.isNotEmpty()) {
                    Text(
                        text = "Weekend Overspending",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    weekendOverspending.forEach { weekend ->
                        WeekendOverspendingCard(
                            weekend = weekend,
                            onClick = {
                                // Navigate to filtered transactions
                                // TODO: Implement navigation to transaction list with filter
                            }
                        )
                    }
                }
                
                // 5. Regular Monthly Spending
                if (regularSpending.isNotEmpty()) {
                    Text(
                        text = "Regular Monthly Spending",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    
                    // Separate expenses and investments
                    val expenses = regularSpending.filter { 
                        !it.title.startsWith("Monthly Investment") 
                    }
                    val investments = regularSpending.filter { 
                        it.title.startsWith("Monthly Investment") 
                    }
                    
                    if (expenses.isNotEmpty()) {
                        Text(
                            text = "Monthly Expenses",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                        expenses.take(5).forEach { expense ->
                            MoneyLeakCard(leak = expense, onClick = {})
                        }
                    }
                    
                    if (investments.isNotEmpty()) {
                        Text(
                            text = "Monthly Investments",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                        investments.take(5).forEach { investment ->
                            MoneyLeakCard(leak = investment, onClick = {})
                        }
                    }
                }
                
                // 6. Grocery vs Eating-Out
                groceryVsEatingOut?.let { data ->
                    Text(
                        text = "Grocery vs Eating-Out",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "Grocery",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = CurrencyFormatter.format((data["total_grocery"] as? Number)?.toDouble() ?: 0.0),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "${data["overall_grocery_percent"]}%",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Eating Out",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = CurrencyFormatter.format((data["total_eating_out"] as? Number)?.toDouble() ?: 0.0),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = "${data["overall_eating_out_percent"]}%",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            (data["improvement_suggestion"] as? String)?.let { suggestion ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        text = "💡 $suggestion",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                
                // 7. Investment Tracking
                investmentTracking?.let { data ->
                    Text(
                        text = "Investment Tracking",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "Total Invested",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = CurrencyFormatter.format((data["total_invested"] as? Number)?.toDouble() ?: 0.0),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Avg Monthly",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = CurrencyFormatter.format((data["average_monthly"] as? Number)?.toDouble() ?: 0.0),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                text = "${data["total_transactions"]} investment transactions",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
                
                // 8. Subscriptions Analysis
                subscriptions?.let { data ->
                    val subsList = data["subscriptions"] as? List<Map<String, Any>> ?: emptyList()
                    if (subsList.isNotEmpty()) {
                        Text(
                            text = "Subscriptions Analysis",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Total Monthly",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = CurrencyFormatter.format((data["total_monthly"] as? Number)?.toDouble() ?: 0.0),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "${data["count"]} active subscriptions",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                                subsList.take(5).forEach { sub ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = sub["merchant"] as? String ?: "Unknown",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = CurrencyFormatter.format((sub["monthly_amount"] as? Number)?.toDouble() ?: 0.0),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                // 9. Unusual Spending Patterns (ML-based)
                Text(
                    text = "Unusual Spending Patterns",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "ML-Powered Analysis",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Click 'Analyze Transactions' to detect unusual spending patterns using machine learning",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                        Button(
                            onClick = {
                                // TODO: Implement ML-based anomaly detection
                                // This will call MoneyLeakService.detectAnomalies()
                            }
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Analyze Transactions")
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun SavingsProjectionCard(projection: SavingsProjection) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Year-End Projection",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Includes income, expenses, and investments",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                Icon(
                    imageVector = Icons.Default.Savings,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            Divider()
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Current Savings",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = CurrencyFormatter.format(projection.currentSavings),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Projected Year-End",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = CurrencyFormatter.format(projection.projectedYearEnd),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                InfoItem(
                    label = "Monthly Savings Rate",
                    value = CurrencyFormatter.format(projection.monthlySavingsRate)
                )
                InfoItem(
                    label = "Remaining Months",
                    value = "${projection.remainingMonths}"
                )
            }
        }
    }
}

@Composable
fun MoneyLeakCard(
    leak: MoneyLeak,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    leak.rank?.let { rank ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "#$rank",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Text(
                        text = leak.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = CurrencyFormatter.format(leak.annualAmount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }
            
            leak.description?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                InfoItem(
                    label = "Monthly",
                    value = CurrencyFormatter.format(leak.monthlyAmount)
                )
                leak.transactionCount?.let { count ->
                    InfoItem(
                        label = "Transactions",
                        value = "$count"
                    )
                }
            }
            
            leak.suggestion?.let { suggestion ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = "💡 $suggestion",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryOverspendingCard(
    alert: CategoryOverspendingAlert,
    onClick: () -> Unit
) {
    val alertColor = when (alert.alertLevel) {
        CategoryOverspendingAlert.AlertLevel.CRITICAL -> MaterialTheme.colorScheme.error
        CategoryOverspendingAlert.AlertLevel.HIGH -> MaterialTheme.colorScheme.errorContainer
        CategoryOverspendingAlert.AlertLevel.MEDIUM -> MaterialTheme.colorScheme.tertiaryContainer
        CategoryOverspendingAlert.AlertLevel.LOW -> MaterialTheme.colorScheme.secondaryContainer
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = alertColor.copy(alpha = 0.3f)
        ),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = alert.category,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = alertColor
                ) {
                    Text(
                        text = alert.alertLevel.name,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.onError
                    )
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Current Month",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = CurrencyFormatter.format(alert.currentAmount),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Historical Avg",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = CurrencyFormatter.format(alert.historicalAvg),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
            
            alert.percentageIncrease?.let { increase ->
                Text(
                    text = "+${String.format("%.1f", increase)}% increase",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun WeekendOverspendingCard(
    weekend: WeekendOverspending,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = weekend.category ?: "Overall",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                weekend.alertLevel?.let { level ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = when (level) {
                            WeekendOverspending.AlertLevel.HIGH -> MaterialTheme.colorScheme.errorContainer
                            WeekendOverspending.AlertLevel.MEDIUM -> MaterialTheme.colorScheme.tertiaryContainer
                            WeekendOverspending.AlertLevel.LOW -> MaterialTheme.colorScheme.secondaryContainer
                        }
                    ) {
                        Text(
                            text = level.name,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Weekend Avg",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = CurrencyFormatter.format(weekend.weekendAvg),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Weekday Avg",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = CurrencyFormatter.format(weekend.weekdayAvg),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Ratio: ${String.format("%.2f", weekend.ratio)}x",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                weekend.percentageIncrease?.let { increase ->
                    Text(
                        text = "+${String.format("%.1f", increase)}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun InfoItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}
