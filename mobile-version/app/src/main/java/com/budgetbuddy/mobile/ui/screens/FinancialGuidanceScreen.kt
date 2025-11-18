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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinancialGuidanceScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Get services from DI (simplified - in production use proper DI)
    val app = context.applicationContext as com.budgetbuddy.mobile.BudgetBuddyApplication
    val database = app.database
    val userId = com.budgetbuddy.mobile.ui.viewmodel.ViewModelProvider.getUserId()
    
    val spendingPatternService = com.budgetbuddy.mobile.service.SpendingPatternService(
        database.spendingPatternDao(),
        database.transactionDao()
    )
    val trendAnalysisService = com.budgetbuddy.mobile.service.TrendAnalysisService(
        database.transactionDao()
    )
    val spendingPredictionService = com.budgetbuddy.mobile.service.SpendingPredictionService(
        database.spendingPredictionDao(),
        database.spendingPatternDao(),
        database.transactionDao(),
        trendAnalysisService
    )
    val financialNudgeService = com.budgetbuddy.mobile.service.FinancialNudgeService(
        database.financialNudgeDao(),
        database.spendingPredictionDao(),
        database.spendingPatternDao(),
        database.transactionDao(),
        spendingPredictionService
    )
    
    var patterns by remember { mutableStateOf<List<SpendingPattern>>(emptyList()) }
    var trends by remember { mutableStateOf<TrendAnalysisResult?>(null) }
    var predictions by remember { mutableStateOf<List<SpendingPrediction>>(emptyList()) }
    var nudges by remember { mutableStateOf<List<FinancialNudge>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                isLoading = true
                
                // Load all data
                patterns = spendingPatternService.getActivePatterns(userId)
                trends = trendAnalysisService.analyzeTrends(userId)
                
                // Get predictions for next month
                val nextMonthStart = java.time.LocalDate.now().withDayOfMonth(1).plusMonths(1)
                val nextMonthEnd = nextMonthStart.withDayOfMonth(nextMonthStart.lengthOfMonth())
                predictions = spendingPredictionService.predictFutureSpending(userId, nextMonthStart, nextMonthEnd)
                
                nudges = financialNudgeService.getActiveNudges(userId)
                
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
                        "Financial Guidance",
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
                                    spendingPatternService.refreshPatterns(userId)
                                    financialNudgeService.generateNudges(userId)
                                    
                                    // Reload data
                                    patterns = spendingPatternService.getActivePatterns(userId)
                                    trends = trendAnalysisService.analyzeTrends(userId)
                                    val nextMonthStart = java.time.LocalDate.now().withDayOfMonth(1).plusMonths(1)
                                    val nextMonthEnd = nextMonthStart.withDayOfMonth(nextMonthStart.lengthOfMonth())
                                    predictions = spendingPredictionService.predictFutureSpending(userId, nextMonthStart, nextMonthEnd)
                                    nudges = financialNudgeService.getActiveNudges(userId)
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Financial Nudges Section
                    if (nudges.isNotEmpty()) {
                        Text(
                            text = "Financial Nudges",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        nudges.forEach { nudge ->
                            NudgeCard(
                                nudge = nudge,
                                onMarkRead = {
                                    scope.launch {
                                        financialNudgeService.markAsRead(nudge.id)
                                        nudges = financialNudgeService.getActiveNudges(userId)
                                    }
                                },
                                onDismiss = {
                                    scope.launch {
                                        financialNudgeService.dismissNudge(nudge.id)
                                        nudges = financialNudgeService.getActiveNudges(userId)
                                    }
                                }
                            )
                        }
                    }
                    
                    // Spending Patterns Section
                    if (patterns.isNotEmpty()) {
                        Text(
                            text = "Spending Patterns",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        patterns.take(5).forEach { pattern ->
                            PatternCard(pattern = pattern)
                        }
                    }
                    
                    // Trends Section
                    trends?.let { trendResult ->
                        if (trendResult.trends.isNotEmpty()) {
                            Text(
                                text = "Spending Trends",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            trendResult.trends.take(5).forEach { trend ->
                                TrendCard(trend = trend)
                            }
                        }
                        
                        if (trendResult.spikes.isNotEmpty()) {
                            Text(
                                text = "Unusual Spikes",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            trendResult.spikes.take(3).forEach { spike ->
                                SpikeCard(spike = spike)
                            }
                        }
                    }
                    
                    // Predictions Section
                    if (predictions.isNotEmpty()) {
                        Text(
                            text = "Spending Predictions",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        predictions.take(5).forEach { prediction ->
                            PredictionCard(prediction = prediction)
                        }
                    }
                    
                    // Overspending Risks
                    val risks = predictions.filter { it.isOverspendingRisk }
                    if (risks.isNotEmpty()) {
                        Text(
                            text = "Overspending Risks",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        risks.forEach { risk ->
                            RiskCard(prediction = risk)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NudgeCard(
    nudge: FinancialNudge,
    onMarkRead: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (nudge.priority) {
                FinancialNudge.Priority.HIGH -> MaterialTheme.colorScheme.errorContainer
                FinancialNudge.Priority.MEDIUM -> MaterialTheme.colorScheme.tertiaryContainer
                FinancialNudge.Priority.LOW -> MaterialTheme.colorScheme.secondaryContainer
            }
        )
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
                    text = nudge.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (!nudge.isRead) {
                    Badge {
                        Text("New")
                    }
                }
            }
            Text(
                text = nudge.message,
                style = MaterialTheme.typography.bodyMedium
            )
            nudge.suggestion?.let { suggestion ->
                Text(
                    text = "💡 $suggestion",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onMarkRead) {
                    Text("Mark Read")
                }
                TextButton(onClick = onDismiss) {
                    Text("Dismiss")
                }
            }
        }
    }
}

@Composable
fun PatternCard(pattern: SpendingPattern) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                    text = pattern.category ?: "Unknown",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = CurrencyFormatter.format(pattern.averageAmount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = "Type: ${pattern.patternType.name} • Frequency: ${pattern.frequency ?: 0}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            pattern.confidenceScore?.let { confidence ->
                LinearProgressIndicator(
                    progress = confidence.toFloat(),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Confidence: ${(confidence * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun TrendCard(trend: Trend) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = trend.category,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Direction: ${trend.direction.name}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = CurrencyFormatter.format(trend.endAmount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = when (trend.direction) {
                        Trend.TrendDirection.INCREASING -> MaterialTheme.colorScheme.error
                        Trend.TrendDirection.DECREASING -> MaterialTheme.colorScheme.primary
                        Trend.TrendDirection.STABLE -> MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    text = "${(trend.strength * 100).toInt()}% strength",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun SpikeCard(spike: SpendingSpike) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = spike.category,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${spike.month.month.name} ${spike.month.year}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = CurrencyFormatter.format(spike.amount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "+${spike.percentageIncrease.toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun PredictionCard(prediction: SpendingPrediction) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                    text = prediction.category ?: "Unknown",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = CurrencyFormatter.format(prediction.predictedAmount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = "Method: ${prediction.predictionMethod} • Confidence: ${((prediction.confidenceScore ?: 0.0) * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun RiskCard(prediction: SpendingPrediction) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = prediction.category ?: "Unknown",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = "Risk: ${prediction.riskLevel?.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Text(
                text = CurrencyFormatter.format(prediction.predictedAmount),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

