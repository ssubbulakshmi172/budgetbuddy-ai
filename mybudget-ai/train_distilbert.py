#!/usr/bin/env python3
"""
train_distilbert.py

Fine-tune DistilBERT for multi-task transaction categorisation:
- Task 1: Transaction Type (P2C, P2P, P2Business)
- Task 2: Category (Dining, Groceries, etc.)
- Task 3: Intent (optional - purchase, transfer, refund, etc.)

Model: distilbert-base-uncased (FP32)
Framework: Hugging Face Transformers + PyTorch
"""

import os
import json
import logging
import yaml
from datetime import datetime
from typing import Dict, List, Optional, Tuple
import pandas as pd
import numpy as np
from sklearn.model_selection import train_test_split
from sklearn.metrics import classification_report, f1_score, confusion_matrix

# Import preprocessing utilities
try:
    from preprocessing_utils import preprocess_upi_narration
    PREPROCESSING_AVAILABLE = True
except ImportError:
    logging.warning("⚠️ preprocessing_utils.py not found. Preprocessing will be disabled.")
    PREPROCESSING_AVAILABLE = False

import torch
import torch.nn as nn
from torch.utils.data import Dataset, DataLoader
from transformers import (
    DistilBertTokenizer,
    DistilBertForSequenceClassification,
    get_linear_schedule_with_warmup
)
from torch.optim import AdamW

# ---------- Configuration ----------
DATA_FILE = "transactions_distilbert.csv"  # Should have: narration, transaction_type, category, intent
SYNTHETIC_DATA_FILE = "transactions_synthetic.csv"  # Additional synthetic dataset
ADDITIONAL_DATA_FILES = [
    "transactions_maximal.csv",  # Maximal merged dataset (optional)
    # Add more CSV files here to load additional data
]  # Additional CSV files to merge (optional)
USE_SYNTHETIC_DATA = True  # Set to True to merge real + synthetic data
USE_ADDITIONAL_FILES = False  # Set to True to merge additional CSV files
USE_PREPROCESSING = True  # Set to True to preprocess UPI narrations
TAXONOMY_FILE = "categories.yml"  # Fallback taxonomy file
MODELS_DIR = "models"
REPORTS_DIR = "reports"
LOGS_DIR = "logs"

# Database configuration (for taxonomy loading)
DB_HOST = os.getenv("DB_HOST", "localhost")
DB_PORT = int(os.getenv("DB_PORT", "3306"))
DB_NAME = os.getenv("DB_NAME", "budgetbuddy_app")
DB_USER = os.getenv("DB_USER", "root")
DB_PASSWORD = os.getenv("DB_PASSWORD", "")
TAXONOMY_FILTER = "Taxonomy"  # Filter categories_for field

# Model config
MODEL_NAME = "distilbert-base-uncased"
MAX_LENGTH = 128
BATCH_SIZE = 16
EPOCHS = 8  # Increased from 4 to 8 for better convergence
LEARNING_RATE = 1.5e-5  # Slightly lower for more stable training
WARMUP_STEPS = 200  # Increased warmup for better convergence
TEST_SIZE = 0.20
RANDOM_STATE = 42
USE_CLASS_WEIGHTS = True  # Enable class weighting for imbalanced categories

# Task configuration
TASKS = {
    'transaction_type': {
        'labels': ['P2C', 'P2P', 'P2Business'],
        'num_labels': 3,
        'required': True
    },
    'category': {
        'labels': None,  # Will be determined from data
        'num_labels': None,
        'required': True
    },
    'intent': {
        'labels': ['purchase', 'transfer', 'refund', 'subscription', 'bill_payment', 'other'],
        'num_labels': 6,
        'required': False  # Optional task
    }
}

# ---------- Prepare directories ----------
os.makedirs(REPORTS_DIR, exist_ok=True)
os.makedirs(MODELS_DIR, exist_ok=True)
os.makedirs(LOGS_DIR, exist_ok=True)

# ---------- Logging ----------
ts = datetime.now().strftime("%Y%m%d_%H%M%S")
log_file = os.path.join(LOGS_DIR, f"train_distilbert_{ts}.log")
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(levelname)s - %(message)s",
    handlers=[
        logging.FileHandler(log_file),
        logging.StreamHandler()
    ]
)
logging.info("🔰 Starting DistilBERT training pipeline")
logging.info(f"📝 Log file: {log_file}")


# ---------- Load Taxonomy from Database (as per requirements) ----------
def load_taxonomy_from_db(db_host: str = DB_HOST, db_port: int = DB_PORT, 
                          db_name: str = DB_NAME, db_user: str = DB_USER, 
                          db_password: str = DB_PASSWORD, 
                          filter_taxonomy: str = TAXONOMY_FILTER) -> Optional[List[str]]:
    """
    Load category taxonomy from database table `categories_keywords`.
    This is the source of truth for categories (as per requirements).
    
    Args:
        db_host: Database host
        db_port: Database port
        db_name: Database name
        db_user: Database username
        db_password: Database password
        filter_taxonomy: Filter by categories_for field - "Taxonomy" for AI categories, None for all
        
    Returns:
        List of category names sorted alphabetically, or None if database unavailable
    """
    try:
        import mysql.connector
        
        connection = mysql.connector.connect(
            host=db_host,
            port=db_port,
            database=db_name,
            user=db_user,
            password=db_password
        )
        
        cursor = connection.cursor()
        
        # Query distinct category names
        # Get categories where categories_for = 'Taxonomy' or NULL (for AI taxonomy)
        if filter_taxonomy:
            query = """
                SELECT DISTINCT category_name 
                FROM categories_keywords 
                WHERE category_name IS NOT NULL 
                AND (categories_for = %s OR categories_for IS NULL OR categories_for = '')
                ORDER BY category_name
            """
            cursor.execute(query, (filter_taxonomy,))
        else:
            # Get all categories regardless of categories_for
            query = """
                SELECT DISTINCT category_name 
                FROM categories_keywords 
                WHERE category_name IS NOT NULL 
                ORDER BY category_name
            """
            cursor.execute(query)
        
        categories = [row[0] for row in cursor.fetchall()]
        
        cursor.close()
        connection.close()
        
        if categories:
            logging.info(f"✅ Loaded taxonomy from database ({db_name}): {len(categories)} categories")
            logging.info(f"   Categories: {categories}")
            return sorted(categories)
        else:
            logging.warning("⚠️ No categories found in database")
            return None
            
    except ImportError:
        logging.warning("⚠️ mysql-connector-python not installed")
        logging.warning("   Install with: pip install mysql-connector-python")
        return None
    except Exception as e:
        logging.exception(f"⚠️ Failed to load taxonomy from database: {e}")
        return None


