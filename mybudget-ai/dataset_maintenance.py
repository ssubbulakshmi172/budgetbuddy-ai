#!/usr/bin/env python3
"""
Dataset Maintenance Script

Unified script for generating and maintaining synthetic transaction datasets.
Provides two main methods:
1. generate_dataset() - Generate new synthetic dataset matching categories.yml
2. verify_and_balance_dataset() - Verify coverage and auto-balance dataset
"""

import yaml
import csv
import pandas as pd
import random
from typing import List, Dict, Tuple
from collections import Counter

# Configuration
TARGET_SAMPLES_PER_CATEGORY = 50
MIN_SAMPLES_PER_CATEGORY = 20
CATEGORIES_FILE = "categories.yml"
SYNTHETIC_FILE = "transactions_synthetic.csv"
MAXIMAL_FILE = "transactions_maximal.csv"


def load_categories():
    """Load categories from YAML file"""
    with open(CATEGORIES_FILE, 'r', encoding='utf-8') as f:
        data = yaml.safe_load(f)
    return data['categories']


def generate_narration(category_name: str, subcategory_name: str, keywords: List[str]) -> str:
    """Generate realistic transaction narration based on keywords"""
    # Select random keyword(s) from the subcategory
    selected_keywords = random.sample(keywords, min(2, len(keywords)))
    
    # Generate narrations based on category type
    if 'Dining' in subcategory_name or 'Restaurant' in subcategory_name:
        narrations = [
            f"Dinner at {selected_keywords[0].title()} Restaurant",
            f"Lunch at {selected_keywords[0].title()}",
            f"Breakfast at {selected_keywords[0].title()} Cafe",
            f"UPI-{selected_keywords[0].upper()}-RESTAURANT@UPI-500123456789",
            f"Ordered from {selected_keywords[0].title()}",
            f"Fine dining at {selected_keywords[0].title()}",
        ]
    elif 'Food Delivery' in subcategory_name or 'Takeaway' in subcategory_name:
        services = ['SWIGGY', 'ZOMATO', 'UBER EATS']
        items = ['PIZZA', 'BURGER', 'FOOD', 'COFFEE']
        narrations = [
            f"UPI-{random.choice(services)}-{random.choice(items)}@PAYTM-500123456789",
            f"Order from {random.choice(services)} - {random.choice(items)}",
            f"{random.choice(services)} Food Delivery",
            f"Online order from {random.choice(services)}",
            f"Takeaway from {selected_keywords[0].title()}",
        ]
    elif 'Groceries' in subcategory_name:
        narrations = [
            f"Grocery purchase at {selected_keywords[0].title()}",
            f"Supermarket - {selected_keywords[0].title()}",
            f"UPI-{selected_keywords[0].upper()}-MART@UPI-500123456789",
            f"Bought {selected_keywords[0]} and {selected_keywords[-1]}",
            f"{selected_keywords[0].title()} Store",
            f"Fresh {selected_keywords[0]} purchase",
        ]
    elif 'Social' in subcategory_name or 'Outings' in subcategory_name:
        narrations = [
            f"Dinner with friends at {selected_keywords[0].title()}",
            f"Lunch with friends",
            f"Group outing - {selected_keywords[0].title()}",
            f"Shared expense with friends",
            f"Hangout - {selected_keywords[0].title()}",
            f"Social gathering payment",
        ]
    elif 'Money Lent' in subcategory_name or 'Borrowed' in subcategory_name:
        narrations = [
            f"Money lent to friend",
            f"Borrowed from friend",
            f"Loan to {selected_keywords[0].title()}",
            f"Personal loan repayment",
            f"Given to friend",
        ]
    elif 'Gifts' in subcategory_name:
        narrations = [
            f"Birthday gift to friend",
            f"Wedding gift payment",
            f"Anniversary gift",
            f"Gift purchase for {selected_keywords[0].title()}",
            f"Holiday gift",
        ]
    elif 'Shopping' in subcategory_name or 'Fashion' in subcategory_name:
        narrations = [
            f"Purchase from {selected_keywords[0].title()}",
            f"{selected_keywords[0].title()} Shopping",
            f"Online order - {selected_keywords[0].title()}",
            f"UPI-{selected_keywords[0].upper()}@PAYTM-500123456789",
            f"{selected_keywords[0].title()} Mall Purchase",
        ]
    elif 'Transport' in subcategory_name or 'Commuting' in subcategory_name:
        narrations = [
            f"{selected_keywords[0].title()} ride",
            f"UPI-{selected_keywords[0].upper()}@PAYTM-500123456789",
            f"{selected_keywords[0].title()} booking",
            f"Commute via {selected_keywords[0].title()}",
        ]
    elif 'Fuel' in subcategory_name or 'Petrol' in subcategory_name:
        narrations = [
            f"{selected_keywords[0].title()} refill",
            f"Gas station - {selected_keywords[0].title()}",
            f"Fuel purchase at {selected_keywords[0].title()}",
        ]
    elif 'Insurance' in subcategory_name:
        narrations = [
            f"{selected_keywords[0].title()} Insurance Premium",
            f"Insurance payment - {selected_keywords[0].title()}",
        ]
    elif 'Taxes' in subcategory_name:
        narrations = [
            f"{selected_keywords[0].title()} Tax Payment",
            f"Tax - {selected_keywords[0].title()}",
        ]
    elif 'Education' in subcategory_name:
        narrations = [
            f"{selected_keywords[0].title()} fees",
            f"Education - {selected_keywords[0].title()}",
            f"School payment - {selected_keywords[0].title()}",
        ]
    elif 'Utilities' in subcategory_name or 'Household Bills' in subcategory_name:
        narrations = [
            f"{selected_keywords[0].title()} bill payment",
            f"Utility - {selected_keywords[0].title()}",
            f"{selected_keywords[0].title()} recharge",
        ]
    elif 'Stocks' in subcategory_name or 'Bonds' in subcategory_name:
        narrations = [
            f"Stock market transaction - {selected_keywords[0].title()}",
            f"ACH D- INDIAN CLEARING CORP-{random.randint(100000, 999999)}",
            f"{selected_keywords[0].title()} trading",
        ]
    elif 'Loan' in subcategory_name:
        narrations = [
            f"{selected_keywords[0].title()} EMI Payment",
            f"Loan repayment - {selected_keywords[0].title()}",
        ]
    elif 'ATM' in subcategory_name or 'Cash' in subcategory_name:
        narrations = [
            f"ATM Cash Withdrawal",
            f"Cash withdrawal at branch",
            f"ATM-{random.randint(100000, 999999)}",
        ]
    else:
        # Generic narration
        narrations = [
            f"{selected_keywords[0].title()} payment",
            f"UPI-{selected_keywords[0].upper()}@PAYTM-500123456789",
            f"Payment for {selected_keywords[0].title()}",
        ]
    
    return random.choice(narrations)


