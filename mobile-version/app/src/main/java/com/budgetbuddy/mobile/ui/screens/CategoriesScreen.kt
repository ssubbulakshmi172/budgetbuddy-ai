package com.budgetbuddy.mobile.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.budgetbuddy.mobile.data.model.CategoryKeyword
import com.budgetbuddy.mobile.BudgetBuddyApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun CategoriesScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val app = context.applicationContext as BudgetBuddyApplication
    val categoryKeywordDao = app.database.categoryKeywordDao()
    
    var categories by remember { mutableStateOf<List<CategoryKeyword>>(emptyList()) }
    var selectedView by remember { mutableStateOf("all") } // "all", "taxonomy", "manual", "predicted"
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(selectedView) {
        scope.launch {
            isLoading = true
            categories = when (selectedView) {
                "taxonomy" -> categoryKeywordDao.getTaxonomyCategories()
                "manual" -> categoryKeywordDao.getManualCategories()
                "predicted" -> {
                    // Get unique predicted categories from transactions
                    val allTransactions = app.database.transactionDao()
                        .getTransactionsByUser(1L)
                        .first()
                    
                    allTransactions
                        .mapNotNull { it.predictedCategory?.takeIf { cat -> cat.isNotEmpty() } }
                        .distinct()
                        .map { categoryName ->
                            CategoryKeyword(
                                id = 0,
                                keyword = categoryName,
                                categoryName = categoryName,
                                categoriesFor = "Predicted"
                            )
                        }
                }
                else -> categoryKeywordDao.getAllCategories()
            }
            isLoading = false
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "Categories",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        
        // View Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedView == "all",
                onClick = { selectedView = "all" },
                label = { Text("All") },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = selectedView == "taxonomy",
                onClick = { selectedView = "taxonomy" },
                label = { Text("Taxonomy") },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = selectedView == "manual",
                onClick = { selectedView = "manual" },
                label = { Text("Manual") },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = selectedView == "predicted",
                onClick = { selectedView = "predicted" },
                label = { Text("AI Predicted") },
                modifier = Modifier.weight(1f)
            )
        }
        
        // Categories List
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (categories.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tag,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                    Text(
                        text = "No categories found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { category ->
                    CategoryCard(category = category)
                }
            }
        }
    }
}

@Composable
fun CategoryCard(category: CategoryKeyword) {
    val categoryTypeColor = when (category.categoriesFor) {
        "Taxonomy" -> MaterialTheme.colorScheme.primary
        "Manual" -> MaterialTheme.colorScheme.secondary
        "Predicted" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tag,
                        contentDescription = null,
                        tint = categoryTypeColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = category.categoryName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Keyword: ${category.keyword}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            AssistChip(
                onClick = { },
                label = {
                    Text(
                        text = category.categoriesFor ?: "Manual",
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = categoryTypeColor.copy(alpha = 0.2f),
                    labelColor = categoryTypeColor
                )
            )
        }
    }
}

