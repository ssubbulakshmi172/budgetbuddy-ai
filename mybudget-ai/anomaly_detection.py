#!/usr/bin/env python3
"""
Enhanced Anomaly Detection using Isolation Forest

Detects unusual transactions (both expenses and deposits) using separate models.
Called from Java via ProcessBuilder.

Usage:
    python3 anomaly_detection.py <userId>

Output: JSON array with anomaly scores (compact format, no indentation)

Transaction Amount Convention:
- Deposits/Credits = Positive amount
- Withdrawals/Expenses = Negative amount
"""

import sys
import os
import json
import pandas as pd
import numpy as np
from datetime import datetime, timedelta
from sklearn.ensemble import IsolationForest
from sklearn.preprocessing import StandardScaler
import warnings
warnings.filterwarnings('ignore')

# Add parent directory to path for imports
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

# Optional: BERT embeddings (install: pip install sentence-transformers)
USE_BERT_EMBEDDINGS = False  # Set to True to enable BERT features
BERT_MODEL = None

if USE_BERT_EMBEDDINGS:
    try:
        from sentence_transformers import SentenceTransformer
        BERT_MODEL = SentenceTransformer('distilbert-base-nli-stsb-mean-tokens')
        sys.stderr.write("✅ BERT embeddings enabled\n")
    except ImportError:
        sys.stderr.write("⚠️ sentence-transformers not installed. BERT embeddings disabled.\n")
        USE_BERT_EMBEDDINGS = False


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
                return config
        except Exception as e:
            pass
    return None


def load_transactions_from_database(user_id, db_config=None):
    """
    Load transactions from database for last year (365 days).
    Includes BOTH positive (deposits) and negative (expenses) transactions.
    Excludes internal transfers.
    """
    try:
        import mysql.connector
    except ImportError:
        sys.stderr.write("mysql-connector-python not installed.\n")
        return None
    
    if db_config is None:
        db_config = load_db_config_from_properties()
        if not db_config:
            db_config = {
                'host': os.getenv('DB_HOST', 'localhost'),
                'user': os.getenv('DB_USER', 'root'),
                'password': os.getenv('DB_PASSWORD', ''),
                'database': os.getenv('DB_NAME', 'budgetbuddy_app')
            }
    
    try:
        conn = mysql.connector.connect(**db_config)
        
        one_year_ago = (datetime.now() - timedelta(days=365)).strftime('%Y-%m-%d')
        
        query = """
            SELECT 
                id,
                amount,
                date,
                category_name as categoryName,
                predicted_category as predictedCategory,
                predicted_intent as predictedIntent,
                narration
            FROM transaction
            WHERE user_id = %s
              AND date >= %s
              AND narration NOT LIKE '%%SELF TRANSFER%%'
              AND narration NOT LIKE '%%TO OWN ACCOUNT%%'
              AND (predicted_category IS NULL OR predicted_category NOT LIKE 'Investments%%')
            ORDER BY date ASC
        """
        
        df = pd.read_sql(query, conn, params=[user_id, one_year_ago])
        conn.close()
        
        if len(df) == 0:
            return None
        
        # Ensure date is datetime
        df['date'] = pd.to_datetime(df['date'])
        
        return df
        
    except Exception as e:
        sys.stderr.write(f"Database error: {e}\n")
        return None


def extract_merchant_pattern(narration):
    """
    Extract merchant pattern from narration.
    Improved stability: lowercase, remove UPI prefixes, remove IDs.
    Returns first strongest token as merchant fingerprint.
    """
    if pd.isna(narration) or narration == '':
        return "unknown"
    
    import re
    
    # Convert to lowercase for stability
    cleaned = str(narration).lower()
    
    # Remove UPI prefixes
    cleaned = re.sub(r'^upi[-/]', '', cleaned)
    cleaned = re.sub(r'\bupi\b', '', cleaned)
    
    # Remove transaction IDs (long numbers)
    cleaned = re.sub(r'\d{9,}', '', cleaned)
    cleaned = re.sub(r'[-/]\d{6,}', '', cleaned)
    
    # Remove bank tags (@YBL, @HDFCBANK, etc.)
    cleaned = re.sub(r'@[a-z0-9]+', '', cleaned)
    
    # Remove special characters, keep only alphanumeric and spaces
    cleaned = re.sub(r'[^a-z0-9\s]', ' ', cleaned)
    
    # Normalize whitespace
    cleaned = re.sub(r'\s+', ' ', cleaned).strip()
    
    # Extract first significant token (merchant fingerprint)
    words = cleaned.split()
    if len(words) > 0:
        # Take first word as merchant fingerprint
        merchant = words[0]
        # If first word is too short, try second word
        if len(merchant) < 3 and len(words) > 1:
            merchant = words[1]
        return merchant if len(merchant) >= 2 else "unknown"
    
    return "unknown"