def generate_transactions(num_per_category: int = TARGET_SAMPLES_PER_CATEGORY) -> List[Dict]:
    """Generate synthetic transactions for all categories"""
    categories = load_categories()
    transactions = []
    
    transaction_types = ['P2C', 'P2P', 'P2Business']
    intents = ['purchase', 'transfer', 'refund', 'subscription', 'bill_payment']
    
    for category in categories:
        if 'subcategories' not in category:
            continue
        
        for subcategory in category['subcategories']:
            subcat_name = subcategory['name']
            keywords = subcategory.get('keywords', [])
            full_category_name = f"{category['name']} / {subcat_name}"
            
            if not keywords:
                continue
            
            # Generate multiple transactions per subcategory
            for _ in range(num_per_category):
                narration = generate_narration(category['name'], subcat_name, keywords)
                
                # Determine transaction type and intent based on category and keywords
                narration_lower = narration.lower()
                
                # P2P-specific categories based on keywords
                p2p_keywords = ['friend', 'friends', 'outing', 'dinner', 'lunch', 'hangout', 
                               'group expense', 'reimbursed', 'lent', 'borrowed', 'gift', 
                               'birthday', 'wedding', 'anniversary', 'social', 'shared']
                
                is_p2p_category = any(kw in narration_lower for kw in p2p_keywords) or \
                                 'Social' in subcat_name or 'Outings' in subcat_name or \
                                 'Money Lent' in subcat_name or 'Borrowed' in subcat_name or \
                                 'Gifts' in subcat_name or 'Shared Expenses' in category['name']
                
                # Determine transaction type and intent
                if 'Loan' in subcat_name or 'Bill' in subcat_name or 'Insurance' in subcat_name:
                    txn_type = 'P2C'
                    intent = 'bill_payment'
                elif 'Transfer' in subcat_name or 'Payment' in subcat_name:
                    if is_p2p_category:
                        txn_type = 'P2P'
                    else:
                        txn_type = random.choice(['P2C', 'P2P'])
                    intent = 'transfer'
                elif 'Cash' in subcat_name or 'Withdrawal' in subcat_name:
                    txn_type = 'P2C'
                    intent = 'transfer'
                elif 'Refund' in narration or 'Received' in narration or 'Gifts Received' in subcat_name:
                    txn_type = 'P2P'
                    intent = 'refund'
                elif 'Subscription' in subcat_name or 'Membership' in subcat_name:
                    txn_type = 'P2C'
                    intent = 'subscription'
                elif is_p2p_category:
                    # Force P2P for social/shared expenses categories
                    txn_type = 'P2P'
                    if any(kw in narration_lower for kw in ['dinner', 'lunch', 'outing', 'hangout']):
                        intent = 'purchase'
                    elif any(kw in narration_lower for kw in ['lent', 'borrowed', 'given', 'received']):
                        intent = 'transfer'
                    elif any(kw in narration_lower for kw in ['gift', 'birthday', 'wedding', 'anniversary']):
                        intent = 'purchase'
                    else:
                        intent = 'transfer'
                else:
                    txn_type = random.choice(transaction_types)
                    intent = random.choice(['purchase', 'transfer'])
                
                transactions.append({
                    'narration': narration,
                    'transaction_type': txn_type,
                    'category': full_category_name,
                    'intent': intent
                })
    
    return transactions


