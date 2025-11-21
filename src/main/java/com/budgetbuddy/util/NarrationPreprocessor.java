package com.budgetbuddy.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for preprocessing transaction narration text.
 * Removes UPI IDs, reference numbers, bank tags, and other noise to extract meaningful merchant/transaction information.
 * Similar to Python's preprocess_upi_narration function.
 */
public class NarrationPreprocessor {

    private static final Logger logger = LoggerFactory.getLogger(NarrationPreprocessor.class);

    /**
     * Clean narration text by removing UPI IDs, reference numbers, and other noise.
     * Similar to Python's preprocess_upi_narration function.
     * 
     * @param narration Raw transaction narration text
     * @return Cleaned narration text
     */
    public static String cleanNarration(String narration) {
        if (narration == null || narration.trim().isEmpty()) {
            return "";
        }
        
        String cleaned = narration.trim();
        
        // Remove UPI prefix
        cleaned = cleaned.replaceAll("(?i)^UPI[-/]", "");
        
        // Remove @bank references (e.g., @YBL, @HDFCBANK, @paytm)
        cleaned = cleaned.replaceAll("(?i)@[A-Z0-9]+", "");
        
        // Remove transaction IDs (long numbers, e.g., 500111811826, 500106336866)
        cleaned = cleaned.replaceAll("[-/]\\d{9,}", "");
        cleaned = cleaned.replaceAll("\\s+\\d{9,}", "");
        
        // Remove transaction numbers with prefixes (e.g., VYAPAR.171813425600)
        cleaned = cleaned.replaceAll("(?i)[A-Z]+\\.\\d{12,}", "");
        
        // Remove PAYTM prefixes and QR codes
        cleaned = cleaned.replaceAll("(?i)PAYTM\\.[A-Z0-9]+", "");
        cleaned = cleaned.replaceAll("(?i)[-/]PAYTMQR[A-Z0-9]+", "");
        cleaned = cleaned.replaceAll("(?i)\\bPAYTMQR[A-Z0-9]+\\b", "");
        
        // Remove long alphanumeric codes (e.g., KPNFARMFRESHOFFLINE, ZUDIOAUNITOFTRENTLIM)
        // But preserve meaningful merchant names
        cleaned = cleaned.replaceAll("[-/]([A-Z]{8,}[0-9]{6,})", "");
        cleaned = cleaned.replaceAll("[-/]([A-Z]*[0-9][A-Z0-9]{14,})", "");
        cleaned = cleaned.replaceAll("[-/]\\d{6,}[A-Z0-9]{4,}", "");
        
        // Remove common noise words
        cleaned = cleaned.replaceAll("(?i)\\bPAYMENT FOR\\b", "");
        cleaned = cleaned.replaceAll("(?i)\\bPAYMENT\\b", "");
        cleaned = cleaned.replaceAll("(?i)\\bTXN\\b", "");
        cleaned = cleaned.replaceAll("(?i)\\bREF\\b", "");
        cleaned = cleaned.replaceAll("(?i)\\bNO\\b", "");
        cleaned = cleaned.replaceAll("(?i)\\bID\\b", "");
        
        // Clean up multiple dashes and spaces
        cleaned = cleaned.replaceAll("[-/]+", " ");
        cleaned = cleaned.replaceAll("\\s+", " ");
        cleaned = cleaned.trim();
        
        return cleaned;
    }

    /**
     * Extract merchant pattern from narration (similar to extractMerchantPattern in MoneyLeakService).
     * Returns first 2-3 significant words from cleaned narration.
     * 
     * @param narration Raw transaction narration text
     * @return Merchant pattern (e.g., "STARBUCKS", "FARM FRESH") or "UNKNOWN" if empty
     */
    public static String extractMerchantPattern(String narration) {
        if (narration == null || narration.trim().isEmpty()) {
            return "UNKNOWN";
        }
        
        // Clean the narration first
        String cleaned = cleanNarration(narration).toUpperCase();
        
        if (cleaned.isEmpty()) {
            return "UNKNOWN";
        }
        
        // Remove all non-alphabetic characters except spaces
        cleaned = cleaned.replaceAll("[^A-Z\\s]", " ");
        cleaned = cleaned.trim();
        
        // Take first 2-3 significant words
        String[] words = cleaned.split("\\s+");
        if (words.length == 0) {
            return "UNKNOWN";
        }
        
        StringBuilder pattern = new StringBuilder(words[0]);
        if (words.length > 1) {
            pattern.append(" ").append(words[1]);
        }
        if (words.length > 2 && words[2].length() > 2) {
            pattern.append(" ").append(words[2]);
        }
        
        String result = pattern.toString().trim();
        return result.isEmpty() ? "UNKNOWN" : result;
    }

    /**
     * Normalize narration for keyword matching (lowercase, trimmed, limited length).
     * 
     * @param narration Narration text
     * @param maxLength Maximum length (default 255)
     * @return Normalized narration
     */
    public static String normalizeForKeyword(String narration, int maxLength) {
        if (narration == null || narration.trim().isEmpty()) {
            return "";
        }
        
        String normalized = cleanNarration(narration).toLowerCase().trim();
        
        if (normalized.length() > maxLength) {
            normalized = normalized.substring(0, maxLength);
        }
        
        return normalized;
    }

    /**
     * Normalize narration for keyword matching with default max length of 255.
     * 
     * @param narration Narration text
     * @return Normalized narration
     */
    public static String normalizeForKeyword(String narration) {
        return normalizeForKeyword(narration, 255);
    }
}

