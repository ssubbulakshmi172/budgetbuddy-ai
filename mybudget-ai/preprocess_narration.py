#!/usr/bin/env python3
"""
Simple script to preprocess narration using Python preprocessing_utils.
Called from Java to ensure consistent preprocessing.

Usage:
    python3 preprocess_narration.py "UPI-STARBUCKS-123@paytm"
    
Output: Preprocessed narration to stdout
"""

import sys
import os

# Add script directory to path
script_dir = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, script_dir)

try:
    from preprocessing_utils import preprocess_upi_narration
except ImportError:
    # Fallback: simple cleaning if preprocessing_utils not available
    import re
    def preprocess_upi_narration(text):
        if not text:
            return ""
        text = text.strip()
        text = re.sub(r'(?i)^UPI[-/]', '', text)
        text = re.sub(r'(?i)@[A-Z0-9]+', '', text)
        text = re.sub(r'[-/]\d{9,}', '', text)
        text = re.sub(r'\s+\d{9,}', '', text)
        text = re.sub(r'[-/]+', ' ', text)
        text = re.sub(r'\s+', ' ', text)
        return text.strip()

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("", end="")
        sys.exit(0)
    
    narration = sys.argv[1]
    if not narration or not narration.strip():
        print("", end="")
        sys.exit(0)
    
    # Preprocess using Python preprocessing
    preprocessed = preprocess_upi_narration(narration, preserve_p2p_clues=True)
    
    # Output preprocessed narration
    print(preprocessed, end="")