def generate_dataset(num_per_category: int = TARGET_SAMPLES_PER_CATEGORY, 
                     output_synthetic: str = SYNTHETIC_FILE,
                     output_maximal: str = MAXIMAL_FILE) -> Tuple[int, int]:
    """
    Method 1: Generate new synthetic transaction dataset matching categories.yml
    
    Args:
        num_per_category: Number of samples to generate per subcategory
        output_synthetic: Path to save synthetic dataset
        output_maximal: Path to save maximal dataset (same content)
    
    Returns:
        Tuple of (total_transactions, unique_categories)
    """
    print("=" * 70)
    print("DATASET GENERATION")
    print("=" * 70)
    print()
    
    print("Generating synthetic transaction dataset...")
    transactions = generate_transactions(num_per_category=num_per_category)
    
    # Shuffle transactions
    random.shuffle(transactions)
    
    total_transactions = len(transactions)
    unique_categories = len(set(t['category'] for t in transactions))
    
    print(f"✅ Generated {total_transactions} transactions")
    print(f"✅ Unique categories: {unique_categories}")
    print()
    
    # Save as transactions_synthetic.csv (primary synthetic dataset)
    print(f"💾 Saving {output_synthetic}...")
    with open(output_synthetic, 'w', newline='', encoding='utf-8') as f:
        writer = csv.DictWriter(f, fieldnames=['narration', 'transaction_type', 'category', 'intent'])
        writer.writeheader()
        writer.writerows(transactions)
    
    print(f"   ✅ Saved {total_transactions} transactions to {output_synthetic}")
    
    # Also save as transactions_maximal.csv (can be used as additional training data)
    print(f"💾 Saving {output_maximal}...")
    with open(output_maximal, 'w', newline='', encoding='utf-8') as f:
        writer = csv.DictWriter(f, fieldnames=['narration', 'transaction_type', 'category', 'intent'])
        writer.writeheader()
        writer.writerows(transactions)
    
    print(f"   ✅ Saved {total_transactions} transactions to {output_maximal}")
    print()
    print("Note: Both files contain the same dataset.")
    print("      - transactions_synthetic.csv: Used when USE_SYNTHETIC_DATA=True")
    print("      - transactions_maximal.csv: Used when USE_ADDITIONAL_FILES=True")
    
    # Show category distribution
    cat_counts = Counter(t['category'] for t in transactions)
    print()
    print("Category distribution (top 10):")
    for cat, count in cat_counts.most_common(10):
        print(f"  {cat[:55]:55s} {count:>4} samples")
    
    return total_transactions, unique_categories


