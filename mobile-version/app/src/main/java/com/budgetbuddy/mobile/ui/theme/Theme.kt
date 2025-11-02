package com.budgetbuddy.mobile.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// BudgetBuddy Brand Colors (exact match to Spring CSS)
private val PrimaryBlue = androidx.compose.ui.graphics.Color(0xFF4361EE)      // --bb-primary #4361ee
private val PrimaryDark = androidx.compose.ui.graphics.Color(0xFF3A56D4)     // --bb-primary-dark #3a56d4
private val PrimaryLight = androidx.compose.ui.graphics.Color(0xFFEEF2FF)     // --bb-primary-light #eef2ff
private val SecondaryPurple = androidx.compose.ui.graphics.Color(0xFF7209B7)  // --bb-secondary #7209b7
private val AccentCyan = androidx.compose.ui.graphics.Color(0xFF4CC9F0)       // --bb-accent #4cc9f0
private val SuccessGreen = androidx.compose.ui.graphics.Color(0xFF1CC88A)     // --bb-success #1cc88a
private val InfoBlue = androidx.compose.ui.graphics.Color(0xFF36B9CC)         // --bb-info #36b9cc
private val WarningYellow = androidx.compose.ui.graphics.Color(0xFFF6C23E)     // --bb-warning #f6c23e
private val ErrorRed = androidx.compose.ui.graphics.Color(0xFFE74A3B)        // --bb-danger #e74a3b

// Grayscale (exact match to Spring)
private val Gray50 = androidx.compose.ui.graphics.Color(0xFFF8F9FC)   // --bb-gray-50
private val Gray100 = androidx.compose.ui.graphics.Color(0xFFF1F3F9)  // --bb-gray-100
private val Gray200 = androidx.compose.ui.graphics.Color(0xFFE9ECEF)  // --bb-gray-200
private val Gray300 = androidx.compose.ui.graphics.Color(0xFFDEE2E6)  // --bb-gray-300
private val Gray600 = androidx.compose.ui.graphics.Color(0xFF6C757D)  // --bb-gray-600
private val Gray700 = androidx.compose.ui.graphics.Color(0xFF495057)  // --bb-gray-700
private val Gray800 = androidx.compose.ui.graphics.Color(0xFF343A40)  // --bb-gray-800
private val Gray900 = androidx.compose.ui.graphics.Color(0xFF212529)  // --bb-gray-900

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryBlue,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = PrimaryLight.copy(alpha = 0.2f),
    onPrimaryContainer = PrimaryBlue,
    
    secondary = SecondaryPurple,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    secondaryContainer = PrimaryLight.copy(alpha = 0.15f),
    onSecondaryContainer = SecondaryPurple,
    
    tertiary = AccentCyan,
    onTertiary = Gray900,
    tertiaryContainer = AccentCyan.copy(alpha = 0.2f),
    onTertiaryContainer = AccentCyan,
    
    error = ErrorRed,
    onError = androidx.compose.ui.graphics.Color.White,
    errorContainer = ErrorRed.copy(alpha = 0.2f),
    onErrorContainer = ErrorRed,
    
    background = Gray900,
    onBackground = Gray100,
    surface = Gray800,
    onSurface = Gray100,
    surfaceVariant = Gray700,
    onSurfaceVariant = Gray200
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = PrimaryLight,
    onPrimaryContainer = PrimaryDark,
    
    secondary = SecondaryPurple,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    secondaryContainer = PrimaryLight,
    onSecondaryContainer = SecondaryPurple,
    
    tertiary = AccentCyan,
    onTertiary = Gray900,
    tertiaryContainer = AccentCyan.copy(alpha = 0.15f),
    onTertiaryContainer = AccentCyan,
    
    error = ErrorRed,
    onError = androidx.compose.ui.graphics.Color.White,
    errorContainer = ErrorRed.copy(alpha = 0.1f),
    onErrorContainer = ErrorRed,
    
    background = androidx.compose.ui.graphics.Color.White,
    onBackground = Gray900,
    surface = Gray50,
    onSurface = Gray900,
    surfaceVariant = Gray100,
    onSurfaceVariant = Gray700
)

@Composable
fun BudgetBuddyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = com.budgetbuddy.mobile.ui.theme.Typography,
        content = content
    )
}
