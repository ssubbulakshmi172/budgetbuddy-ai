#!/usr/bin/env python3
"""
get_categories.py

Extract all category names from categories.yml and output as JSON array.
Used by Spring Boot TransactionController to populate category dropdowns.
"""

import sys
import json
import os
import yaml
import logging

# Suppress warnings for cleaner output
logging.basicConfig(level=logging.WARNING)

# Configuration
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
CATEGORIES_FILE = os.path.join(SCRIPT_DIR, "categories.yml")


def get_categories(categories_file: str = CATEGORIES_FILE) -> list:
    """
    Extract all category names from categories.yml
    
    Returns:
        List of category names (top-level categories)
    """
    categories = []
    
    if not os.path.exists(categories_file):
        sys.stderr.write(f"⚠️ Categories file not found: {categories_file}\n")
        return categories
    
    try:
        with open(categories_file, 'r', encoding='utf-8') as f:
            categories_config = yaml.safe_load(f)
        
        if not categories_config or 'categories' not in categories_config:
            sys.stderr.write("⚠️ No 'categories' section found in YAML\n")
            return categories
        
        # Extract top-level category names
        for cat in categories_config['categories']:
            if isinstance(cat, dict) and 'name' in cat:
                category_name = cat['name'].strip()
                if category_name:
                    categories.append(category_name)
        
        # Also extract subcategory names in format "Category / Subcategory"
        for cat in categories_config['categories']:
            if isinstance(cat, dict) and 'name' in cat:
                top_name = cat['name'].strip()
                if 'subcategories' in cat and isinstance(cat['subcategories'], list):
                    for subcat in cat['subcategories']:
                        if isinstance(subcat, dict) and 'name' in subcat:
                            subcat_name = subcat['name'].strip()
                            full_name = f"{top_name} / {subcat_name}"
                            if full_name not in categories:
                                categories.append(full_name)
        
    except Exception as e:
        sys.stderr.write(f"❌ Error loading categories from {categories_file}: {e}\n")
        return categories
    
    return sorted(categories)


def main():
    """Main entry point"""
    try:
        categories = get_categories()
        
        # Output JSON array to stdout (only JSON, no other output)
        json_output = json.dumps(categories)
        print(json_output, file=sys.stdout, flush=True)
        
        # Exit with error only if no categories found
        if not categories:
            sys.exit(1)
            
    except Exception as e:
        sys.stderr.write(f"❌ Error: {e}\n")
        sys.exit(1)


if __name__ == "__main__":
    main()

