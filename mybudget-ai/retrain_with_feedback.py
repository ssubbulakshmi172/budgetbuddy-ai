#!/usr/bin/env python3
"""
Retrain model using accumulated user corrections.

Includes bias safeguards and validation to prevent bias amplification.

Usage:
    python3 retrain_with_feedback.py [--corrections-file corrections_for_training.csv]

Features:
- Validates corrections before retraining
- Checks for bias amplification risk
- Merges corrections with existing training data
- Retrains model with combined dataset
"""

import sys
import os
import pandas as pd
import logging
from pathlib import Path
import argparse

# Add current directory to path
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

def load_corrections(corrections_file='corrections_for_training.csv'):
    """Load user corrections from CSV"""
    if not os.path.exists(corrections_file):
        logger.error(f"❌ Corrections file not found: {corrections_file}")
        logger.info("💡 Run export_corrections.py first to generate corrections file")
        return None
    
    try:
        df = pd.read_csv(corrections_file)
        logger.info(f"📊 Loaded {len(df)} corrections from {corrections_file}")
        return df
    except Exception as e:
        logger.error(f"❌ Failed to load corrections: {e}")
        return None

def load_valid_categories(yaml_file='categories.yml'):
    """Load valid categories from YAML"""
    try:
        import yaml
        with open(yaml_file, 'r', encoding='utf-8') as f:
            data = yaml.safe_load(f)
        
        valid_categories = []
        if 'categories' in data:
            for cat in data['categories']:
                if isinstance(cat, dict) and 'name' in cat:
                    top_name = cat['name']
                    if 'subcategories' in cat:
                        for subcat in cat['subcategories']:
                            if isinstance(subcat, dict) and 'name' in subcat:
                                valid_categories.append(f"{top_name} / {subcat['name']}")
                    else:
                        valid_categories.append(top_name)
        
        return set(valid_categories)
    except Exception as e:
        logger.warning(f"Could not load categories from YAML: {e}")
        return None

def validate_corrections(df):
    """Validate corrections for quality"""
    original_count = len(df)
    
    # Remove duplicates
    df = df.drop_duplicates(subset=['narration', 'category'])
    logger.info(f"   Removed {original_count - len(df)} duplicates")
    
    # Filter out very short narrations (likely typos or errors)
    df = df[df['narration'].str.len() >= 5]
    logger.info(f"   Filtered to {len(df)} corrections with valid narrations (>=5 chars)")
    
    # Check category validity
    valid_categories = load_valid_categories()
    if valid_categories:
        invalid = df[~df['category'].isin(valid_categories)]
        if len(invalid) > 0:
            logger.warning(f"   ⚠️ Found {len(invalid)} corrections with invalid categories")
            logger.warning(f"      Invalid: {invalid['category'].unique().tolist()[:5]}")
            df = df[df['category'].isin(valid_categories)]
            logger.info(f"   Filtered to {len(df)} corrections with valid categories")
    
    logger.info(f"✅ Validated: {len(df)} corrections after filtering")
    return df

def check_bias_risk(corrections_df, original_train_df):
    """
    Check for potential bias amplification.
    Returns True if safe to retrain, False if bias risk detected.
    """
    logger.info("🔍 Checking for bias amplification risk...")
    
    # Check correction distribution
    correction_dist = corrections_df['category'].value_counts()
    original_dist = original_train_df['category'].value_counts()
    
    bias_risks = []
    
    for category in correction_dist.index:
        correction_count = correction_dist[category]
        
        if category in original_dist.index:
            original_count = original_dist[category]
            relative_change = correction_count / original_count if original_count > 0 else float('inf')
            
            # Alert if corrections heavily favor one category (>50% increase)
            if relative_change > 0.5:
                bias_risks.append({
                    'category': category,
                    'corrections': correction_count,
                    'original': original_count,
                    'relative_change': relative_change
                })
                logger.warning(f"   ⚠️ Category '{category}': {relative_change:.1%} correction rate")
                logger.warning(f"      Corrections: {correction_count}, Original: {original_count}")
    
    if bias_risks:
        logger.error("❌ Bias risk detected!")
        logger.error("   Categories with high correction rates may indicate bias.")
        logger.error("   Please review corrections before retraining.")
        return False
    
    logger.info("✅ No significant bias risk detected")
    return True

