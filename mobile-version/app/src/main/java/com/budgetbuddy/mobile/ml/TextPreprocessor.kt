package com.budgetbuddy.mobile.ml

import java.util.regex.Pattern

/**
 * Text preprocessing utility for UPI transaction narrations
 * 
 * Ported from Python preprocess_upi_narration() function.
 * Cleans transaction descriptions by:
 * - Removing UPI prefixes and bank tags
 * - Removing transaction IDs and reference numbers
 * - Normalizing stock market/clearing corporation references
 * - Cleaning separators and noise words
 * - For P2P: Lighter cleaning to preserve user clues
 */
object TextPreprocessor {
    
    // Default noise words (fallback if config not available)
    private val DEFAULT_NOISE_WORDS = listOf(
        "YOU ARE PAYING FOR",
        "PAYMENT FOR",
        "TRANSACTION",
        "GENERATING DYNAMIC",
        "REF NO",
        "TXN",
        "TXNID"
    )
    
    private val TRANSACTION_NOISE_WORDS = DEFAULT_NOISE_WORDS
    
    /**
     * Detect if narration likely indicates a P2P (person-to-person) transaction.
     * P2P transactions contain user-added clues that should be preserved.
     */
    private fun isLikelyP2P(narration: String): Boolean {
        if (narration.isBlank()) return false
        
        val narrationLower = narration.lowercase()
        
        // P2P indicators: user-added descriptive text
        val p2pIndicators = listOf(
            "friend", "friends", "with friend", "with friends",
            "dinner", "lunch", "outing", "hangout", "social",
            "group expense", "shared", "reimbursed", "reimburse",
            "lent", "borrowed", "loan to", "given to", "received from",
            "gift", "birthday", "wedding", "anniversary", "party",
            "split", "shared expense", "contribution",
            "to ", "from ", "sent to", "paid to", "received from"
        )
        
        // Normalize text for pattern matching (replace dashes/slashes with spaces)
        val narrationNormalized = narration.replace(Regex("[-/]+"), " ")
        
        // Check if narration contains P2P indicators
        val hasP2PKeywords = p2pIndicators.any { indicator ->
            narrationLower.contains(indicator) || narrationNormalized.lowercase().contains(indicator)
        }
        
        // Check for person names - handle both spaces and dashes
        val personNamePatterns = listOf(
            Regex("\\bto[-/ ]+[A-Z][a-z]{2,}\\b", RegexOption.IGNORE_CASE),  // "to John", "to-John", "TO-JOHN"
            Regex("\\bfrom[-/ ]+[A-Z][a-z]{2,}\\b", RegexOption.IGNORE_CASE),  // "from Mike", "from-Mike"
            Regex("\\bwith[-/ ]+[A-Z][a-z]{2,}\\b", RegexOption.IGNORE_CASE),  // "with Emma", "with-Emma"
            Regex("\\b[A-Z][a-z]{2,}[-/ ]+(?:and|&)[-/ ]+[A-Z][a-z]{2,}\\b", RegexOption.IGNORE_CASE),  // "John and Sarah"
            Regex("\\b[A-Z][a-z]{2,}[-/ ]+paid\\b", RegexOption.IGNORE_CASE),  // "John paid"
            Regex("\\bpaid[-/ ]+[A-Z][a-z]{2,}\\b", RegexOption.IGNORE_CASE),  // "paid John"
            Regex("^[A-Z]{2,}-[A-Z][a-z]{2,}", RegexOption.IGNORE_CASE)  // "UPI-JOHN", "TO-SARAH" at start
        )
        
        val hasPersonName = personNamePatterns.any { pattern ->
            pattern.containsMatchIn(narration) || pattern.containsMatchIn(narrationNormalized)
        }
        
        return hasP2PKeywords || hasPersonName
    }
    
