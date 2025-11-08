#!/usr/bin/env python3
"""
Export user corrections from database for retraining.

This script exports transactions where users corrected AI predictions,
filtering by confidence threshold and validating data quality.

Usage:
    python3 export_corrections.py [--min-confidence 0.5] [--output corrections.csv]

Output:
    CSV file with corrections ready for retraining:
    - narration: Transaction description
    - category: User-corrected category
    - predicted_category: Original AI prediction
    - confidence: AI prediction confidence
    - date: Transaction date
"""

import argparse
import sys
import os
import pandas as pd
from datetime import datetime
import logging

# Add parent directory to path for imports
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

def load_categories_from_yaml(yaml_file='categories.yml'):
    """Load valid categories from YAML file"""
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

def load_db_config_from_properties():
    """Load database config from Spring Boot application.properties"""
    props_file = '../src/main/resources/application.properties'
    if os.path.exists(props_file):
        try:
            config = {}
            with open(props_file, 'r') as f:
                for line in f:
                    line = line.strip()
                    if line and not line.startswith('#') and '=' in line:
                        key, value = line.split('=', 1)
                        if 'spring.datasource.url' in key:
                            # Parse JDBC URL: jdbc:mysql://localhost:3306/budgetbuddy_app
                            import re
                            match = re.search(r'://([^:]+):(\d+)/(\w+)', value)
                            if match:
                                config['host'] = match.group(1)
                                config['port'] = int(match.group(2))
                                config['database'] = match.group(3)
                        elif 'spring.datasource.username' in key:
                            config['user'] = value
                        elif 'spring.datasource.password' in key:
                            config['password'] = value
            if 'host' in config:
                logger.info(f"✅ Loaded DB config from application.properties")
                return config
        except Exception as e:
            logger.debug(f"Could not load from properties: {e}")
    return None

def export_corrections_from_csv(transactions_file='transactions_*.csv', 
                                 min_confidence=0.5,
                                 output_file='corrections_for_training.csv'):
    """
    Export corrections from CSV transaction files.
    
    Alternative to database export - useful if database is not accessible.
    Both methods are privacy-first since database is local.
    """
    import glob
    
    # Find all transaction CSV files
    transaction_files = glob.glob(transactions_file)
    if not transaction_files:
        logger.error(f"No transaction files found matching: {transactions_file}")
        return None
    
    logger.info(f"📊 Found {len(transaction_files)} transaction files")
    
    # Load all transactions
    all_transactions = []
    for file in transaction_files:
        try:
            df = pd.read_csv(file)
            if 'manual_category' in df.columns or 'categoryName' in df.columns:
                all_transactions.append(df)
                logger.info(f"   Loaded {len(df)} transactions from {file}")
        except Exception as e:
            logger.warning(f"Could not load {file}: {e}")
    
    if not all_transactions:
        logger.error("No transaction data loaded")
        return None
    
    # Combine all transactions
    combined_df = pd.concat(all_transactions, ignore_index=True)
    logger.info(f"📊 Total transactions loaded: {len(combined_df)}")
    
    # Identify corrections
    # Corrections are where manual_category/categoryName != predicted_category
    manual_col = None
    predicted_col = None
    confidence_col = None
    
    for col in combined_df.columns:
        if 'manual' in col.lower() or 'categoryname' in col.lower():
            manual_col = col
        if 'predicted_category' in col.lower():
            predicted_col = col
        if 'confidence' in col.lower():
            confidence_col = col
    
    if not manual_col or not predicted_col:
        logger.warning("Could not find manual/predicted category columns")
        logger.info(f"Available columns: {combined_df.columns.tolist()}")
        return None
    
    # Filter corrections
    corrections = combined_df[
        (combined_df[manual_col].notna()) &
        (combined_df[manual_col] != combined_df[predicted_col]) &
        (combined_df[confidence_col] >= min_confidence if confidence_col and confidence_col in combined_df.columns else True)
    ].copy()
    
    logger.info(f"✅ Found {len(corrections)} corrections")
    
    if len(corrections) == 0:
        logger.warning("No corrections found matching criteria")
        return None
    
    # Prepare output
    output_df = pd.DataFrame({
        'narration': corrections.get('narration', ''),
        'category': corrections[manual_col],
        'predicted_category': corrections[predicted_col],
        'confidence': corrections.get(confidence_col, 0.0) if confidence_col else 0.0,
        'date': corrections.get('date', ''),
        'source': 'user_correction',
        'export_date': datetime.now().isoformat()
    })
    
    # Validate categories
    valid_categories = load_categories_from_yaml()
    if valid_categories:
        invalid = output_df[~output_df['category'].isin(valid_categories)]
        if len(invalid) > 0:
            logger.warning(f"⚠️ Found {len(invalid)} corrections with invalid categories")
            logger.warning(f"   Invalid categories: {invalid['category'].unique().tolist()}")
            # Filter out invalid categories
            output_df = output_df[output_df['category'].isin(valid_categories)]
            logger.info(f"✅ Filtered to {len(output_df)} valid corrections")
    
    # Remove duplicates
    output_df = output_df.drop_duplicates(subset=['narration', 'category'])
    logger.info(f"✅ After deduplication: {len(output_df)} corrections")
    
    # Save to CSV
    output_df.to_csv(output_file, index=False)
    logger.info(f"✅ Exported {len(output_df)} corrections to {output_file}")
    
    # Print summary
    logger.info("\n📊 Correction Summary:")
    logger.info(f"   Total corrections: {len(output_df)}")
    logger.info(f"   Categories corrected: {output_df['category'].nunique()}")
    logger.info(f"   Average confidence: {output_df['confidence'].mean():.3f}")
    
    return output_df

