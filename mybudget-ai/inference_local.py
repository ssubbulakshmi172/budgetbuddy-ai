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
except ImportError as e:
    sys.stderr.write(f"❌ Failed to import DistilBERT: {e}\n")
    sys.stderr.write("Please ensure distilbert_inference.py is in the same directory\n")
    sys.exit(1)

# Configuration flag: Set to True to include probability distributions in output
INCLUDE_PROBABILITIES = False

# Load model on startup (singleton pattern)
_predictor = None


def extract_category_parts(category: str) -> tuple:
    """
    Extract top-level category and subcategory from full category string.
    If category is in format "TopCategory / Subcategory", returns both parts.
    Otherwise returns (category, None).
    
    Args:
        category: Full category string (e.g., "Investments & Finance / Stocks & Bonds")
        
    Returns:
        Tuple of (top_category, subcategory) where:
        - top_category: Top-level category (e.g., "Investments & Finance")
        - subcategory: Subcategory (e.g., "Stocks & Bonds") or None if no separator
    """
    if not category:
        return (category, None)
    
    # Split on " / " separator
    if " / " in category:
        parts = category.split(" / ", 1)
        if len(parts) == 2:
            return (parts[0].strip(), parts[1].strip())
    
    # No separator found, return original category with no subcategory
    return (category, None)


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
            "predicted_category": "Uncategorized",
            "predicted_subcategory": None
        }
    
    # Check if model loaded
    if _predictor is None:
        return {
            "error": "Model not loaded",
            "predicted_category": "Uncategorized",
            "predicted_subcategory": None
        }
    
    # Use DistilBERT for prediction
    try:
        result = _predictor.predict(description)
        # Extract top category and subcategory from full category
        # (e.g., "Investments & Finance / Stocks & Bonds" -> "Investments & Finance" and "Stocks & Bonds")
        full_category = result.get("category", "Uncategorized")
        top_category, subcategory = extract_category_parts(full_category)
        
        # Build response with both category and subcategory
        response = {
            "description": description,
            "model_type": "DistilBERT",
            "transaction_type": result.get("transaction_type", "N/A"),
            "predicted_category": top_category,
            "intent": result.get("intent", "N/A"),
            "confidence": result.get("confidence", {})
        }
        
        # Include probability distributions if flag is enabled
        if INCLUDE_PROBABILITIES and "probabilities" in result:
            response["all_probabilities"] = result.get("probabilities", {})
        
        # Add subcategory if it exists
        if subcategory:
            response["predicted_subcategory"] = subcategory
        
        return response
    except Exception as e:
        sys.stderr.write(f"❌ DistilBERT prediction error: {e}\n")
        import traceback
        sys.stderr.write(f"Traceback: {traceback.format_exc()}\n")
        return {
            "error": str(e),
            "predicted_category": "Uncategorized",
            "predicted_subcategory": None
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
            "predicted_category": "Uncategorized",
            "predicted_subcategory": None
        }] * len(descriptions)
    
    results = []
    for desc in descriptions:
        if not desc or not desc.strip():
            results.append({
                "error": "Empty description",
                "predicted_category": "Uncategorized",
                "predicted_subcategory": None
            })
        else:
            try:
                result = _predictor.predict(desc)
                # Extract top category and subcategory from full category
                # (e.g., "Investments & Finance / Stocks & Bonds" -> "Investments & Finance" and "Stocks & Bonds")
                full_category = result.get("category", "Uncategorized")
                top_category, subcategory = extract_category_parts(full_category)
                
                # Build response with both category and subcategory
                batch_result = {
                    "description": desc,
                    "model_type": "DistilBERT",
                    "transaction_type": result.get("transaction_type", "N/A"),
                    "predicted_category": top_category,
                    "intent": result.get("intent", "N/A"),
                    "confidence": result.get("confidence", {})
                }
                
                # Include probability distributions if flag is enabled
                if INCLUDE_PROBABILITIES and "probabilities" in result:
                    batch_result["all_probabilities"] = result.get("probabilities", {})
                
                # Add subcategory if it exists
                if subcategory:
                    batch_result["predicted_subcategory"] = subcategory
                
                results.append(batch_result)
            except Exception as e:
                sys.stderr.write(f"❌ Batch prediction error for '{desc}': {e}\n")
                results.append({
                    "error": str(e),
                    "predicted_category": "Uncategorized",
                    "predicted_subcategory": None
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
            "predicted_category": "Uncategorized",
            "predicted_subcategory": None
        }
        print(json.dumps(result), file=sys.stdout, flush=True)
        sys.exit(1)
    
    # Check if this is batch mode (JSON array as first argument)
    if len(sys.argv) < 2:
        result = {
            "error": "Missing description argument",
            "predicted_category": "Uncategorized",
            "predicted_subcategory": None
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
                "predicted_category": "Uncategorized",
                "predicted_subcategory": None
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