def is_recurring_monthly(transactions_df, min_occurrences=3):
    """
    Check if transactions recur monthly.
    Monthly recurrence window: 25-35 days (instead of hard 27-33).
    """
    if len(transactions_df) < min_occurrences:
        return False
    
    # Sort by date
    transactions_sorted = transactions_df.sort_values('date').copy()
    
    # Check if all consecutive transactions are approximately monthly
    # Window: 25-35 days (approximately 30 days ±5 days)
    for i in range(1, len(transactions_sorted)):
        days_between = (transactions_sorted.iloc[i]['date'] - transactions_sorted.iloc[i-1]['date']).days
        if days_between < 25 or days_between > 35:
            return False
    
    return True


def identify_regular_monthly_spending(df):
    """
    Identify transactions that are part of regular monthly spending patterns.
    Uses 25-35 day window for monthly recurrence.
    """
    if len(df) < 3:
        return set()
    
    # Group by merchant pattern and amount
    df = df.copy()
    df['merchant_pattern'] = df['narration'].apply(extract_merchant_pattern)
    df['amount_abs'] = df['amount'].abs()
    
    # Round amount to nearest 10 for grouping (handle small variations)
    df['amount_rounded'] = (df['amount_abs'] / 10).round() * 10
    
    regular_transaction_ids = set()
    
    # Group by merchant pattern and rounded amount
    grouped = df.groupby(['merchant_pattern', 'amount_rounded'])
    
    for (merchant, amount_rounded), group_df in grouped:
        if len(group_df) >= 3:  # At least 3 occurrences (3 months)
            # Check if recurring monthly (25-35 days window)
            if is_recurring_monthly(group_df, min_occurrences=3):
                regular_transaction_ids.update(group_df['id'].tolist())
    
    return regular_transaction_ids