def load_taxonomy_from_yaml(taxonomy_file: str) -> Optional[List[str]]:
    """
    Load category taxonomy from YAML configuration file.
    Supports both flat and hierarchical structures with subcategories.
    
    For hierarchical structures, creates flat names like "TopCategory / Subcategory".
    Returns list of category names sorted alphabetically.
    """
    if not os.path.exists(taxonomy_file):
        return None
    
    try:
        with open(taxonomy_file, "r", encoding="utf-8") as f:
            taxonomy = yaml.safe_load(f)
        
        categories = []
        if taxonomy and 'categories' in taxonomy:
            for cat in taxonomy['categories']:
                if isinstance(cat, dict) and 'name' in cat:
                    top_name = cat['name']
                    
                    # Check if this category has subcategories (hierarchical structure)
                    if 'subcategories' in cat and isinstance(cat['subcategories'], list):
                        # Extract subcategories and create flat names
                        for subcat in cat['subcategories']:
                            if isinstance(subcat, dict) and 'name' in subcat:
                                subcat_name = subcat['name']
                                # Create flat category name: "TopCategory / Subcategory"
                                flat_name = f"{top_name} / {subcat_name}"
                                categories.append(flat_name)
                    else:
                        # No subcategories, use top-level name directly
                        categories.append(top_name)
                elif isinstance(cat, str):
                    categories.append(cat)
        
        if categories:
            categories = sorted(categories)
            logging.info(f"✅ Loaded taxonomy from {taxonomy_file}: {len(categories)} categories")
            logging.info(f"   Categories: {categories}")
            return categories
        return None
    except Exception as e:
        logging.exception(f"⚠️ Failed to load taxonomy from {taxonomy_file}: {e}")
        return None


def load_taxonomy(db_first: bool = True, merge_sources: bool = True) -> Optional[List[str]]:
    """
    Load taxonomy from database and/or YAML file.
    Supports both sources working together (as per requirements).
    
    Args:
        db_first: If True, database is primary source. If False, YAML is primary.
        merge_sources: If True, merge categories from both sources. 
                      If False, use primary source only with fallback.
        
    Returns:
        List of category names merged from both sources, or None if neither available
    """
    db_categories = None
    yaml_categories = None
    
    # Load from both sources
    try:
        db_categories = load_taxonomy_from_db()
    except Exception as e:
        logging.debug(f"Database load attempt failed: {e}")
    
    try:
        yaml_categories = load_taxonomy_from_yaml(TAXONOMY_FILE)
    except Exception as e:
        logging.debug(f"YAML load attempt failed: {e}")
    
    # Merge or use fallback based on merge_sources flag
    if merge_sources:
        # Merge categories from both sources (database takes precedence for ordering)
        merged_categories = []
        category_set = set()
        
        if db_categories:
            logging.info(f"📊 Merging {len(db_categories)} categories from database")
            for cat in db_categories:
                if cat not in category_set:
                    merged_categories.append(cat)
                    category_set.add(cat)
        
        if yaml_categories:
            logging.info(f"📄 Merging {len(yaml_categories)} categories from YAML file")
            for cat in yaml_categories:
                if cat not in category_set:
                    merged_categories.append(cat)
                    category_set.add(cat)
                    logging.info(f"   + Added category from YAML: {cat}")
        
        if merged_categories:
            merged_categories = sorted(merged_categories)
            logging.info(f"✅ Merged taxonomy: {len(merged_categories)} total categories")
            logging.info(f"   Sources: Database={db_categories is not None}, YAML={yaml_categories is not None}")
            return merged_categories
    
    # Fallback mode: use primary source, then fallback
    if db_first:
        if db_categories:
            if yaml_categories:
                logging.info("💡 Using database categories (primary). YAML file also available for reference.")
            return db_categories
        if yaml_categories:
            logging.info("💡 Database taxonomy unavailable, using YAML file...")
            return yaml_categories
    else:
        if yaml_categories:
            if db_categories:
                logging.info("💡 Using YAML categories (primary). Database also available for reference.")
            return yaml_categories
        if db_categories:
            logging.info("💡 YAML taxonomy unavailable, using database...")
            return db_categories
    
    logging.warning("⚠️ No taxonomy found in database or YAML file")
    logging.warning("   Will extract categories from dataset instead")
    return None


# ---------- Custom Dataset Class ----------
class TransactionDataset(Dataset):
    """Dataset for multi-task transaction classification"""
    
    def __init__(self, texts: List[str], labels: Dict[str, List[int]], tokenizer, max_length: int = 128):
        self.texts = texts
        self.labels = labels
        self.tokenizer = tokenizer
        self.max_length = max_length
    
    def __len__(self):
        return len(self.texts)
    
    def __getitem__(self, idx):
        text = str(self.texts[idx])
        encoding = self.tokenizer(
            text,
            truncation=True,
            padding='max_length',
            max_length=self.max_length,
            return_tensors='pt'
        )
        
        item = {
            'input_ids': encoding['input_ids'].flatten(),
            'attention_mask': encoding['attention_mask'].flatten(),
            'text': text
        }
        
        # Add labels for each task
        for task_name in self.labels.keys():
            item[f'{task_name}_label'] = torch.tensor(self.labels[task_name][idx], dtype=torch.long)
        
        return item


