package com.budgetbuddy.mobile.util

import java.text.NumberFormat
import java.util.Locale

object CurrencyFormatter {
    // Use INR (Indian Rupee) currency
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    
    init {
        // Ensure it uses ₹ symbol
        currencyFormat.maximumFractionDigits = 2
        currencyFormat.minimumFractionDigits = 2
    }
    
    fun format(amount: Double): String {
        return currencyFormat.format(amount)
    }
    
    fun format(amount: Double?): String {
        return if (amount != null) {
            format(amount)
        } else {
            format(0.0)
        }
    }
    
    fun formatAbsolute(amount: Double): String {
        return format(kotlin.math.abs(amount))
    }
}

