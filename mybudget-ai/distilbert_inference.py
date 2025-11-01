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
import torch
import torch.nn as nn
from typing import Dict, Optional
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

logging.basicConfig(level=logging.WARNING)


def clean_text(text: str) -> str:
    """Clean transaction narration text"""
    if not text:
        return ""
    return str(text).strip()


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
    
    def __init__(self, model_path: str = MODEL_PATH):
        self.device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
        self.model_path = model_path
        self.model = None
        self.tokenizer = None
        self.label_decoders = {}
        self.tasks = {}
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
        Predict transaction_type, category, and intent for narration
        
        Args:
            narration: Transaction description text
            
        Returns:
            Dictionary with predictions, confidence, and probabilities
        """
        clean_narration = clean_text(narration)
        if not clean_narration:
            return {
                "transaction_type": "N/A",
                "category": "Uncategorized",
                "intent": "N/A",
                "confidence": {},
                "probabilities": {}
            }
        
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


def get_predictor(model_path: str = MODEL_PATH) -> DistilBertPredictor:
    """Get singleton predictor instance"""
    global _predictor_instance
    if _predictor_instance is None:
        _predictor_instance = DistilBertPredictor(model_path)
    return _predictor_instance


if __name__ == "__main__":
    # Test inference
    predictor = get_predictor()
    result = predictor.predict("UPI/PAY/1234567890/STARBUCKS/txn@paytm")
    print(json.dumps(result, indent=2))