def load_existing_training_data():
    """Load existing training data from CSV files"""
    import glob
    
    transaction_files = glob.glob('transactions_*.csv')
    if not transaction_files:
        logger.error("❌ No training data files found (transactions_*.csv)")
        return None
    
    logger.info(f"📊 Loading training data from {len(transaction_files)} files...")
    
    all_data = []
    for file in transaction_files:
        try:
            df = pd.read_csv(file)
            # Standardize column names
            if 'narration' in df.columns and 'category' in df.columns:
                all_data.append(df[['narration', 'category']])
        except Exception as e:
            logger.warning(f"Could not load {file}: {e}")
    
    if not all_data:
        logger.error("❌ No valid training data loaded")
        return None
    
    combined = pd.concat(all_data, ignore_index=True)
    combined = combined.drop_duplicates(subset=['narration'])
    
    logger.info(f"✅ Loaded {len(combined)} training samples")
    return combined

def merge_corrections_with_training(corrections_df, train_df):
    """Merge corrections with existing training data"""
    logger.info("🔄 Merging corrections with training data...")
    
    # Format corrections to match training format
    corrections_formatted = pd.DataFrame({
        'narration': corrections_df['narration'],
        'category': corrections_df['category']
    })
    
    # Combine
    combined_df = pd.concat([train_df, corrections_formatted], ignore_index=True)
    
    # Remove duplicates (keep corrections over original training data)
    original_count = len(combined_df)
    combined_df = combined_df.drop_duplicates(subset=['narration'], keep='last')
    logger.info(f"   Removed {original_count - len(combined_df)} duplicates")
    
    logger.info(f"✅ Combined dataset: {len(combined_df)} samples")
    logger.info(f"   Original training: {len(train_df)}")
    logger.info(f"   Corrections added: {len(corrections_formatted)}")
    
    return combined_df

def main():
    parser = argparse.ArgumentParser(description='Retrain model with user feedback')
    parser.add_argument('--corrections-file', type=str, default='corrections_for_training.csv',
                       help='Corrections CSV file (default: corrections_for_training.csv)')
    parser.add_argument('--skip-bias-check', action='store_true',
                       help='Skip bias risk check (not recommended)')
    parser.add_argument('--output-file', type=str, default='transactions_with_corrections.csv',
                       help='Output combined dataset file')
    
    args = parser.parse_args()
    
    logger.info("🔄 Starting retraining with user feedback...")
    logger.info(f"   Corrections file: {args.corrections_file}")
    
    # 1. Load corrections
    corrections = load_corrections(args.corrections_file)
    if corrections is None or len(corrections) == 0:
        logger.error("❌ No corrections available. Skipping retraining.")
        logger.info("💡 Run export_corrections.py first to generate corrections file")
        return 1
    
    # 2. Validate corrections
    corrections = validate_corrections(corrections)
    if len(corrections) == 0:
        logger.error("❌ No valid corrections after validation")
        return 1
    
    # 3. Load existing training data
    original_train = load_existing_training_data()
    if original_train is None:
        logger.error("❌ Could not load training data")
        return 1
    
    # 4. Check bias risk
    if not args.skip_bias_check:
        if not check_bias_risk(corrections, original_train):
            logger.error("❌ Bias risk detected. Retraining aborted.")
            logger.info("   Review corrections and adjust before retraining.")
            logger.info("   Or use --skip-bias-check to proceed anyway (not recommended)")
            return 1
    
    # 5. Merge with training data
    combined_data = merge_corrections_with_training(corrections, original_train)
    
    # 6. Save combined dataset
    combined_data.to_csv(args.output_file, index=False)
    logger.info(f"✅ Saved combined dataset to {args.output_file}")
    
    # 7. Retrain model
    logger.info("🚀 Starting model retraining...")
    logger.info("   Run: python3 train_distilbert.py")
    logger.info("   Or update train_distilbert.py to use the combined dataset")
    
    logger.info("✅ Retraining preparation complete!")
    logger.info(f"   Next step: Train model with {args.output_file}")
    
    return 0

if __name__ == '__main__':
    sys.exit(main())

