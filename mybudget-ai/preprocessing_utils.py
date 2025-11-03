#!/usr/bin/env python3
"""
preprocessing_utils.py

Utility functions for preprocessing UPI transaction narrations:
- Clean UPI transaction descriptions
- Remove transaction IDs and bank tags
- Normalize text for better model performance
- Normalize stock market/clearing corporation references

Note: Categories are managed in categories.yml. Stock market transactions
are normalized during preprocessing and should be categorized via the model
using keywords defined in categories.yml (e.g., "Investments & Finance / Stocks & Bonds").
"""

import re
import os
import yaml
from typing import Optional, List

# Default noise words (fallback if YAML not available)
DEFAULT_NOISE_WORDS = [
    'YOU ARE PAYING FOR',
    'PAYMENT FOR',
    'TRANSACTION',
    'GENERATING DYNAMIC',
    'REF NO',
    'TXN',
    'TXNID'
]


def load_noise_words(config_file: str = "categories.yml") -> List[str]:
    """
    Load noise words from categories.yml preprocessing configuration.
    
    Args:
        config_file: Path to categories.yml file
        
    Returns:
        List of noise words to remove during preprocessing
    """
    noise_words = DEFAULT_NOISE_WORDS.copy()
    
    # Try to load from YAML file
    config_path = os.path.join(os.path.dirname(__file__), config_file)
    if os.path.exists(config_path):
        try:
            with open(config_path, 'r', encoding='utf-8') as f:
                config = yaml.safe_load(f)
                if config and 'preprocessing' in config:
                    preprocessing = config['preprocessing']
                    if 'noise_words' in preprocessing:
                        noise_words = preprocessing['noise_words']
        except Exception:
            # If YAML parsing fails, use defaults
            pass
    
    return noise_words


# Load noise words from config (can be overridden)
TRANSACTION_NOISE_WORDS = load_noise_words()

def is_likely_p2p(narration: str) -> bool:
    """
    Detect if narration likely indicates a P2P (person-to-person) transaction.
    P2P transactions contain user-added clues that should be preserved.
    
    Args:
        narration: Transaction narration text
        
    Returns:
        True if narration likely indicates P2P transaction
    """
    if not narration:
        return False
    
    narration_lower = narration.lower()
    
    # P2P indicators: user-added descriptive text
    p2p_indicators = [
        'friend', 'friends', 'with friend', 'with friends',
        'dinner', 'lunch', 'outing', 'hangout', 'social',
        'group expense', 'shared', 'reimbursed', 'reimburse',
        'lent', 'borrowed', 'loan to', 'given to', 'received from',
        'gift', 'birthday', 'wedding', 'anniversary', 'party',
        'split', 'shared expense', 'contribution',
        'to ', 'from ', 'sent to', 'paid to', 'received from'
    ]
    
    # Normalize text for pattern matching (replace dashes/slashes with spaces)
    narration_normalized = re.sub(r'[-/]+', ' ', narration)
    
    # Check if narration contains P2P indicators (handle both original and normalized)
    has_p2p_keywords = any(indicator in narration_lower for indicator in p2p_indicators) or \
                       any(indicator in narration_normalized.lower() for indicator in p2p_indicators)
    
    # Check for person names - handle both spaces and dashes
    # Pattern: "to [Name]", "from [Name]", "with [Name]", etc.
    # Also handle UPI format: "UPI-TO-JOHN" or "TO-JOHN"
    person_name_patterns = [
        r'\bto[-/ ]+[A-Z][a-z]{2,}\b',  # "to John", "to-John", "TO-JOHN"
        r'\bfrom[-/ ]+[A-Z][a-z]{2,}\b',  # "from Mike", "from-Mike"
        r'\bwith[-/ ]+[A-Z][a-z]{2,}\b',  # "with Emma", "with-Emma"
        r'\b[A-Z][a-z]{2,}[-/ ]+(?:and|&)[-/ ]+[A-Z][a-z]{2,}\b',  # "John and Sarah", "John-And-Sarah"
        r'\b[A-Z][a-z]{2,}[-/ ]+paid\b',  # "John paid", "John-paid"
        r'\bpaid[-/ ]+[A-Z][a-z]{2,}\b',  # "paid John", "paid-John"
        r'^[A-Z]{2,}-[A-Z][a-z]{2,}',  # "UPI-JOHN", "TO-SARAH" at start
    ]
    
    has_person_name = any(re.search(pattern, narration, re.IGNORECASE) for pattern in person_name_patterns) or \
                     any(re.search(pattern, narration_normalized, re.IGNORECASE) for pattern in person_name_patterns)
    
    return has_p2p_keywords or has_person_name