# ---------- Multi-Task Model ----------
class MultiTaskDistilBERT(nn.Module):
    """DistilBERT with multiple classification heads"""
    
    def __init__(self, base_model_name: str, tasks: Dict):
        super(MultiTaskDistilBERT, self).__init__()
        
        # Load base DistilBERT model
        self.bert = DistilBertForSequenceClassification.from_pretrained(
            base_model_name,
            num_labels=768  # Hidden size for feature extraction
        )
        
        # Remove the classification head from base model
        self.bert.classifier = nn.Identity()
        
        # Create separate heads for each task
        self.task_heads = nn.ModuleDict()
        for task_name, task_config in tasks.items():
            if task_config['num_labels']:
                self.task_heads[task_name] = nn.Sequential(
                    nn.Dropout(0.1),
                    nn.Linear(768, task_config['num_labels'])
                )
    
    def forward(self, input_ids, attention_mask, task_labels: Optional[Dict] = None):
        # Get BERT embeddings
        outputs = self.bert(input_ids=input_ids, attention_mask=attention_mask)
        pooled_output = outputs.logits  # [batch_size, 768]
        
        # Apply each task head
        predictions = {}
        losses = []
        
        for task_name, head in self.task_heads.items():
            logits = head(pooled_output)
            predictions[task_name] = logits
            
            # Calculate loss if labels provided
            if task_labels and f'{task_name}_label' in task_labels:
                # Use class weights for category task if available
                if task_name == 'category' and hasattr(self, 'category_class_weights'):
                    loss_fn = nn.CrossEntropyLoss(weight=self.category_class_weights)
                else:
                    loss_fn = nn.CrossEntropyLoss()
                loss = loss_fn(logits, task_labels[f'{task_name}_label'])
                losses.append(loss)
        
        # Combine losses (weighted average)
        total_loss = sum(losses) / len(losses) if losses else None
        
        return {
            'predictions': predictions,
            'loss': total_loss,
            'pooled_output': pooled_output
        }


# ---------- Load and Merge Multiple Datasets ----------
def load_and_merge_datasets(real_data_file: str, synthetic_data_file: Optional[str] = None, 
                            use_synthetic: bool = True) -> pd.DataFrame:
    """
    Load and merge real and synthetic datasets.
    Automatically discovers and loads all CSV files in the current directory.
    
    Args:
        real_data_file: Path to real transaction data CSV (required)
        synthetic_data_file: Path to synthetic transaction data CSV (legacy, kept for compatibility)
        use_synthetic: Whether to include synthetic data (legacy, kept for compatibility)
        
    Returns:
        Merged DataFrame with normalized columns
    """
    datasets = []
    loaded_files = set()
    
    # Load real dataset (required)
    if not os.path.exists(real_data_file):
        raise FileNotFoundError(f"Real data file not found: {real_data_file}")
    
    df_real = pd.read_csv(real_data_file)
    logging.info(f"✅ Loaded real dataset: {len(df_real)} rows from {real_data_file}")
    print(f"   ✅ Loaded real dataset: {len(df_real)} rows from {os.path.basename(real_data_file)}")
    
    # Normalize column names: handle both 'narration' and 'description'
    if 'description' in df_real.columns and 'narration' not in df_real.columns:
        df_real = df_real.rename(columns={'description': 'narration'})
    
    datasets.append(df_real)
    loaded_files.add(os.path.abspath(real_data_file))
    
    # Auto-discover and load ALL transaction CSV files in current directory
    # This automatically includes: transactions_balanced.csv, transactions_mapped.csv, 
    # transactions_maximal.csv, transactions_synthetic.csv, transactions_retails_synthetic.csv, etc.
    current_dir = os.path.dirname(os.path.abspath(real_data_file)) or '.'
    csv_files = sorted([f for f in os.listdir(current_dir) 
                 if f.endswith('.csv') and f.startswith('transactions_')])
    
    logging.info(f"🔍 Auto-discovered {len(csv_files)} transaction CSV files: {csv_files}")
    print(f"   🔍 Auto-discovered {len(csv_files)} transaction CSV files")
    
    for csv_file in csv_files:
        file_path = os.path.join(current_dir, csv_file)
        abs_path = os.path.abspath(file_path)
        
        # Skip if already loaded (real_data_file)
        if abs_path in loaded_files:
            continue
        
        # Skip if file doesn't exist
        if not os.path.exists(file_path):
            continue
        
        try:
            df = pd.read_csv(file_path)
            logging.info(f"✅ Auto-loaded dataset: {len(df)} rows from {csv_file}")
            print(f"   ✅ Auto-loaded dataset: {len(df)} rows from {csv_file}")
            
            # Normalize column names
            if 'description' in df.columns and 'narration' not in df.columns:
                df = df.rename(columns={'description': 'narration'})
            
            # Add missing columns with defaults
            if 'transaction_type' not in df.columns:
                df['transaction_type'] = 'P2C'
            if 'intent' not in df.columns:
                df['intent'] = 'purchase'
            if 'category' not in df.columns:
                df['category'] = 'Other'
            
            datasets.append(df)
            loaded_files.add(abs_path)
        except Exception as e:
            logging.warning(f"⚠️ Failed to load {csv_file}: {e}")
            print(f"   ⚠️ Skipped {csv_file}: {str(e)}")
    
    # Legacy: Load synthetic dataset if specified (for backward compatibility)
    if use_synthetic and synthetic_data_file and os.path.exists(synthetic_data_file):
        abs_path = os.path.abspath(synthetic_data_file)
        if abs_path not in loaded_files:
            df_synthetic = pd.read_csv(synthetic_data_file)
            logging.info(f"✅ Loaded synthetic dataset: {len(df_synthetic)} rows from {synthetic_data_file}")
            print(f"   ✅ Loaded synthetic dataset: {len(df_synthetic)} rows from {os.path.basename(synthetic_data_file)}")
            
            if 'description' in df_synthetic.columns:
                df_synthetic = df_synthetic.rename(columns={'description': 'narration'})
            if 'transaction_type' not in df_synthetic.columns:
                df_synthetic['transaction_type'] = 'P2C'
            if 'intent' not in df_synthetic.columns:
                df_synthetic['intent'] = 'purchase'
            
            datasets.append(df_synthetic)
            loaded_files.add(abs_path)
    
    # Merge datasets
    if len(datasets) == 1:
        df_merged = datasets[0]
    else:
        df_merged = pd.concat(datasets, ignore_index=True)
    
    # Remove duplicates based on narration
    before_dedup = len(df_merged)
    df_merged = df_merged.drop_duplicates(subset=['narration'], keep='first')
    after_dedup = len(df_merged)
    if before_dedup > after_dedup:
        logging.info(f"📊 Removed {before_dedup - after_dedup} duplicate rows")
        print(f"   📊 Removed {before_dedup - after_dedup} duplicate rows")
    
    logging.info(f"✅ Merged datasets: {len(df_merged)} total rows from {len(datasets)} file(s)")
    print(f"   📊 Merged {len(datasets)} dataset(s): {len(df_merged)} total rows")
    
    return df_merged