def verify_and_balance_dataset(input_file: str = SYNTHETIC_FILE,
                               target_samples: int = TARGET_SAMPLES_PER_CATEGORY,
                               min_samples: int = MIN_SAMPLES_PER_CATEGORY) -> Dict:
    """
    Method 2: Verify dataset coverage and auto-balance missing/low samples
    
    Args:
        input_file: Path to CSV file to verify
        target_samples: Target number of samples per category
        min_samples: Minimum samples required per category
    
    Returns:
        Dictionary with verification results and statistics
    """
    print("=" * 70)
    print("DATASET VERIFICATION & BALANCING")
    print("=" * 70)
    print()
    
    # Load categories.yml
    with open(CATEGORIES_FILE, 'r') as f:
        categories_config = yaml.safe_load(f)
    
    # Load transactions CSV
    df = pd.read_csv(input_file)
    
    print(f"📋 Total subcategories in {CATEGORIES_FILE}: {len(categories_config['categories'])}")
    print(f"📊 Total rows in {input_file}: {len(df)}")
    print()
    
    # Extract all subcategories from YAML with keywords
    yaml_subcategories = {}
    for cat in categories_config['categories']:
        if 'subcategories' in cat:
            for subcat in cat['subcategories']:
                full_name = f"{cat['name']} / {subcat['name']}"
                keywords = subcat.get('keywords', [])
                yaml_subcategories[full_name] = {
                    'keywords': keywords,
                    'category': cat['name'],
                    'subcategory': subcat['name']
                }
    
    # Get category counts from CSV
    csv_category_counts = Counter(df['category']) if 'category' in df.columns else Counter()
    
    print("=" * 70)
    print("COVERAGE ANALYSIS")
    print("=" * 70)
    print()
    
    # Categorize
    missing = []
    covered = []
    low_count = []
    
    for subcat_name, subcat_info in sorted(yaml_subcategories.items()):
        count = csv_category_counts.get(subcat_name, 0)
        if count == 0:
            missing.append((subcat_name, subcat_info))
        elif count < min_samples:
            low_count.append((subcat_name, count, subcat_info))
        else:
            covered.append((subcat_name, count))
    
    # Print results
    print(f"✅ Well covered (≥{min_samples} samples): {len(covered)}")
    print(f"⚠️  Low coverage (<{min_samples} samples): {len(low_count)}")
    print(f"❌ Missing (0 samples): {len(missing)}")
    print()
    
    if covered:
        print(f"\n✅ WELL COVERED CATEGORIES (≥{min_samples} samples):")
        print("-" * 70)
        for subcat, count in sorted(covered, key=lambda x: x[1], reverse=True)[:15]:
            print(f"  {subcat[:58]:58s} {count:>4} samples")
        if len(covered) > 15:
            print(f"  ... and {len(covered) - 15} more")
        print()
    
    if low_count:
        print(f"⚠️  LOW COVERAGE CATEGORIES (<{min_samples} samples):")
        print("-" * 70)
        for subcat, count, info in sorted(low_count, key=lambda x: x[1]):
            print(f"  {subcat[:58]:58s} {count:>4} samples")
            print(f"    Keywords: {', '.join(info['keywords'][:5])}{'...' if len(info['keywords']) > 5 else ''}")
        print()
    
    if missing:
        print("❌ MISSING CATEGORIES (0 samples):")
        print("-" * 70)
        for subcat, info in missing:
            print(f"  {subcat}")
            print(f"    Keywords: {', '.join(info['keywords'][:5])}{'...' if len(info['keywords']) > 5 else ''}")
        print()
    
    # Generate missing/low samples
    new_rows = []
    
    for subcat_name, count, info in low_count:
        needed = max(min_samples, target_samples) - count
        print(f"📝 Generating {needed} samples for: {subcat_name}")
        
        keywords = info['keywords']
        for i in range(needed):
            # Generate realistic narration using the shared function
            selected_keywords = random.sample(keywords, min(2, len(keywords))) if keywords else [info['subcategory']]
            narration = generate_narration(info['category'], info['subcategory'], selected_keywords)
            
            # Determine transaction type and intent
            narration_lower = narration.lower()
            if 'Loan' in info['subcategory'] or 'EMI' in info['subcategory']:
                txn_type = 'P2C'
                intent = 'bill_payment'
            elif 'Tax' in info['subcategory'] or 'Fee' in info['subcategory']:
                txn_type = 'P2C'
                intent = 'bill_payment'
            elif 'Insurance' in info['subcategory']:
                txn_type = 'P2C'
                intent = 'bill_payment'
            elif 'Transfer' in info['subcategory'] or 'Payment' in info['subcategory']:
                txn_type = random.choice(['P2C', 'P2P'])
                intent = 'transfer'
            elif 'Social' in info['subcategory'] or 'Outings' in info['subcategory']:
                txn_type = 'P2P'
                intent = 'purchase'
            elif 'Lent' in info['subcategory'] or 'Borrowed' in info['subcategory']:
                txn_type = 'P2P'
                intent = 'transfer'
            elif 'Gift' in info['subcategory']:
                txn_type = 'P2P'
                intent = 'purchase'
            else:
                txn_type = random.choice(['P2C', 'P2P', 'P2Business'])
                intent = random.choice(['purchase', 'transfer'])
            
            new_rows.append({
                'narration': narration,
                'transaction_type': txn_type,
                'category': subcat_name,
                'intent': intent
            })
    
    for subcat_name, info in missing:
        needed = target_samples
        print(f"📝 Generating {needed} samples for: {subcat_name}")
        
        keywords = info['keywords']
        for i in range(needed):
            selected_keywords = random.sample(keywords, min(2, len(keywords))) if keywords else [info['subcategory']]
            narration = generate_narration(info['category'], info['subcategory'], selected_keywords)
            
            # Determine transaction type and intent
            if 'Loan' in info['subcategory'] or 'EMI' in info['subcategory']:
                txn_type = 'P2C'
                intent = 'bill_payment'
            elif 'Tax' in info['subcategory'] or 'Fee' in info['subcategory']:
                txn_type = 'P2C'
                intent = 'bill_payment'
            elif 'Insurance' in info['subcategory']:
                txn_type = 'P2C'
                intent = 'bill_payment'
            elif 'Transfer' in info['subcategory']:
                txn_type = random.choice(['P2C', 'P2P'])
                intent = 'transfer'
            elif 'Social' in info['subcategory'] or 'Outings' in info['subcategory']:
                txn_type = 'P2P'
                intent = 'purchase'
            elif 'Lent' in info['subcategory']:
                txn_type = 'P2P'
                intent = 'transfer'
            elif 'Gift' in info['subcategory']:
                txn_type = 'P2P'
                intent = 'purchase'
            else:
                txn_type = random.choice(['P2C', 'P2P', 'P2Business'])
                intent = random.choice(['purchase', 'transfer'])
            
            new_rows.append({
                'narration': narration,
                'transaction_type': txn_type,
                'category': subcat_name,
                'intent': intent
            })
    
    # Append new rows if any
    if new_rows:
        print()
        print(f"✅ Generated {len(new_rows)} new transaction samples")
        print()
        
        new_df = pd.DataFrame(new_rows)
        df_updated = pd.concat([df, new_df], ignore_index=True)
        
        # Save updated CSV
        df_updated.to_csv(input_file, index=False)
        print(f"💾 Updated {input_file}")
        print(f"   Previous: {len(df)} rows")
        print(f"   Added: {len(new_rows)} rows")
        print(f"   New total: {len(df_updated)} rows")
    else:
        print()
        print(f"✅ No missing or low-coverage categories found!")
        print(f"   All categories have adequate samples (≥{min_samples} per category)")
        df_updated = df
    
    # Final summary
    print()
    print("=" * 70)
    print("FINAL SUMMARY")
    print("=" * 70)
    print()
    
    final_counts = Counter(df_updated['category'])
    all_covered = all(final_counts.get(subcat, 0) >= min_samples for subcat in yaml_subcategories.keys())
    
    print(f"✅ Categories in YAML: {len(yaml_subcategories)}")
    print(f"✅ Categories in dataset: {len(final_counts)}")
    print(f"✅ All categories covered: {all_covered}")
    print()
    
    if all_covered:
        min_samples_found = min(final_counts.values())
        max_samples_found = max(final_counts.values())
        avg_samples = sum(final_counts.values()) / len(final_counts)
        
        print(f"📊 Sample distribution:")
        print(f"   Minimum: {min_samples_found} samples")
        print(f"   Maximum: {max_samples_found} samples")
        print(f"   Average: {avg_samples:.1f} samples")
        print()
        print("✅ Dataset is balanced and ready for training!")
    else:
        print("⚠️  Some categories still need more samples")
        print("   Run verify_and_balance_dataset() again to generate missing samples")
    
    return {
        'total_categories': len(yaml_subcategories),
        'covered_categories': len(covered),
        'low_coverage': len(low_count),
        'missing_categories': len(missing),
        'total_rows': len(df_updated),
        'new_rows_added': len(new_rows),
        'all_covered': all_covered,
        'category_counts': dict(final_counts)
    }