def preprocess_upi_narration(text: Optional[str], preserve_p2p_clues: bool = True) -> str:
    """
    Preprocess UPI transaction narration to remove IDs, digits, and bank tags.
    
    For P2P transactions (detected by user clues), preserves more of the original
    narration to keep descriptive text added by users.
    
    This function cleans transaction descriptions by:
    - Removing UPI prefixes and bank tags
    - Removing transaction IDs and reference numbers
    - Normalizing stock market/clearing corporation references
    - Cleaning separators and noise words
    - For P2P: Lighter cleaning to preserve user clues
    
    Args:
        text: Raw transaction narration string
        preserve_p2p_clues: If True, detect P2P and preserve user clues
        
    Returns:
        Cleaned narration string, or empty string if input is invalid
        
    Examples:
        >>> preprocess_upi_narration("UPI-STARBUCKS-123456@paytm")
        'STARBUCKS'
        >>> preprocess_upi_narration("Dinner with friends at restaurant")
        'Dinner with friends at restaurant'
        >>> preprocess_upi_narration("ACH D- INDIAN CLEARING CORP-000000RZVBRM")
        'ACH DEBIT STOCK MARKET CLEARING'
    """
    if not text or not isinstance(text, str):
        return ""
    
    text = text.strip()
    if not text:
        return ""
    
    # Detect P2P early - BEFORE removing UPI prefix, check for P2P indicators
    # UPI transactions might have clues like "UPI-TO-JOHN" or "UPI-FRIEND-PAYMENT"
    original_text = text
    is_p2p = preserve_p2p_clues and is_likely_p2p(text)
    
    # Step 1: Remove UPI prefix (always remove for consistency)
    # But preserve content after it for P2P detection
    text_after_upi = text
    if re.match(r'^UPI[-/]', text, flags=re.IGNORECASE):
        text_after_upi = re.sub(r'^UPI[-/]', '', text, flags=re.IGNORECASE)
        # Re-check P2P after removing UPI prefix (in case clues are after UPI-)
        if not is_p2p:
            is_p2p = preserve_p2p_clues and is_likely_p2p(text_after_upi)
    
    text = text_after_upi
    
    # Step 2: Remove bank tags and handles (e.g., @HDFCBANK-HDFC.COMERUPI, @GPay-ICIC0DC0099)
    # Bank tags typically end before transaction IDs or meaningful content
    # For P2P: Be more careful to preserve clues that might be after @ symbol
    if '@' in text:
        parts = text.split('@', 1)
        before_at = parts[0]
        after_at = parts[1]
        
        # For P2P, check if content after @ contains person names or P2P clues
        if is_p2p and is_likely_p2p(after_at):
            # Keep meaningful content after @ for P2P transactions
            # Extract bank tag but preserve clues
            bank_match = re.match(r'^([A-Z0-9]+(?:-[A-Z0-9]+)*)', after_at, re.IGNORECASE)
            if bank_match:
                bank_tag = bank_match.group(1)
                remaining = after_at[len(bank_tag):].strip()
                # If remaining has content (might be clues), keep it
                if remaining:
                    text = before_at + ' ' + remaining
                else:
                    text = before_at
            else:
                # No clear bank tag, but might have clues - check if it looks like a name/clue
                if re.search(r'[A-Z][a-z]{2,}', after_at):  # Has capitalized words (might be name)
                    text = before_at + ' ' + after_at
                else:
                    text = before_at
        else:
            # Standard processing for non-P2P
            bank_match = re.match(r'^([A-Z0-9]+(?:-[A-Z0-9]+)*)', after_at, re.IGNORECASE)
            if bank_match:
                bank_tag = bank_match.group(1)
                remaining = after_at[len(bank_tag):]  # Content after bank tag
                text = before_at + remaining  # Reconstruct without bank tag
            else:
                # No clear bank tag structure, remove @ and everything after (fallback)
                text = before_at
    
    # Step 3: Remove transaction IDs (long numbers, e.g., 500542064115, 112425210473)
    # Pattern: 9+ digit numbers that appear after dashes or spaces
    text = re.sub(r'[-/]\d{9,}', '', text)
    text = re.sub(r'\s+\d{9,}', '', text)
    
    # Step 4: Remove transaction numbers with prefixes (e.g., VYAPAR.171813425600)
    text = re.sub(r'[A-Z]+\.\d{12,}', '', text, flags=re.IGNORECASE)
    
    # Step 5: Remove PAYTM prefixes and QR codes
    text = re.sub(r'PAYTM\.[A-Z0-9]+', '', text, flags=re.IGNORECASE)
    # Remove Paytm QR code identifiers (e.g., PAYTMQR5KFKEC, PAYTMQR...)
    text = re.sub(r'[-/]PAYTMQR[A-Z0-9]+', '', text, flags=re.IGNORECASE)
    text = re.sub(r'\bPAYTMQR[A-Z0-9]+\b', '', text, flags=re.IGNORECASE)
    
    # Step 6: Remove long alphanumeric codes and clearing corporation reference codes
    # Remove: 8+ uppercase letters followed by 6+ digits (clearing codes)
    text = re.sub(r'[-/]([A-Z]{8,}[0-9]{6,})', '', text)
    # Remove: 15+ alphanumeric chars that contain digits (transaction IDs, not all-letter merchant names)
    text = re.sub(r'[-/]([A-Z]*[0-9][A-Z0-9]{14,})', '', text)
    # Remove alphanumeric codes like 000000RZVBRM (starts with zeros) - but preserve clearing corp names
    # Only remove codes AFTER clearing corp names
    text = re.sub(r'[-/]\d{6,}[A-Z0-9]{4,}', '', text)
    
    # Step 7: Normalize stock market/clearing corporation references
    # Normalize "ACH D" (ACH Debit) to "ACH DEBIT" for better recognition
    text = re.sub(r'\bACH\s+D\b', 'ACH DEBIT', text, flags=re.IGNORECASE)
    # DO NOT normalize clearing corporation names - keep them for keyword matching
    # Original names like "INDIAN CLEARING CORP" should be preserved so keyword matching works
    # The regex below is commented out to preserve original clearing corp names
    # text = re.sub(
    #     r'\b(INDIAN|NSE|BSE)\s+CLEARING\s+CORP(?:ORATION)?\b',
    #     'STOCK MARKET CLEARING',
    #     text,
    #     flags=re.IGNORECASE
    # )
    
    # Step 7.5: Normalize bank transfer and payment terms
    # Normalize CHQ/cheque payment terms
    text = re.sub(r'\bCHQ\s+PAID\b', 'CHEQUE PAYMENT', text, flags=re.IGNORECASE)
    text = re.sub(r'\bCHEQUE\s+PAID\b', 'CHEQUE PAYMENT', text, flags=re.IGNORECASE)
    # Normalize transfer terms
    text = re.sub(r'\bTRANSFER\s+IN\b', 'BANK TRANSFER', text, flags=re.IGNORECASE)
    text = re.sub(r'\bTRANSFER\s+OUT\b', 'BANK TRANSFER', text, flags=re.IGNORECASE)
    # Remove generic bank name suffixes that don't add meaning (LTD, LTD., etc.)
    text = re.sub(r'\b(BANK\s+)?LTD\.?\b', '', text, flags=re.IGNORECASE)
    
    # Step 7.6: Normalize common spelling variations
    # Groceries variations: grocies, groc, grocerie -> grocery
    # Match "groc" followed by word boundary (space, dash, end of string, or non-letter)
    text = re.sub(r'\bgrocies\b', 'grocery', text, flags=re.IGNORECASE)
    text = re.sub(r'\bgroc(?=\s|[-/]|$)', 'grocery', text, flags=re.IGNORECASE)
    text = re.sub(r'\bgrocerie\b', 'grocery', text, flags=re.IGNORECASE)
    text = re.sub(r'\bgrocerys\b', 'grocery', text, flags=re.IGNORECASE)
    # Food variations: foods -> food (singularize for consistency)
    text = re.sub(r'\bfoods\b', 'food', text, flags=re.IGNORECASE)
    
    # Step 8: Normalize separators (replace multiple dashes/slashes with single space)
    text = re.sub(r'[-/]+', ' ', text)
    
    # Step 9: Remove standalone transaction keywords that add no semantic value
    # For P2P, skip aggressive noise word removal to preserve user clues
    if not is_p2p:
        for noise_word in TRANSACTION_NOISE_WORDS:
            text = re.sub(r'\b' + re.escape(noise_word) + r'\b', '', text, flags=re.IGNORECASE)
    else:
        # For P2P, only remove obvious technical noise, keep descriptive words
        critical_noise = ['TXN', 'TXNID', 'REF NO', 'GENERATING DYNAMIC']
        for noise_word in critical_noise:
            text = re.sub(r'\b' + re.escape(noise_word) + r'\b', '', text, flags=re.IGNORECASE)
    
    # Step 10: Clean up extra spaces
    text = re.sub(r'\s+', ' ', text)
    
    # Step 11: Remove leading/trailing spaces and separators
    text = text.strip(' -/')
    
    return text if text else ""


# Test cases for development/debugging
if __name__ == "__main__":
    test_cases = [
        "UPI-CHILD CARE PHARMACY-VYAPAR.171813425600@HDFCBANK-HDFC.COMERUPI-112425210473-MEDICAL",
        "UPI-ZOMATO-ZOMATO@RAPL-RATN000RAPL-500542064115-FOOD",
        "UPI-CAFE COFFEE DAY-CAFECOFFEEDAY@GPay-ICIC0DC0099-500550670663-FOOD",
        "UPI-SWIGGY-2VU2NQU1@YBL-YESB0YBLUPI-500306083723-KTXNDOW205595278",
        "ACH D- INDIAN CLEARING CORP-000000RZVBRM",  # Stock market transaction
    ]
    
    print("=" * 70)
    print("Preprocessing Test Cases")
    print("=" * 70)
    print()
    
    for i, test in enumerate(test_cases, 1):
        cleaned = preprocess_upi_narration(test)
        
        print(f"Test {i}:")
        print(f"  Original: {test}")
        print(f"  Cleaned:  {cleaned}")
        print()