# ---------- Load User Corrections ----------
def load_corrections(corrections_file: str = CORRECTIONS_FILE) -> Optional[pd.DataFrame]:
    """
    Load user corrections from CSV file.
    
    Expected format:
    - narration: Transaction description
    - category: User-corrected category
    - predicted_category: Original AI prediction (optional)
    - confidence: AI confidence score (optional)
    
    Returns:
        DataFrame with corrections, or None if file doesn't exist
    """
    if not os.path.exists(corrections_file):
        logging.info(f"💡 No corrections file found: {corrections_file} (skipping)")
        return None
    
    try:
        df = pd.read_csv(corrections_file)
        logging.info(f"✅ Loaded {len(df)} user corrections from {corrections_file}")
        print(f"   ✅ Loaded {len(df)} user corrections")
        
        # Validate required columns
        if 'narration' not in df.columns or 'category' not in df.columns:
            logging.warning(f"⚠️ Corrections file missing required columns (narration, category)")
            return None
        
        # Clean and validate
        df = df.dropna(subset=['narration', 'category'])
        df = df[df['narration'].str.len() >= 5]  # Filter very short narrations
        df = df.drop_duplicates(subset=['narration', 'category'])
        
        # Ensure required columns exist
        if 'transaction_type' not in df.columns:
            df['transaction_type'] = 'P2C'  # Default
        if 'intent' not in df.columns:
            df['intent'] = 'purchase'  # Default
        
        logging.info(f"✅ Validated: {len(df)} corrections after cleaning")
        print(f"   ✅ Validated: {len(df)} corrections")
        
        return df
        
    except Exception as e:
        logging.warning(f"⚠️ Failed to load corrections: {e}")
        return None