def extract_features(df, transaction_type='expense'):
    """
    Extract features for anomaly detection.
    
    Features:
    - Amount features (absolute value for ML)
    - Time features (day of week, day of month, month, weekend)
    - Salary week feature (day <= 5)
    - Month-end scarcity feature (day >= 26)
    - Rolling averages based on previous transactions (not date gaps)
    - Category and intent encoding
    - Optional: BERT embeddings for narration
    """
    features = pd.DataFrame()
    
    # Ensure date is datetime
    if not pd.api.types.is_datetime64_any_dtype(df['date']):
        df['date'] = pd.to_datetime(df['date'])
    
    # Amount features (use absolute value for ML)
    features['amount'] = df['amount'].abs()
    features['log_amount'] = np.log1p(features['amount'])
    
    # Time features
    features['day_of_week'] = df['date'].dt.dayofweek
    features['day_of_month'] = df['date'].dt.day
    features['month'] = df['date'].dt.month
    features['is_weekend'] = (features['day_of_week'] >= 5).astype(int)
    
    # Salary week feature (first 5 days of month)
    features['is_salary_week'] = (features['day_of_month'] <= 5).astype(int)
    
    # Month-end scarcity feature (last 5 days of month)
    features['is_month_end'] = (features['day_of_month'] >= 26).astype(int)
    
    # Category encoding - use predictedCategory → categoryName → 'Unknown'
    category_col = df['predictedCategory'].fillna(df['categoryName']).fillna('Unknown')
    category_codes = pd.Categorical(category_col).codes
    features['category_encoded'] = category_codes
    
    # Intent encoding - use predictedIntent → 'unknown'
    intent_col = df['predictedIntent'].fillna('unknown')
    intent_codes = pd.Categorical(intent_col).codes
    features['intent_encoded'] = intent_codes
    
    # Rolling averages based on previous transactions (not date gaps)
    # Sort by date to ensure chronological order, but keep original index
    sort_order = df['date'].argsort()
    df_sorted = df.iloc[sort_order]
    features_sorted = features.iloc[sort_order].copy()
    
    # Calculate rolling averages based on transaction count (not days)
    # rolling_txn_avg_5: average of last 5 transactions
    # rolling_txn_avg_20: average of last 20 transactions
    features_sorted['rolling_txn_avg_5'] = features_sorted['amount'].rolling(
        window=5, min_periods=1
    ).mean()
    features_sorted['rolling_txn_avg_20'] = features_sorted['amount'].rolling(
        window=20, min_periods=1
    ).mean()
    
    # Fill NaN with mean
    features_sorted['rolling_txn_avg_5'] = features_sorted['rolling_txn_avg_5'].fillna(
        features_sorted['amount'].mean()
    )
    features_sorted['rolling_txn_avg_20'] = features_sorted['rolling_txn_avg_20'].fillna(
        features_sorted['amount'].mean()
    )
    
    # Reorder to match original df order (restore original index order)
    features = features_sorted.reindex(df.index)
    
    # Fill any NaN values that might have been introduced during reindexing
    # Use forward fill, then backward fill, then fill with 0
    features = features.ffill().bfill().fillna(0)
    
    # Optional: BERT embeddings for narration
    if USE_BERT_EMBEDDINGS and BERT_MODEL is not None:
        try:
            narrations = df['narration'].fillna('').astype(str).tolist()
            bert_embeddings = BERT_MODEL.encode(narrations, show_progress_bar=False)
            
            # Add BERT embeddings as features (first 10 dimensions for efficiency)
            for i in range(min(10, bert_embeddings.shape[1])):
                features[f'bert_emb_{i}'] = bert_embeddings[:, i]
        except Exception as e:
            sys.stderr.write(f"⚠️ BERT embedding error: {e}\n")
    
    return features


def calculate_dynamic_contamination(num_transactions):
    """
    Calculate dynamic contamination based on transaction count.
    - <50 transactions → 0.02 (2%)
    - 50-150 transactions → 0.03 (3%)
    - >150 transactions → 0.05 (5%)
    """
    if num_transactions < 50:
        return 0.02
    elif num_transactions <= 150:
        return 0.03
    else:
        return 0.05


def detect_anomalies(df, transaction_type='expense'):
    """
    Detect anomalies using Isolation Forest.
    Returns dataframe with anomaly_score and is_anomaly columns.
    """
    if len(df) < 10:
        # Not enough data
        return df.assign(anomaly_score=0.0, is_anomaly=False)
    
    # Extract features
    features = extract_features(df, transaction_type)
    
    # Standardize features before model
    scaler = StandardScaler()
    features_scaled = scaler.fit_transform(features)
    
    # Calculate dynamic contamination
    contamination = calculate_dynamic_contamination(len(df))
    
    # Fit Isolation Forest
    iso_forest = IsolationForest(
        contamination=contamination,
        random_state=42,
        n_estimators=100,
        n_jobs=-1  # Use all CPU cores
    )
    
    # Get predictions and scores
    anomaly_predictions = iso_forest.fit_predict(features_scaled)
    anomaly_scores = iso_forest.decision_function(features_scaled)
    
    # Add results to dataframe
    df_result = df.copy()
    df_result['anomaly_score'] = anomaly_scores
    df_result['is_anomaly'] = (anomaly_predictions == -1)
    
    return df_result


