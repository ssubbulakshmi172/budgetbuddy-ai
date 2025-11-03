#!/usr/bin/env python3
"""
distilbert_inference.py

Standalone DistilBERT inference module for multi-task predictions.
Used by inference_local.py
"""

import os
import json
import logging
import warnings
import re
import yaml
import torch
import torch.nn as nn
from typing import Dict, Optional, List, Tuple
from transformers import (
    DistilBertTokenizer,
    DistilBertForSequenceClassification
)

# Suppress warnings
warnings.filterwarnings('ignore')
os.environ['PYTHONWARNINGS'] = 'ignore'

# Configuration - use relative path from script location
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
MODEL_PATH = os.path.join(SCRIPT_DIR, "models", "distilbert_multitask_latest")
CATEGORIES_FILE = os.path.join(SCRIPT_DIR, "categories.yml")

logging.basicConfig(level=logging.WARNING)


def clean_text(text: str) -> str:
    """Clean transaction narration text"""
    if not text:
        return ""
    # Use preprocessing utils if available, preserving P2P clues
    try:
        from preprocessing_utils import preprocess_upi_narration
        return preprocess_upi_narration(text, preserve_p2p_clues=True)
    except ImportError:
        return str(text).strip()


def load_keyword_mappings(categories_file: str = CATEGORIES_FILE) -> List[Tuple[str, str]]:
    """
    Load keyword-to-category mappings from categories.yml
    
    Returns:
        List of tuples: (keyword, category_name) where category_name is in format "TopCategory / Subcategory"
    """
    keyword_mappings = []
    
    if not os.path.exists(categories_file):
        logging.warning(f"Categories file not found: {categories_file}")
        return keyword_mappings
    
    try:
        with open(categories_file, 'r', encoding='utf-8') as f:
            categories_config = yaml.safe_load(f)
        
        if not categories_config or 'categories' not in categories_config:
            return keyword_mappings
        
        for cat in categories_config['categories']:
            if not isinstance(cat, dict) or 'name' not in cat:
                continue
            
            top_name = cat['name']
            
            # Check if this category has subcategories
            if 'subcategories' in cat and isinstance(cat['subcategories'], list):
                for subcat in cat['subcategories']:
                    if not isinstance(subcat, dict) or 'name' not in subcat:
                        continue
                    
                    subcat_name = subcat['name']
                    full_category_name = f"{top_name} / {subcat_name}"
                    
                    # Extract keywords
                    keywords = subcat.get('keywords', [])
                    if isinstance(keywords, list):
                        for keyword in keywords:
                            if keyword and isinstance(keyword, str):
                                keyword_mappings.append((keyword.lower().strip(), full_category_name))
            else:
                # Top-level category without subcategories (less common)
                full_category_name = top_name
                keywords = cat.get('keywords', [])
                if isinstance(keywords, list):
                    for keyword in keywords:
                        if keyword and isinstance(keyword, str):
                            keyword_mappings.append((keyword.lower().strip(), full_category_name))
        
        logging.info(f"Loaded {len(keyword_mappings)} keyword mappings from {categories_file}")
        
    except Exception as e:
        logging.warning(f"Error loading keyword mappings from {categories_file}: {e}")
    
    return keyword_mappings


def match_keywords(narration: str, keyword_mappings: List[Tuple[str, str]]) -> Optional[str]:
    """
    Match narration against keywords and return category if match found.
    Keyword matching takes precedence over model predictions.
    
    Args:
        narration: Transaction narration text (can be original or cleaned)
        keyword_mappings: List of (keyword, category_name) tuples
        
    Returns:
        Category name if keyword match found, None otherwise
    """
    if not narration or not keyword_mappings:
        return None
    
    narration_lower = narration.lower()
    
    # Check each keyword (longest matches first for better precision)
    # Sort by keyword length (longest first) to match "mutual fund" before "fund"
    sorted_mappings = sorted(keyword_mappings, key=lambda x: len(x[0]), reverse=True)
    
    for keyword, category_name in sorted_mappings:
        # Use word boundary matching for better precision
        # Pattern: \b matches word boundaries
        pattern = r'\b' + re.escape(keyword) + r'\b'
        if re.search(pattern, narration_lower, re.IGNORECASE):
            logging.info(f"✅ Keyword match found: '{keyword}' -> '{category_name}' in narration: '{narration[:100]}'")
            return category_name
    
    return None