def main():
    """Main entry point with CLI"""
    import sys
    
    if len(sys.argv) > 1:
        command = sys.argv[1].lower()
        
        if command == 'generate' or command == 'gen':
            print("🔄 Generating new dataset...")
            random.seed(42)
            total, unique = generate_dataset()
            print()
            print(f"✅ Generation complete: {total} transactions, {unique} categories")
        
        elif command == 'verify' or command == 'balance':
            print("🔍 Verifying and balancing dataset...")
            random.seed(42)
            result = verify_and_balance_dataset()
            print()
            print(f"✅ Verification complete")
            print(f"   Covered: {result['covered_categories']}/{result['total_categories']}")
            print(f"   Added: {result['new_rows_added']} new rows")
        
        elif command == 'both' or command == 'all':
            print("🔄 Generating new dataset...")
            random.seed(42)
            generate_dataset()
            print()
            print("🔍 Verifying and balancing dataset...")
            result = verify_and_balance_dataset()
            print()
            print(f"✅ Complete! Dataset ready for training")
        
        else:
            print(f"Unknown command: {command}")
            print("Usage: python3 dataset_maintenance.py [generate|verify|both]")
    else:
        # Default: show help
        print("=" * 70)
        print("DATASET MAINTENANCE SCRIPT")
        print("=" * 70)
        print()
        print("Usage:")
        print("  python3 dataset_maintenance.py generate  - Generate new dataset")
        print("  python3 dataset_maintenance.py verify    - Verify & balance dataset")
        print("  python3 dataset_maintenance.py both     - Generate + verify")
        print()
        print("Or use in Python:")
        print("  from dataset_maintenance import generate_dataset, verify_and_balance_dataset")
        print("  generate_dataset()")
        print("  verify_and_balance_dataset()")


if __name__ == '__main__':
    main()