def prepare_output(anomalies_df):
    """
    Prepare final JSON output format.
    Returns list of dictionaries with required fields.
    """
    results = []
    
    for _, row in anomalies_df.iterrows():
        # Category: predictedCategory → categoryName → 'Unknown'
        category = 'Unknown'
        if pd.notna(row['predictedCategory']):
            category = str(row['predictedCategory'])
        elif pd.notna(row['categoryName']):
            category = str(row['categoryName'])
        
        # Intent: predictedIntent → 'unknown'
        intent = 'unknown'
        if pd.notna(row['predictedIntent']):
            intent = str(row['predictedIntent'])
        
        # Amount: positive absolute value
        amount = float(abs(row['amount'])) if pd.notna(row['amount']) else 0.0
        
        # Date: YYYY-MM-DD format
        date_str = None
        if pd.notna(row['date']):
            if isinstance(row['date'], pd.Timestamp):
                date_str = row['date'].strftime('%Y-%m-%d')
            else:
                date_str = str(row['date'])
        
        # Narration
        narration = str(row['narration']) if pd.notna(row['narration']) else ''
        
        # Anomaly score
        anomaly_score = float(row['anomaly_score']) if pd.notna(row['anomaly_score']) else 0.0
        
        results.append({
            'transaction_id': int(row['id']) if pd.notna(row['id']) else None,
            'date': date_str,
            'amount': amount,
            'category': category,
            'intent': intent,
            'narration': narration,
            'anomaly_score': anomaly_score
        })
    
    return results


def main():
    """Main function - entry point for Java ProcessBuilder"""
    if len(sys.argv) < 2:
        sys.stderr.write("Usage: python3 anomaly_detection.py <userId>\n")
        sys.exit(1)
    
    try:
        user_id = int(sys.argv[1])
    except ValueError:
        sys.stderr.write("Error: userId must be an integer\n")
        sys.exit(1)
    
    # Load transactions (both positive and negative)
    df = load_transactions_from_database(user_id)
    
    if df is None or len(df) == 0:
        # Return empty result (compact JSON)
        print(json.dumps([]))
        sys.exit(0)
    
    # Split into expenses and deposits
    df_expense = df[df['amount'] < 0].copy()
    df_deposit = df[df['amount'] > 0].copy()
    
    all_anomalies = []
    
    # Detect anomalies in expenses
    if len(df_expense) >= 10:
        sys.stderr.write(f"Analyzing {len(df_expense)} expense transactions...\n")
        
        # Identify regular monthly spending (exclude from anomalies)
        regular_expense_ids = identify_regular_monthly_spending(df_expense)
        if len(regular_expense_ids) > 0:
            sys.stderr.write(f"Excluding {len(regular_expense_ids)} regular monthly expenses\n")
        
        # Detect anomalies
        df_expense_anomalies = detect_anomalies(df_expense, transaction_type='expense')
        
        # Filter to only anomalies, exclude regular monthly spending
        expense_anomalies = df_expense_anomalies[
            (df_expense_anomalies['is_anomaly']) & 
            (~df_expense_anomalies['id'].isin(regular_expense_ids))
        ].copy()
        
        if len(expense_anomalies) > 0:
            all_anomalies.append(expense_anomalies)
            sys.stderr.write(f"Found {len(expense_anomalies)} expense anomalies\n")
    else:
        sys.stderr.write(f"Not enough expense transactions ({len(df_expense)} < 10)\n")
    
    # Detect anomalies in deposits
    if len(df_deposit) >= 10:
        sys.stderr.write(f"Analyzing {len(df_deposit)} deposit transactions...\n")
        
        # For deposits, we don't exclude regular monthly patterns (salary is expected)
        # But we can still detect unusual deposits
        df_deposit_anomalies = detect_anomalies(df_deposit, transaction_type='deposit')
        
        # Filter to only anomalies
        deposit_anomalies = df_deposit_anomalies[
            df_deposit_anomalies['is_anomaly']
        ].copy()
        
        if len(deposit_anomalies) > 0:
            all_anomalies.append(deposit_anomalies)
            sys.stderr.write(f"Found {len(deposit_anomalies)} deposit anomalies\n")
    else:
        sys.stderr.write(f"Not enough deposit transactions ({len(df_deposit)} < 10)\n")
    
    # Combine all anomalies
    if len(all_anomalies) > 0:
        try:
            df_all_anomalies = pd.concat(all_anomalies, ignore_index=True)
            # Sort by anomaly_score (most anomalous first - negative scores are more anomalous)
            df_all_anomalies = df_all_anomalies.sort_values('anomaly_score')
            
            # Prepare output
            results = prepare_output(df_all_anomalies)
        except Exception as e:
            sys.stderr.write(f"Error combining anomalies: {e}\n")
            results = []
    else:
        results = []
    
    # Output JSON (compact format, no indentation for easier parsing)
    print(json.dumps(results))
    sys.exit(0)


if __name__ == '__main__':
    main()