class MultiTaskDistilBERT(nn.Module):
    """DistilBERT with multiple classification heads (same as training)"""
    
    def __init__(self, base_model_name: str, tasks: Dict):
        super(MultiTaskDistilBERT, self).__init__()
        
        # Load base DistilBERT model
        self.bert = DistilBertForSequenceClassification.from_pretrained(
            base_model_name,
            num_labels=768
        )
        
        # Remove the classification head from base model
        self.bert.classifier = nn.Identity()
        
        # Create separate heads for each task
        self.task_heads = nn.ModuleDict()
        for task_name, task_config in tasks.items():
            if task_config.get('num_labels'):
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


class DistilBertPredictor:
    """Predictor class for DistilBERT multi-task model"""
    
    def __init__(self, model_path: str = MODEL_PATH, categories_file: str = CATEGORIES_FILE):
        self.device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
        self.model_path = model_path
        self.categories_file = categories_file
        self.model = None
        self.tokenizer = None
        self.label_decoders = {}
        self.tasks = {}
        self.keyword_mappings = load_keyword_mappings(categories_file)
        self._load_model()
    
    def _load_model(self):
        """Load model and tokenizer"""
        # Load config
        config_path = os.path.join(self.model_path, "config.json")
        if not os.path.exists(config_path):
            raise FileNotFoundError(f"Model config not found at {config_path}")
        
        with open(config_path, 'r') as f:
            model_config = json.load(f)
        
        self.tasks = model_config['tasks']
        
        # Reconstruct label decoders
        for task_name, task_info in self.tasks.items():
            labels = task_info.get('labels', [])
            self.label_decoders[task_name] = {
                idx: label for idx, label in enumerate(labels)
            }
        
        # Load tokenizer
        self.tokenizer = DistilBertTokenizer.from_pretrained(self.model_path)
        
        # Load model architecture and weights
        model_name = model_config.get('model_name', 'distilbert-base-uncased')
        self.model = MultiTaskDistilBERT(model_name, self.tasks)
        
        # Load weights
        try:
            # Try loading from safetensors
            from safetensors.torch import load_file
            safetensors_path = os.path.join(self.model_path, "model.safetensors")
            if os.path.exists(safetensors_path):
                state_dict = load_file(safetensors_path)
                # Need to map state dict to match model structure
                self._load_state_dict(state_dict)
            else:
                raise FileNotFoundError("Model weights not found")
        except ImportError:
            # Fallback: try PyTorch format
            pytorch_path = os.path.join(self.model_path, "pytorch_model.bin")
            if os.path.exists(pytorch_path):
                state_dict = torch.load(pytorch_path, map_location=self.device)
                self._load_state_dict(state_dict)
            else:
                raise FileNotFoundError(f"Model weights not found in {self.model_path}")
        
        self.model.to(self.device)
        self.model.eval()
        logging.info(f"DistilBERT model loaded from {self.model_path} on {self.device}")
    
    def _load_state_dict(self, state_dict: Dict):
        """Load state dict, handling both full model and separate BERT/task_heads"""
        # Try to load full state dict first
        try:
            self.model.load_state_dict(state_dict, strict=False)
            return
        except:
            pass
        
        # If that fails, try loading BERT and task heads separately
        bert_state = {}
        task_heads_state = {}
        
        for key, value in state_dict.items():
            if key.startswith('bert.'):
                bert_state[key] = value
            elif key.startswith('task_heads.'):
                task_heads_state[key] = value
        
        # Load BERT weights
        if bert_state:
            # Load base BERT from pretrained, then update weights
            from transformers import DistilBertForSequenceClassification
            bert_model = DistilBertForSequenceClassification.from_pretrained(
                self.model_path,
                num_labels=768
            )
            self.model.bert = bert_model.bert
            self.model.bert.classifier = nn.Identity()
        
        # Load task heads
        task_heads_path = os.path.join(self.model_path, "task_heads.pt")
        if os.path.exists(task_heads_path):
            task_heads_dict = torch.load(task_heads_path, map_location=self.device)
            self.model.task_heads.load_state_dict(task_heads_dict)
    
    def predict(self, narration: str) -> Dict:
        """
        Predict transaction_type, category, and intent for narration.
        Keyword matching takes precedence over model predictions.
        
        Args:
            narration: Transaction description text
            
        Returns:
            Dictionary with predictions, confidence, and probabilities
        """
        if not narration or not narration.strip():
            return {
                "transaction_type": "N/A",
                "category": "Uncategorized",
                "intent": "N/A",
                "confidence": {},
                "probabilities": {}
            }
        
        # FIRST: Check keyword matching (takes precedence over model)
        # Check both original and cleaned narration
        keyword_matched_category = match_keywords(narration, self.keyword_mappings)
        if not keyword_matched_category:
            # Also try cleaned narration
            clean_narration = clean_text(narration)
            if clean_narration and clean_narration.lower() != narration.lower():
                keyword_matched_category = match_keywords(clean_narration, self.keyword_mappings)
        
        # If keyword matched, use that category and still get model prediction for transaction_type and intent
        if keyword_matched_category:
            # Still get model prediction for transaction_type and intent, but override category
            clean_narration = clean_text(narration)
            model_results = self._predict_with_model(clean_narration) if clean_narration else {
                "transaction_type": "N/A",
                "intent": "N/A",
                "confidence": {},
                "probabilities": {}
            }
            
            # Override category with keyword match, but keep model's transaction_type and intent
            results = {
                "transaction_type": model_results.get("transaction_type", "N/A"),
                "category": keyword_matched_category,  # Keyword match takes precedence
                "intent": model_results.get("intent", "N/A"),
                "confidence": model_results.get("confidence", {}),
                "probabilities": model_results.get("probabilities", {})
            }
            
            # Add flag to indicate keyword matching was used
            results["keyword_matched"] = True
            results["model_category"] = model_results.get("category", "Uncategorized")  # Store model's original prediction
            
            logging.info(f"✅ Using keyword-matched category '{keyword_matched_category}' "
                        f"(model would have predicted '{model_results.get('category', 'Uncategorized')}')")
            
            return results
        
        # No keyword match - use model prediction
        clean_narration = clean_text(narration)
        if not clean_narration:
            return {
                "transaction_type": "N/A",
                "category": "Uncategorized",
                "intent": "N/A",
                "confidence": {},
                "probabilities": {}
            }
        
        results = self._predict_with_model(clean_narration)
        results["keyword_matched"] = False
        return results
    
    def _predict_with_model(self, clean_narration: str) -> Dict:
        """
        Internal method to get model predictions only.
        Used by predict() method.
        """
        # Tokenize
        inputs = self.tokenizer(
            clean_narration,
            return_tensors="pt",
            truncation=True,
            padding=True,
            max_length=128
        )
        input_ids = inputs['input_ids'].to(self.device)
        attention_mask = inputs['attention_mask'].to(self.device)
        
        # Predict
        with torch.no_grad():
            outputs = self.model(input_ids=input_ids, attention_mask=attention_mask)
        
        results = {
            "transaction_type": "N/A",
            "category": "Uncategorized",
            "intent": "N/A",
            "confidence": {},
            "probabilities": {}
        }
        
        # Decode predictions for each task
        for task_name, logits in outputs['predictions'].items():
            probabilities = torch.softmax(logits, dim=1)[0].cpu().numpy()
            predicted_idx = torch.argmax(logits, dim=1).item()
            
            if task_name in self.label_decoders:
                predicted_label = self.label_decoders[task_name].get(
                    predicted_idx,
                    list(self.label_decoders[task_name].values())[0] if self.label_decoders[task_name] else "N/A"
                )
                confidence = float(probabilities[predicted_idx])
                
                # Map task names
                if task_name == 'transaction_type':
                    results["transaction_type"] = predicted_label
                elif task_name == 'category':
                    results["category"] = predicted_label
                elif task_name == 'intent':
                    results["intent"] = predicted_label
                
                results['confidence'][task_name] = confidence
                results['probabilities'][task_name] = {
                    self.label_decoders[task_name][i]: float(p) 
                    for i, p in enumerate(probabilities) 
                    if i in self.label_decoders[task_name]
                }
        
        return results


# Singleton instance
_predictor_instance = None


def get_predictor(model_path: str = MODEL_PATH, categories_file: str = CATEGORIES_FILE) -> DistilBertPredictor:
    """Get singleton predictor instance"""
    global _predictor_instance
    if _predictor_instance is None:
        _predictor_instance = DistilBertPredictor(model_path, categories_file)
    return _predictor_instance


if __name__ == "__main__":
    # Test inference
    predictor = get_predictor()
    result = predictor.predict("UPI/PAY/1234567890/STARBUCKS/txn@paytm")
    print(json.dumps(result, indent=2))

