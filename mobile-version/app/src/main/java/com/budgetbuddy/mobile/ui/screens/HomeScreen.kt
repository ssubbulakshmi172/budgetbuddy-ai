package com.budgetbuddy.mobile.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.budgetbuddy.mobile.ui.navigation.Screen

@Composable
fun HomeScreen(navController: NavController) {
    var animateTitle by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        android.util.Log.d("HomeScreen", "🏠 HomeScreen composed and launched")
        animateTitle = true
        android.util.Log.d("HomeScreen", "✅ Animation state set to true")
    }
    
    // Gradient background colors - light blue gradient
    val lightBlue = Color(0xFFE3F2FD) // Light blue
    val mediumBlue = Color(0xFFBBDEFB) // Medium blue
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(lightBlue, mediumBlue),
                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = androidx.compose.ui.geometry.Offset(1000f, 1000f)
                )
            )
    ) {
        // Debug: Show a simple text to verify rendering
        Text(
            text = "BudgetBuddy Loading...",
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
        )
        
        // White card container - takes available space and allows scrolling
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(24.dp),
                    spotColor = Color.Black.copy(alpha = 0.1f)
                ),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            )
        ) {
            val scrollState = rememberScrollState()
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(
                        state = scrollState,
                        enabled = true
                    )
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Header Section - App Logo/Title
                AnimatedVisibility(
                    visible = animateTitle,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { -40 })
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Orange wallet icon
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = Color(0xFFFF6B35), // Orange accent
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "BudgetBuddy",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF212121)
                            )
                            Text(
                                text = "Your Personal Finance Assistant",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF757575),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
                
                // Privacy Card
                AnimatedVisibility(
                    visible = animateTitle,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { 20 }) + scaleIn()
                ) {
                    PrivacyCard() // Matches Spring Boot privacy notice
                }
                
                // Quick Actions Grid
                AnimatedVisibility(
                    visible = animateTitle,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { 20 })
                ) {
                    QuickActionsGrid(navController = navController)
                }
                
                // Get Started Button with Gradient
                AnimatedVisibility(
                    visible = animateTitle,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { 20 })
                ) {
                    GetStartedButton(
                        onClick = { navController.navigate(Screen.Dashboard.route) }
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun PrivacyCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = Color.Black.copy(alpha = 0.1f)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFE8F4FD) // Very light blue background
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Shield/lock icon matching Spring Boot (bi-shield-lock-fill)
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = Color(0xFF1CC88A), // Success green matching Spring Boot
                modifier = Modifier.size(28.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "100% Local & Private",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1CC88A) // Success green matching Spring Boot
                )
                Text(
                    text = "Your data never leaves your device",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF757575),
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
fun QuickActionsGrid(navController: NavController) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Quick Actions",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF212121),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        // Top row - matching Spring Boot Quick Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickActionCard(
                title = "New Transaction",
                subtitle = "Create manually",
                icon = Icons.Default.AddCircle,
                onClick = {
                    android.util.Log.d("HomeScreen", "🔵 QuickAction: New Transaction clicked")
                    navController.navigate(Screen.AddTransaction.route) {
                        popUpTo(Screen.Home.route) { inclusive = false }
                    }
                },
                modifier = Modifier.weight(1f)
            )
            QuickActionCard(
                title = "Upload Transaction",
                subtitle = "Upload Excel/CSV",
                icon = Icons.Default.CloudUpload,
                onClick = {
                    android.util.Log.d("HomeScreen", "🔵 QuickAction: Upload Transaction clicked")
                    navController.navigate(Screen.Upload.route) {
                        popUpTo(Screen.Home.route) { inclusive = false }
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }
        
        // Bottom row - matching Spring Boot Quick Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickActionCard(
                title = "Dashboard",
                subtitle = "View analytics",
                icon = Icons.Default.Dashboard,
                onClick = { 
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Home.route) { inclusive = false }
                    }
                },
                modifier = Modifier.weight(1f)
            )
            QuickActionCard(
                title = "Categories",
                subtitle = "Manage categories",
                icon = Icons.Default.Tag, // Matches Spring Boot bi-tags icon
                onClick = { 
                    navController.navigate(Screen.Categories.route) {
                        popUpTo(Screen.Home.route) { inclusive = false }
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }
        
        // Additional row - Other Actions (matching Spring Boot)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickActionCard(
                title = "View Transactions",
                subtitle = "Browse and manage",
                icon = Icons.Default.AccountBalanceWallet,
                onClick = { 
                    navController.navigate(Screen.Transactions.route) {
                        popUpTo(Screen.Home.route) { inclusive = false }
                    }
                },
                modifier = Modifier.weight(1f)
            )
            QuickActionCard(
                title = "Financial Guidance",
                subtitle = "Patterns & insights",
                icon = Icons.Default.Insights,
                onClick = { 
                    navController.navigate(Screen.FinancialGuidance.route) {
                        popUpTo(Screen.Home.route) { inclusive = false }
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun QuickActionCard(
    title: String,
    subtitle: String = "",
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .height(if (subtitle.isNotEmpty()) 120.dp else 100.dp)
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(12.dp),
                spotColor = Color.Black.copy(alpha = 0.1f)
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Icon with gradient background matching Spring Boot style
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF667EEA),
                                Color(0xFF764BA2)
                            )
                        ),
                        shape = androidx.compose.foundation.shape.CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF212121),
                textAlign = TextAlign.Center
            )
            if (subtitle.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF757575),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun GetStartedButton(onClick: () -> Unit) {
    // Gradient colors - blue to purple-blue
    val startBlue = Color(0xFF2196F3)
    val endBlue = Color(0xFF7B1FA2)
    
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = Color.Black.copy(alpha = 0.2f)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(startBlue, endBlue)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Get Started",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}