    /**
     * Preprocess UPI transaction narration to remove IDs, digits, and bank tags.
     * 
     * For P2P transactions (detected by user clues), preserves more of the original
     * narration to keep descriptive text added by users.
     */
    fun preprocessUpiNarration(text: String?, preserveP2PClues: Boolean = true): String {
        if (text == null || text.isBlank()) {
            return ""
        }
        
        var processed = text.trim()
        if (processed.isEmpty()) {
            return ""
        }
        
        // Detect P2P early - BEFORE removing UPI prefix
        val isP2P = preserveP2PClues && isLikelyP2P(processed)
        
        // Step 1: Remove UPI prefix
        if (processed.matches(Regex("^UPI[-/].*", RegexOption.IGNORE_CASE))) {
            processed = processed.replace(Regex("^UPI[-/]", RegexOption.IGNORE_CASE), "")
            // Re-check P2P after removing UPI prefix
            if (!isP2P && preserveP2PClues) {
                val recheckedP2P = isLikelyP2P(processed)
                if (recheckedP2P) {
                    // Update isP2P flag (we'll use a local variable)
                    // For now, continue with the check
                }
            }
        }
        
        // Step 2: Remove bank tags and handles
        if (processed.contains("@")) {
            val parts = processed.split("@", limit = 2)
            val beforeAt = parts[0]
            val afterAt = if (parts.size > 1) parts[1] else ""
            
            if (isP2P && isLikelyP2P(afterAt)) {
                // For P2P, preserve clues after @
                val bankMatch = Regex("^([A-Z0-9]+(?:-[A-Z0-9]+)*)", RegexOption.IGNORE_CASE).find(afterAt)
                if (bankMatch != null) {
                    val bankTag = bankMatch.value
                    val remaining = afterAt.substring(bankTag.length).trim()
                    processed = if (remaining.isNotEmpty()) {
                        "$beforeAt $remaining"
                    } else {
                        beforeAt
                    }
                } else {
                    // Check if it looks like a name/clue
                    if (Regex("[A-Z][a-z]{2,}").containsMatchIn(afterAt)) {
                        processed = "$beforeAt $afterAt"
                    } else {
                        processed = beforeAt
                    }
                }
            } else {
                // Standard processing for non-P2P
                val bankMatch = Regex("^([A-Z0-9]+(?:-[A-Z0-9]+)*)", RegexOption.IGNORE_CASE).find(afterAt)
                if (bankMatch != null) {
                    val bankTag = bankMatch.value
                    val remaining = afterAt.substring(bankTag.length)
                    processed = beforeAt + remaining
                } else {
                    processed = beforeAt
                }
            }
        }
        
        // Step 3: Remove transaction IDs (long numbers)
        processed = processed.replace(Regex("[-/]\\d{9,}"), "")
        processed = processed.replace(Regex("\\s+\\d{9,}"), "")
        
        // Step 4: Remove transaction numbers with prefixes
        processed = processed.replace(Regex("[A-Z]+\\.\\d{12,}", RegexOption.IGNORE_CASE), "")
        
        // Step 5: Remove PAYTM prefixes and QR codes
        processed = processed.replace(Regex("PAYTM\\.[A-Z0-9]+", RegexOption.IGNORE_CASE), "")
        processed = processed.replace(Regex("[-/]PAYTMQR[A-Z0-9]+", RegexOption.IGNORE_CASE), "")
        processed = processed.replace(Regex("\\bPAYTMQR[A-Z0-9]+\\b", RegexOption.IGNORE_CASE), "")
        
        // Step 6: Remove long alphanumeric codes
        processed = processed.replace(Regex("[-/]([A-Z]{8,}[0-9]{6,})"), "")
        processed = processed.replace(Regex("[-/]([A-Z]*[0-9][A-Z0-9]{14,})"), "")
        processed = processed.replace(Regex("[-/]\\d{6,}[A-Z0-9]{4,}"), "")
        
        // Step 7: Normalize stock market/clearing corporation references
        processed = processed.replace(Regex("\\bACH\\s+D\\b", RegexOption.IGNORE_CASE), "ACH DEBIT")
        
        // Step 7.5: Normalize bank transfer and payment terms
        processed = processed.replace(Regex("\\bCHQ\\s+PAID\\b", RegexOption.IGNORE_CASE), "CHEQUE PAYMENT")
        processed = processed.replace(Regex("\\bCHEQUE\\s+PAID\\b", RegexOption.IGNORE_CASE), "CHEQUE PAYMENT")
        processed = processed.replace(Regex("\\bTRANSFER\\s+IN\\b", RegexOption.IGNORE_CASE), "BANK TRANSFER")
        processed = processed.replace(Regex("\\bTRANSFER\\s+OUT\\b", RegexOption.IGNORE_CASE), "BANK TRANSFER")
        processed = processed.replace(Regex("\\b(BANK\\s+)?LTD\\.?\\b", RegexOption.IGNORE_CASE), "")
        
        // Step 7.6: Normalize common spelling variations
        processed = processed.replace(Regex("\\bgrocies\\b", RegexOption.IGNORE_CASE), "grocery")
        processed = processed.replace(Regex("\\bgroc(?=\\s|[-/]|$)", RegexOption.IGNORE_CASE), "grocery")
        processed = processed.replace(Regex("\\bgrocerie\\b", RegexOption.IGNORE_CASE), "grocery")
        processed = processed.replace(Regex("\\bgrocerys\\b", RegexOption.IGNORE_CASE), "grocery")
        processed = processed.replace(Regex("\\bfoods\\b", RegexOption.IGNORE_CASE), "food")
        
        // Step 8: Normalize separators
        processed = processed.replace(Regex("[-/]+"), " ")
        
        // Step 9: Remove noise words
        if (!isP2P) {
            for (noiseWord in TRANSACTION_NOISE_WORDS) {
                processed = processed.replace(Regex("\\b${Pattern.quote(noiseWord)}\\b", RegexOption.IGNORE_CASE), "")
            }
        } else {
            // For P2P, only remove critical noise
            val criticalNoise = listOf("TXN", "TXNID", "REF NO", "GENERATING DYNAMIC")
            for (noiseWord in criticalNoise) {
                processed = processed.replace(Regex("\\b${Pattern.quote(noiseWord)}\\b", RegexOption.IGNORE_CASE), "")
            }
        }
        
        // Step 10: Clean up extra spaces
        processed = processed.replace(Regex("\\s+"), " ")
        
        // Step 11: Remove leading/trailing spaces and separators
        processed = processed.trim(' ', '-', '/')
        
        return processed.ifEmpty { "" }
    }
}