# ---------- Load and Prepare Data ----------
def load_and_prepare_data(data_file: str, synthetic_file: Optional[str] = None, 
                          use_synthetic: bool = USE_SYNTHETIC_DATA,
                          use_preprocessing: bool = USE_PREPROCESSING,
                          use_corrections: bool = USE_CORRECTIONS) -> Tuple[pd.DataFrame, Dict]:
    """
    Load dataset(s) and prepare task labels with optional preprocessing and corrections.
    
    Args:
        data_file: Path to main data file
        synthetic_file: Path to synthetic data file (legacy)
        use_synthetic: Whether to include synthetic data
        use_preprocessing: Whether to apply UPI preprocessing
        use_corrections: Whether to include user corrections if available
    """
    logging.info(f"📥 Loading data from {data_file}")
    if use_synthetic and synthetic_file:
        logging.info(f"   Will also load synthetic data from {synthetic_file}")
    print(f"\n📥 Loading dataset(s)...")
    
    # Load and merge datasets
    df = load_and_merge_datasets(data_file, synthetic_file, use_synthetic)
    
    # Optionally load and merge user corrections
    if use_corrections:
        corrections_df = load_corrections()
        if corrections_df is not None and len(corrections_df) > 0:
            logging.info(f"🔄 Merging {len(corrections_df)} corrections into training data...")
            print(f"   🔄 Merging {len(corrections_df)} corrections into training data...")
            
            # Merge corrections with training data
            # Corrections are user-validated, so we prioritize them
            df = pd.concat([df, corrections_df], ignore_index=True)
            
            # Remove duplicates (keep corrections if both exist)
            # If same narration appears in both, keep the one with user correction
            df = df.drop_duplicates(subset=['narration'], keep='last')
            
            logging.info(f"✅ Combined dataset: {len(df)} total rows (including corrections)")
            print(f"   ✅ Combined dataset: {len(df)} total rows")
    
    # Validate required columns
    required_cols = ['narration']
    missing_cols = [col for col in required_cols if col not in df.columns]
    if missing_cols:
        raise ValueError(f"Missing required columns: {missing_cols}")
    
    # Basic cleaning
    df = df.dropna(subset=['narration'])
    df['narration'] = df['narration'].astype(str)
    df = df[df['narration'].str.len() > 0]
    
    # Apply UPI preprocessing if enabled
    if use_preprocessing and PREPROCESSING_AVAILABLE:
        logging.info("🔧 Applying UPI preprocessing to narrations...")
        df['narration'] = df['narration'].apply(preprocess_upi_narration)
        # Remove rows that became empty after preprocessing
        df = df[df['narration'].str.len() > 0]
        logging.info(f"✅ After preprocessing: {len(df)} rows")
        print(f"   ✅ Applied UPI preprocessing: {len(df)} rows remaining")
    else:
        logging.info(f"✅ After cleaning: {len(df)} rows")
        print(f"   ✅ After cleaning: {len(df)} rows")
    
    # Prepare task labels
    label_encoders = {}
    encoded_labels = {}
    
    # Task 1: Transaction Type
    if 'transaction_type' in df.columns:
        df['transaction_type'] = df['transaction_type'].fillna('P2C')  # Default
        unique_types = sorted(df['transaction_type'].unique())
        TASKS['transaction_type']['labels'] = unique_types
        TASKS['transaction_type']['num_labels'] = len(unique_types)
        
        label_encoders['transaction_type'] = {label: idx for idx, label in enumerate(unique_types)}
        encoded_labels['transaction_type'] = df['transaction_type'].map(label_encoders['transaction_type']).values
        
        logging.info(f"✅ Transaction Type: {len(unique_types)} classes - {unique_types}")
        logging.info(f"   Distribution: {df['transaction_type'].value_counts().to_dict()}")
    else:
        logging.warning("⚠️ 'transaction_type' column not found, skipping this task")
        TASKS['transaction_type']['required'] = False
    
    # Task 2: Category - Use taxonomy from database AND YAML file (as per requirements)
    # Load and merge categories from both database and YAML file
    # Database categories take precedence, YAML adds any additional categories
    taxonomy_categories = load_taxonomy(db_first=True, merge_sources=True)
    
    if 'category' in df.columns:
        df['category'] = df['category'].fillna('Other')
        
        # Clean category names (strip whitespace, handle case)
        df['category'] = df['category'].astype(str).str.strip()
        
        if taxonomy_categories:
            # Use taxonomy from database as source of truth
            # Map dataset categories to taxonomy categories
            dataset_categories = set(df['category'].unique())
            taxonomy_set = set(taxonomy_categories)
            
            # Check for categories in dataset that aren't in taxonomy
            missing_in_taxonomy = dataset_categories - taxonomy_set
            if missing_in_taxonomy:
                logging.warning(f"⚠️ Dataset contains categories not in taxonomy: {missing_in_taxonomy}")
                logging.warning("   These will be mapped to 'Other' if present, or filtered out")
            
            # Add "Other" to taxonomy if it exists in dataset
            if 'Other' in dataset_categories and 'Other' not in taxonomy_set:
                taxonomy_categories.append('Other')
                taxonomy_set.add('Other')
            
            # Filter dataset to only include taxonomy categories (or map unknown to "Other")
            if 'Other' in taxonomy_categories:
                df['category'] = df['category'].apply(
                    lambda x: x if x in taxonomy_set else 'Other'
                )
            else:
                # Filter out categories not in taxonomy
                before_filter = len(df)
                df = df[df['category'].isin(taxonomy_set)]
                after_filter = len(df)
                if before_filter > after_filter:
                    logging.warning(f"⚠️ Filtered out {before_filter - after_filter} rows with non-taxonomy categories")
            
            # Use taxonomy categories as the definitive list
            taxonomy_categories = sorted(set(taxonomy_categories))
            
            # Filter out old top-level categories that have 0 samples (Charity, Dining, Entertainment, etc.)
            # These are legacy categories from database that aren't in consolidated taxonomy
            category_counts = df['category'].value_counts()
            active_categories = [cat for cat in taxonomy_categories if cat in category_counts.index and category_counts[cat] > 0]
            
            # Keep only categories that exist in the dataset
            taxonomy_categories = [cat for cat in taxonomy_categories if cat in active_categories or cat not in ['Charity', 'Dining', 'Entertainment', 'Fitness', 'Groceries', 'Healthcare', 'Shopping', 'Transport', 'Travel', 'Utilities']]
            
            TASKS['category']['labels'] = taxonomy_categories
            TASKS['category']['num_labels'] = len(taxonomy_categories)
            
            logging.info(f"✅ Category Taxonomy: {len(taxonomy_categories)} classes (filtered to active categories)")
            logging.info(f"   Categories: {taxonomy_categories}")
            
            # Create label encoder based on taxonomy
            label_encoders['category'] = {label: idx for idx, label in enumerate(taxonomy_categories)}
            
            # Map dataset categories to encoded labels
            encoded_labels['category'] = df['category'].map(label_encoders['category'])
            
            # Handle any NaN values (shouldn't happen, but just in case)
            nan_mask = encoded_labels['category'].isna()
            if nan_mask.any():
                logging.warning(f"⚠️ Found {nan_mask.sum()} NaN category labels, mapping to first category")
                first_idx = 0
                encoded_labels['category'] = encoded_labels['category'].fillna(first_idx)
            
            # Convert to numpy array
            encoded_labels['category'] = encoded_labels['category'].astype(int).values
            
            logging.info(f"✅ Category distribution in dataset:")
            logging.info(f"   {df['category'].value_counts().to_dict()}")
        else:
            # Fallback: extract categories from dataset if taxonomy not available
            logging.warning("⚠️ Taxonomy not available, extracting categories from dataset")
            unique_categories = sorted(df['category'].unique())
            TASKS['category']['labels'] = unique_categories
            TASKS['category']['num_labels'] = len(unique_categories)
            
            label_encoders['category'] = {label: idx for idx, label in enumerate(unique_categories)}
            encoded_labels['category'] = df['category'].map(label_encoders['category']).values
            
            logging.info(f"✅ Category: {len(unique_categories)} classes (from dataset)")
            logging.info(f"   Distribution: {df['category'].value_counts().to_dict()}")
    else:
        raise ValueError("'category' column is required but not found in dataset")
    
    # Task 3: Intent (optional, infer if not present)
    if 'intent' in df.columns:
        unique_intents = sorted(df['intent'].unique())
        TASKS['intent']['labels'] = unique_intents
        TASKS['intent']['num_labels'] = len(unique_intents)
        TASKS['intent']['required'] = True
        
        label_encoders['intent'] = {label: idx for idx, label in enumerate(unique_intents)}
        encoded_labels['intent'] = df['intent'].map(label_encoders['intent']).values
        
        logging.info(f"✅ Intent: {len(unique_intents)} classes - {unique_intents}")
    else:
        # Infer intent from narration (simple keyword-based)
        logging.info("⚠️ 'intent' column not found, inferring from narration...")
        intents = []
        for narration in df['narration']:
            narration_lower = str(narration).lower()
            if any(kw in narration_lower for kw in ['transfer', 'sent', 'received']):
                intent = 'transfer'
            elif any(kw in narration_lower for kw in ['refund', 'return']):
                intent = 'refund'
            elif any(kw in narration_lower for kw in ['subscription', 'monthly', 'recurring']):
                intent = 'subscription'
            elif any(kw in narration_lower for kw in ['bill', 'utility', 'recharge']):
                intent = 'bill_payment'
            elif any(kw in narration_lower for kw in ['purchase', 'buy', 'payment']):
                intent = 'purchase'
            else:
                intent = 'other'
            intents.append(intent)
        
        df['intent'] = intents
        TASKS['intent']['required'] = True  # Now we have it
        label_encoders['intent'] = {label: idx for idx, label in enumerate(TASKS['intent']['labels'])}
        encoded_labels['intent'] = df['intent'].map(label_encoders['intent']).values
        
        logging.info(f"✅ Intent (inferred): {len(TASKS['intent']['labels'])} classes")
        logging.info(f"   Distribution: {df['intent'].value_counts().to_dict()}")
    
    # Remove tasks that are not required
    tasks_to_use = {k: v for k, v in TASKS.items() if v.get('required', False) and v.get('num_labels')}
    encoded_labels = {k: v for k, v in encoded_labels.items() if k in tasks_to_use}
    
    logging.info(f"\n📊 Tasks to train: {list(tasks_to_use.keys())}")
    
    return df, label_encoders, encoded_labels, tasks_to_use


