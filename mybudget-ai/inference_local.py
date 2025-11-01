#!/usr/bin/env python3
"""
inference_local.py

Standalone inference script for local DistilBERT model prediction (no Flask server).
Called from Java via ProcessBuilder.

Uses distilbert_inference.py for predictions.

Usage:
    python3 inference_local.py "UPI/PAY/1234567890/STARBUCKS/txn@paytm"

Output: JSON to stdout
{
    "model_type": "DistilBERT",
    "transaction_type": "P2C",
    "predicted_category": "Dining",
    "intent": "purchase",
    "confidence": {...},
    "all_probabilities": {...}
}
"""

import sys
import json
import os
import logging
import warnings

# Suppress warnings for cleaner output
logging.basicConfig(level=logging.WARNING)
warnings.filterwarnings('ignore')
os.environ['PYTHONWARNINGS'] = 'ignore'

# Import DistilBERT inference module
try:
    from distilbert_inference import get_predictor
    DISTILBERT_AVAILABLE = True
except ImportError as e:
    DISTILBERT_AVAILABLE = False
    sys.stderr.write(f"❌ Failed to import DistilBERT: {e}\n")
    sys.stderr.write("Please ensure distilbert_inference.py is in the same directory\n")
    sys.exit(1)

# Load model on startup (singleton pattern)
_predictor = None


def load_model():
    """Load DistilBERT model once (singleton)"""
    global _predictor
    
    if _predictor is None:
        # Change to script directory to find models relative to script
        script_dir = os.path.dirname(os.path.abspath(__file__))
        original_cwd = os.getcwd()
        os.chdir(script_dir)
        
        try:
            _predictor = get_predictor()
            sys.stderr.write(f"✅ DistilBERT model loaded successfully\n")
        except Exception as e:
            sys.stderr.write(f"❌ Error loading DistilBERT: {e}\n")
            import traceback
            sys.stderr.write(f"Traceback: {traceback.format_exc()}\n")
            _predictor = None
            raise
        finally:
            os.chdir(original_cwd)


def predict(description: str) -> dict:
    """
    Predict category for transaction description using DistilBERT
    
    Args:
        description: Transaction narration/description
        
    Returns:
        Dictionary with prediction results
    """
    global _predictor
    
    if not description or not description.strip():
        return {
            "error": "Empty description",
            "predicted_category": "Uncategorized"
        }
    
    # Check if model loaded
    if _predictor is None:
        return {
            "error": "Model not loaded",
            "predicted_category": "Uncategorized"
        }
    
    # Use DistilBERT for prediction
    try:
        result = _predictor.predict(description)
        return {
            "description": description,
            "model_type": "DistilBERT",
            "transaction_type": result.get("transaction_type", "N/A"),
            "predicted_category": result.get("category", "Uncategorized"),
            "intent": result.get("intent", "N/A"),
            "confidence": result.get("confidence", {}),
            "all_probabilities": result.get("probabilities", {})
        }
    except Exception as e:
        sys.stderr.write(f"❌ DistilBERT prediction error: {e}\n")
        import traceback
        sys.stderr.write(f"Traceback: {traceback.format_exc()}\n")
        return {
            "error": str(e),
            "predicted_category": "Uncategorized"
        }


def predict_batch(descriptions: list) -> list:
    """
    Predict categories for multiple transaction descriptions at once (BATCH)
    Much faster than calling predict() multiple times!
    
    Args:
        descriptions: List of transaction narrations/descriptions
        
    Returns:
        List of prediction dictionaries
    """
    global _predictor
    
    if _predictor is None:
        return [{
            "error": "Model not loaded",
            "predicted_category": "Uncategorized"
        }] * len(descriptions)
    
    results = []
    for desc in descriptions:
        if not desc or not desc.strip():
            results.append({
                "error": "Empty description",
                "predicted_category": "Uncategorized"
            })
        else:
            try:
                result = _predictor.predict(desc)
                results.append({
                    "description": desc,
                    "model_type": "DistilBERT",
                    "transaction_type": result.get("transaction_type", "N/A"),
                    "predicted_category": result.get("category", "Uncategorized"),
                    "intent": result.get("intent", "N/A"),
                    "confidence": result.get("confidence", {}),
                    "all_probabilities": result.get("probabilities", {})
                })
            except Exception as e:
                sys.stderr.write(f"❌ Batch prediction error for '{desc}': {e}\n")
                results.append({
                    "error": str(e),
                    "predicted_category": "Uncategorized"
                })
    
    return results


def main():
    """Main entry point"""
    # Load model ONCE
    try:
        load_model()
    except Exception as e:
        result = {
            "error": f"Failed to load model: {str(e)}",
            "predicted_category": "Uncategorized"
        }
        print(json.dumps(result), file=sys.stdout, flush=True)
        sys.exit(1)
    
    # Check if this is batch mode (JSON array as first argument)
    if len(sys.argv) < 2:
        result = {
            "error": "Missing description argument",
            "predicted_category": "Uncategorized"
        }
        print(json.dumps(result), file=sys.stdout, flush=True)
        sys.exit(1)
    
    first_arg = sys.argv[1].strip()
    
    # If argument starts with '[' it's a JSON array (batch mode)
    if first_arg.startswith('['):
        try:
            descriptions = json.loads(first_arg)
            if not isinstance(descriptions, list):
                raise ValueError("Batch input must be a JSON array")
            
            # Batch predict
            results = predict_batch(descriptions)
            
            # Output JSON array to stdout
            json_output = json.dumps(results)
            print(json_output, file=sys.stdout, flush=True)
            
            # Exit with error only if there are real errors (not just "Uncategorized")
            has_real_error = any(
                "error" in r and r.get("error") not in ["Empty description"]
                for r in results
            )
            sys.exit(1 if has_real_error else 0)
        except json.JSONDecodeError as e:
            result = {
                "error": f"Invalid JSON array: {str(e)}",
                "predicted_category": "Uncategorized"
            }
            print(json.dumps(result), file=sys.stdout, flush=True)
            sys.exit(1)
    else:
        # Single prediction mode (backward compatible)
        description = first_arg
        
        # Predict
        result = predict(description)
        
        # Output JSON to stdout (only JSON, no other output)
        json_output = json.dumps(result)
        print(json_output, file=sys.stdout, flush=True)
        
        # Exit with error code only if there's an actual error
        if "error" in result:
            error_msg = result.get("error", "")
            if error_msg and error_msg not in ["Empty description"]:
                sys.exit(1)


if __name__ == "__main__":
    main()
