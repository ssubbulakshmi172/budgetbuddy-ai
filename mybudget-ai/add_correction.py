#!/usr/bin/env python3
"""
Add a user correction to user_corrections.json

This script appends a user correction to the JSON file for later use in model retraining.
The file format is a list of correction objects, each containing:
- narration: Transaction narration text
- category: User-corrected category
- userId: User ID (optional, for Efficient Mode cloud sync)
- transactionId: Transaction ID (optional, for Efficient Mode cloud sync)
- timestamp: When the correction was added

Usage:
    python3 add_correction.py <narration> <category> [userId] [transactionId]
"""

import sys
import json
import os
from datetime import datetime
from pathlib import Path

# File path - always use mybudget-ai/user_corrections.json
# Get the directory where this script is located
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
CORRECTIONS_FILE = os.path.join(SCRIPT_DIR, "user_corrections.json")

def load_corrections():
    """Load existing corrections from JSON file"""
    if not os.path.exists(CORRECTIONS_FILE):
        return []
    
    try:
        with open(CORRECTIONS_FILE, 'r', encoding='utf-8') as f:
            content = f.read().strip()
            if not content:
                return []
            return json.loads(content)
    except json.JSONDecodeError as e:
        print(f"⚠️ Warning: Invalid JSON in {CORRECTIONS_FILE}. Starting fresh.", file=sys.stderr)
        return []
    except Exception as e:
        print(f"❌ Error loading corrections: {e}", file=sys.stderr)
        return []

def save_corrections(corrections):
    """Save corrections to JSON file"""
    try:
        with open(CORRECTIONS_FILE, 'w', encoding='utf-8') as f:
            json.dump(corrections, f, indent=2, ensure_ascii=False)
        return True
    except Exception as e:
        print(f"❌ Error saving corrections: {e}", file=sys.stderr)
        return False

def add_correction(narration, category, userId=None, transactionId=None):
    """Add a correction to the JSON file"""
    if not narration or not narration.strip():
        print("❌ Error: Narration is required", file=sys.stderr)
        return False
    
    if not category or not category.strip():
        print("❌ Error: Category is required", file=sys.stderr)
        return False
    
    # Load existing corrections
    corrections = load_corrections()
    
    # Create new correction entry
    correction = {
        "narration": narration.strip(),
        "category": category.strip(),
        "timestamp": datetime.now().isoformat()
    }
    
    # Add optional metadata
    if userId and userId.strip():
        correction["userId"] = userId.strip()
    
    if transactionId and transactionId.strip():
        correction["transactionId"] = transactionId.strip()
    
    # Check for duplicates (same narration and category)
    for existing in corrections:
        if (existing.get("narration", "").strip().lower() == correction["narration"].lower() and
            existing.get("category", "").strip() == correction["category"]):
            print(f"⚠️ Warning: Duplicate correction already exists: '{correction['narration'][:50]}...' -> '{correction['category']}'", file=sys.stderr)
            # Update timestamp instead of adding duplicate
            existing["timestamp"] = correction["timestamp"]
            if userId and userId.strip():
                existing["userId"] = userId.strip()
            if transactionId and transactionId.strip():
                existing["transactionId"] = transactionId.strip()
            if save_corrections(corrections):
                print(f"✅ Updated existing correction timestamp")
                return True
            return False
    
    # Add new correction
    corrections.append(correction)
    
    # Save to file
    if save_corrections(corrections):
        print(f"✅ Added correction to user_corrections.json: '{correction['narration'][:50]}...' -> '{correction['category']}'")
        print(f"📊 Total corrections in file: {len(corrections)}")
        print(f"📁 File location: {os.path.abspath(CORRECTIONS_FILE)}")
        return True
    else:
        print(f"❌ Failed to save correction to {CORRECTIONS_FILE}", file=sys.stderr)
        return False

def main():
    """Main entry point"""
    if len(sys.argv) < 3:
        print("Usage: python3 add_correction.py <narration> <category> [userId] [transactionId]", file=sys.stderr)
        sys.exit(1)
    
    narration = sys.argv[1]
    category = sys.argv[2]
    userId = sys.argv[3] if len(sys.argv) > 3 and sys.argv[3] else None
    transactionId = sys.argv[4] if len(sys.argv) > 4 and sys.argv[4] else None
    
    success = add_correction(narration, category, userId, transactionId)
    sys.exit(0 if success else 1)

if __name__ == "__main__":
    main()