# ---------- Training Function ----------
def train_epoch(model, dataloader, optimizer, scheduler, device, tasks, epoch_num, total_epochs):
    """Train for one epoch"""
    model.train()
    total_loss = 0
    num_batches = len(dataloader)
    
    for batch_idx, batch in enumerate(dataloader):
        input_ids = batch['input_ids'].to(device)
        attention_mask = batch['attention_mask'].to(device)
        
        # Prepare task labels
        task_labels = {}
        for task_name in tasks.keys():
            if f'{task_name}_label' in batch:
                task_labels[f'{task_name}_label'] = batch[f'{task_name}_label'].to(device)
        
        optimizer.zero_grad()
        
        outputs = model(input_ids=input_ids, attention_mask=attention_mask, task_labels=task_labels)
        loss = outputs['loss']
        
        loss.backward()
        torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
        optimizer.step()
        scheduler.step()
        
        total_loss += loss.item()
        
        # Log progress every 10% of batches
        if (batch_idx + 1) % max(1, num_batches // 10) == 0 or (batch_idx + 1) == num_batches:
            progress = (batch_idx + 1) / num_batches * 100
            avg_loss = total_loss / (batch_idx + 1)
            logging.info(f"   Epoch {epoch_num}/{total_epochs} - Batch {batch_idx + 1}/{num_batches} ({progress:.0f}%) - Loss: {avg_loss:.4f}")
            print(f"   Progress: {progress:.0f}% | Loss: {avg_loss:.4f}", end='\r')
    
    print()  # New line after progress
    return total_loss / len(dataloader)


# ---------- Evaluation Function ----------
def evaluate(model, dataloader, device, tasks, label_encoders, show_progress=True):
    """Evaluate model on validation/test set"""
    model.eval()
    predictions = {task: [] for task in tasks.keys()}
    true_labels = {task: [] for task in tasks.keys()}
    total_loss = 0
    num_batches = len(dataloader)
    
    with torch.no_grad():
        for batch_idx, batch in enumerate(dataloader):
            input_ids = batch['input_ids'].to(device)
            attention_mask = batch['attention_mask'].to(device)
            
            task_labels = {}
            batch_size = input_ids.shape[0]
            
            # Collect labels for all tasks
            for task_name in tasks.keys():
                if f'{task_name}_label' in batch:
                    label_tensor = batch[f'{task_name}_label'].to(device)
                    task_labels[f'{task_name}_label'] = label_tensor
                    true_labels[task_name].extend(label_tensor.cpu().numpy())
            
            outputs = model(input_ids=input_ids, attention_mask=attention_mask, task_labels=task_labels)
            
            # Handle loss (can be None if no labels provided)
            if outputs['loss'] is not None:
                total_loss += outputs['loss'].item()
            
            # Get predictions for all tasks
            for task_name, logits in outputs['predictions'].items():
                preds = torch.argmax(logits, dim=1).cpu().numpy()
                predictions[task_name].extend(preds)
                
                # If true_labels missing for this batch, try to get from batch
                current_pred_len = len(predictions[task_name])
                current_label_len = len(true_labels[task_name])
                
                # If predictions outpace labels, try to sync
                if current_pred_len > current_label_len:
                    if f'{task_name}_label' in batch:
                        missing_count = current_pred_len - current_label_len
                        label_values = batch[f'{task_name}_label'].cpu().numpy()
                        if len(label_values) >= missing_count:
                            true_labels[task_name].extend(label_values[-missing_count:])
            
            # Show progress during evaluation
            if show_progress and (batch_idx + 1) % max(1, num_batches // 5) == 0:
                progress = (batch_idx + 1) / num_batches * 100
                print(f"   Evaluating: {progress:.0f}%", end='\r')
    
    # Calculate metrics for each task
    metrics = {}
    for task_name in tasks.keys():
        if len(predictions[task_name]) > 0 and len(true_labels[task_name]) > 0:
            # Ensure same length
            min_len = min(len(predictions[task_name]), len(true_labels[task_name]))
            if min_len > 0:
                y_true = np.array(true_labels[task_name][:min_len])
                y_pred = np.array(predictions[task_name][:min_len])
                
                # F1 scores
                macro_f1 = f1_score(y_true, y_pred, average='macro', zero_division=0)
                weighted_f1 = f1_score(y_true, y_pred, average='weighted', zero_division=0)
                
                # Classification report
                label_names = tasks[task_name]['labels']
                # Get all possible labels (indices) to ensure all classes are included
                all_labels = list(range(len(label_names)))
                report = classification_report(
                    y_true, y_pred,
                    labels=all_labels,
                    target_names=label_names,
                    output_dict=True,
                    zero_division=0
                )
                
                metrics[task_name] = {
                    'macro_f1': macro_f1,
                    'weighted_f1': weighted_f1,
                    'classification_report': report,
                    'confusion_matrix': confusion_matrix(y_true, y_pred, labels=range(len(label_names)))
                }
            else:
                logging.warning(f"⚠️ No valid predictions for task: {task_name}")
        else:
            logging.warning(f"⚠️ Missing predictions or labels for task: {task_name} (preds: {len(predictions[task_name])}, labels: {len(true_labels[task_name])})")
    
    avg_loss = total_loss / len(dataloader) if total_loss > 0 else None
    
    return metrics, avg_loss


# ---------- Main Training Pipeline ----------
def main():
    """Main training function"""
    logging.info("=" * 60)
    logging.info("🚀 DistilBERT Multi-Task Training Pipeline")
    logging.info("=" * 60)
    
    # Device
    device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
    logging.info(f"🖥️  Device: {device}")
    
    # Load data (including corrections if available)
    df, label_encoders, encoded_labels, tasks_to_use = load_and_prepare_data(
        DATA_FILE, 
        synthetic_file=SYNTHETIC_DATA_FILE if USE_SYNTHETIC_DATA else None,
        use_synthetic=USE_SYNTHETIC_DATA,
        use_preprocessing=USE_PREPROCESSING,
        use_corrections=USE_CORRECTIONS
    )
    
    # Split data
    texts = df['narration'].tolist()
    train_texts, test_texts = train_test_split(
        texts, test_size=TEST_SIZE, random_state=RANDOM_STATE
    )
    
    # Split labels accordingly
    train_labels = {}
    test_labels = {}
    for task_name in tasks_to_use.keys():
        train_idx, test_idx = train_test_split(
            range(len(texts)), test_size=TEST_SIZE, random_state=RANDOM_STATE
        )
        train_labels[task_name] = [encoded_labels[task_name][i] for i in train_idx]
        test_labels[task_name] = [encoded_labels[task_name][i] for i in test_idx]
    
    logging.info(f"📚 Train: {len(train_texts)}, Test: {len(test_texts)}")
    
    # Tokenizer
    logging.info(f"🔤 Loading tokenizer: {MODEL_NAME}")
    tokenizer = DistilBertTokenizer.from_pretrained(MODEL_NAME)
    
    # Datasets
    train_dataset = TransactionDataset(train_texts, train_labels, tokenizer, MAX_LENGTH)
    test_dataset = TransactionDataset(test_texts, test_labels, tokenizer, MAX_LENGTH)
    
    train_loader = DataLoader(train_dataset, batch_size=BATCH_SIZE, shuffle=True)
    test_loader = DataLoader(test_dataset, batch_size=BATCH_SIZE, shuffle=False)
    
    # Model
    logging.info(f"🤖 Initializing model: {MODEL_NAME}")
    print(f"\n🤖 Initializing model: {MODEL_NAME}...")
    model = MultiTaskDistilBERT(MODEL_NAME, tasks_to_use)
    model.to(device)
    print(f"   ✅ Model initialized on {device}")
    
    # Count parameters
    total_params = sum(p.numel() for p in model.parameters())
    trainable_params = sum(p.numel() for p in model.parameters() if p.requires_grad)
    logging.info(f"   Total parameters: {total_params:,}")
    logging.info(f"   Trainable parameters: {trainable_params:,}")
    print(f"   Parameters: {trainable_params:,} trainable / {total_params:,} total")
    
    # Optimizer and scheduler
    print(f"\n⚙️  Setting up optimizer and scheduler...")
    optimizer = AdamW(model.parameters(), lr=LEARNING_RATE, weight_decay=0.01)
    total_steps = len(train_loader) * EPOCHS
    scheduler = get_linear_schedule_with_warmup(
        optimizer, num_warmup_steps=WARMUP_STEPS, num_training_steps=total_steps
    )
    print(f"   ✅ Optimizer: AdamW (lr={LEARNING_RATE}, weight_decay=0.01)")
    print(f"   ✅ Scheduler: Linear warmup ({WARMUP_STEPS} steps) → {total_steps} total steps")
    
    # Calculate class weights for category task if enabled
    if USE_CLASS_WEIGHTS and 'category' in tasks_to_use:
        from sklearn.utils.class_weight import compute_class_weight
        category_labels = train_labels['category']
        unique_labels = np.unique(category_labels)
        class_weights = compute_class_weight('balanced', classes=unique_labels, y=category_labels)
        class_weight_dict = {int(label): float(weight) for label, weight in zip(unique_labels, class_weights)}
        logging.info(f"📊 Computed class weights for {len(class_weight_dict)} categories")
        logging.info(f"   Weight range: {min(class_weights):.3f} - {max(class_weights):.3f}")
        # Store in model for use in loss calculation
        model.category_class_weights = torch.tensor([class_weight_dict.get(i, 1.0) for i in range(len(tasks_to_use['category']['labels']))]).to(device)
        print(f"   ✅ Class weights enabled for category classification")
    
    # Training loop
    logging.info(f"\n🏋️  Starting training ({EPOCHS} epochs)...")
    logging.info(f"   Training batches: {len(train_loader)}")
    logging.info(f"   Test batches: {len(test_loader)}")
    print(f"\n🏋️  Training Progress:")
    best_test_f1 = 0
    
    for epoch in range(EPOCHS):
        logging.info(f"\n{'='*60}")
        logging.info(f"Epoch {epoch + 1}/{EPOCHS}")
        logging.info(f"{'='*60}")
        print(f"\n{'='*60}")
        print(f"Epoch {epoch + 1}/{EPOCHS}")
        print(f"{'='*60}")
        
        # Train
        print(f"📚 Training...")
        train_loss = train_epoch(model, train_loader, optimizer, scheduler, device, tasks_to_use, epoch + 1, EPOCHS)
        logging.info(f"📉 Train Loss: {train_loss:.4f}")
        print(f"   ✅ Training complete - Loss: {train_loss:.4f}")
        
        # Evaluate
        print(f"📊 Evaluating...")
        test_metrics, test_loss = evaluate(model, test_loader, device, tasks_to_use, label_encoders, show_progress=True)
        print()  # New line after evaluation progress
        
        if test_loss:
            logging.info(f"📉 Test Loss: {test_loss:.4f}")
            print(f"   Test Loss: {test_loss:.4f}")
        
        # Log metrics for each task
        print(f"\n📈 Task Results:")
        for task_name, metrics_dict in test_metrics.items():
            macro_f1 = metrics_dict['macro_f1']
            weighted_f1 = metrics_dict['weighted_f1']
            logging.info(f"\n📊 Task: {task_name.upper()}")
            logging.info(f"   Macro F1: {macro_f1:.4f}")
            logging.info(f"   Weighted F1: {weighted_f1:.4f}")
            print(f"   {task_name.upper()}: Macro F1 = {macro_f1:.4f}, Weighted F1 = {weighted_f1:.4f}")
            
            # Track best F1 (use category task for model selection)
            if task_name == 'category' and macro_f1 > best_test_f1:
                best_test_f1 = macro_f1
                print(f"   🎯 New best category F1: {best_test_f1:.4f}")
    
    # Final evaluation
    logging.info(f"\n{'='*60}")
    logging.info("📈 Final Evaluation")
    logging.info(f"{'='*60}")
    
    final_metrics, _ = evaluate(model, test_loader, device, tasks_to_use, label_encoders)
    
    # Save results
    report_path = os.path.join(REPORTS_DIR, f"distilbert_metrics_{ts}.json")
    results = {
        'model': MODEL_NAME,
        'timestamp': ts,
        'tasks': {task: {'labels': tasks_to_use[task]['labels'], 'num_labels': tasks_to_use[task]['num_labels']} 
                  for task in tasks_to_use.keys()},
        'metrics': {}
    }
    
    for task_name, metrics_dict in final_metrics.items():
        results['metrics'][task_name] = {
            'macro_f1': float(metrics_dict['macro_f1']),
            'weighted_f1': float(metrics_dict['weighted_f1']),
            'classification_report': metrics_dict['classification_report'],
            'confusion_matrix': metrics_dict['confusion_matrix'].tolist()
        }
        
        # Log per-class metrics
        logging.info(f"\n📊 {task_name.upper()} - Per-Class F1:")
        report = metrics_dict['classification_report']
        for label in tasks_to_use[task_name]['labels']:
            if label in report:
                f1 = report[label].get('f1-score', 0)
                logging.info(f"   {label}: {f1:.4f}")
    
    with open(report_path, 'w') as f:
        json.dump(results, f, indent=2)
    logging.info(f"💾 Saved metrics to {report_path}")
    
    # Save model
    model_path = os.path.join(MODELS_DIR, f"distilbert_multitask_{ts}")
    os.makedirs(model_path, exist_ok=True)
    
    model.bert.save_pretrained(model_path)
    tokenizer.save_pretrained(model_path)
    
    # Save task heads separately
    torch.save(model.task_heads.state_dict(), os.path.join(model_path, 'task_heads.pt'))
    
    # Save label encoders and config
    config = {
        'model_name': MODEL_NAME,
        'tasks': {task: {'labels': tasks_to_use[task]['labels'], 'num_labels': tasks_to_use[task]['num_labels']} 
                  for task in tasks_to_use.keys()},
        'label_encoders': {task: {k: int(v) for k, v in encoder.items()} 
                          for task, encoder in label_encoders.items()},
        'max_length': MAX_LENGTH
    }
    
    with open(os.path.join(model_path, 'config.json'), 'w') as f:
        json.dump(config, f, indent=2)
    
    # Also save as "latest"
    latest_path = os.path.join(MODELS_DIR, "distilbert_multitask_latest")
    import shutil
    if os.path.exists(latest_path):
        shutil.rmtree(latest_path)
    shutil.copytree(model_path, latest_path)
    
    logging.info(f"💾 Model saved to {model_path}")
    logging.info(f"💾 Latest model copied to {latest_path}")
    
    logging.info("\n✅ Training complete!")
    logging.info(f"📝 Full log: {log_file}")


if __name__ == "__main__":
    main()