def export_corrections_from_database(db_config=None, 
                                     min_confidence=0.5,
                                     output_file='corrections_for_training.csv'):
    """
    Export corrections from MySQL database.
    
    Since the database is local (MySQL on same machine), this is privacy-first.
    No data leaves the local environment.
    
    Requires database connection configuration.
    """
    try:
        import mysql.connector
    except ImportError:
        logger.error("mysql-connector-python not installed.")
        logger.info("💡 Install with: pip install mysql-connector-python")
        logger.info("   Or use CSV export: python3 export_corrections.py --source csv")
        return None
    
    if db_config is None:
        # Try to load from application.properties or environment
        db_config = load_db_config_from_properties()
        
        # Fallback to environment variables
        if not db_config:
            db_config = {
                'host': os.getenv('DB_HOST', 'localhost'),
                'user': os.getenv('DB_USER', 'root'),
                'password': os.getenv('DB_PASSWORD', ''),
                'database': os.getenv('DB_NAME', 'budgetbuddy_app')
            }
    
    try:
        conn = mysql.connector.connect(**db_config)
        
        query = """
            SELECT 
                narration,
                categoryName as category,
                predictedCategory as predicted_category,
                predictionConfidence as confidence,
                date
            FROM transaction
            WHERE categoryName IS NOT NULL
              AND categoryName != predictedCategory
              AND (predictionConfidence IS NULL OR predictionConfidence >= %s)
            ORDER BY date DESC
        """
        
        df = pd.read_sql(query, conn, params=[min_confidence])
        conn.close()
        
        if len(df) == 0:
            logger.warning("No corrections found in database")
            return None
        
        # Add metadata
        df['source'] = 'user_correction'
        df['export_date'] = datetime.now().isoformat()
        
        # Validate categories
        valid_categories = load_categories_from_yaml()
        if valid_categories:
            df = df[df['category'].isin(valid_categories)]
        
        # Remove duplicates
        df = df.drop_duplicates(subset=['narration', 'category'])
        
        # Save to CSV
        df.to_csv(output_file, index=False)
        logger.info(f"✅ Exported {len(df)} corrections to {output_file}")
        
        return df
        
    except Exception as e:
        logger.error(f"Database export failed: {e}")
        logger.info("💡 Try using CSV export instead: python3 export_corrections.py --source csv")
        return None

def main():
    parser = argparse.ArgumentParser(description='Export user corrections for retraining')
    parser.add_argument('--min-confidence', type=float, default=0.5,
                       help='Minimum AI confidence to include correction (default: 0.5)')
    parser.add_argument('--output', type=str, default='corrections_for_training.csv',
                       help='Output CSV file path (default: corrections_for_training.csv)')
    parser.add_argument('--source', type=str, default='database', choices=['csv', 'database'],
                       help='Data source: database (default, local MySQL) or csv files')
    
    args = parser.parse_args()
    
    logger.info("🔄 Starting correction export...")
    logger.info(f"   Min confidence: {args.min_confidence}")
    logger.info(f"   Output file: {args.output}")
    logger.info(f"   Source: {args.source}")
    
    if args.source == 'database':
        logger.info("   Using local MySQL database (privacy-first, no data leaves machine)")
        result = export_corrections_from_database(
            min_confidence=args.min_confidence,
            output_file=args.output
        )
        if result is None:
            logger.info("💡 Database export failed. Trying CSV export as fallback...")
            result = export_corrections_from_csv(
                min_confidence=args.min_confidence,
                output_file=args.output
            )
    else:
        logger.info("   Using CSV files (alternative to database)")
        result = export_corrections_from_csv(
            min_confidence=args.min_confidence,
            output_file=args.output
        )
    
    if result is not None:
        logger.info(f"✅ Export complete: {len(result)} corrections exported")
        return 0
    else:
        logger.error("❌ Export failed")
        return 1

if __name__ == '__main__':
    sys.exit(main())

