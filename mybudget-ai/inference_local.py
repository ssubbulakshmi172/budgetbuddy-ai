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
    from distilbert_inference import get_predictor, clean_text
except ImportError as e:
    sys.stderr.write(f"❌ Failed to import DistilBERT: {e}\n")
    sys.stderr.write("Please ensure distilbert_inference.py is in the same directory\n")
    sys.exit(1)

# Import preprocessing utils for consistent preprocessing
try:
    from preprocessing_utils import preprocess_upi_narration
    PREPROCESSING_AVAILABLE = True
except ImportError:
    PREPROCESSING_AVAILABLE = False
    sys.stderr.write("⚠️ preprocessing_utils.py not found. Corrections matching will be exact only.\n")

# Configuration flag: Set to True to include probability distributions in output
INCLUDE_PROBABILITIES = False

# Load model on startup (singleton pattern)
_predictor = None

# User corrections cache (in-memory for fast lookup)
_corrections_cache = None
CORRECTIONS_FILE = "user_corrections.json"


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


def load_corrections():
    """
    Load user corrections from JSON file into memory (singleton).
    
    Preprocesses narrations before storing to match how the model preprocesses input.
    This ensures corrections match even if the raw narration differs (e.g., with/without UPI tags).
    """
    global _corrections_cache
    
    if _corrections_cache is None:
        _corrections_cache = {}
        
        # Get script directory
        script_dir = os.path.dirname(os.path.abspath(__file__))
        corrections_path = os.path.join(script_dir, CORRECTIONS_FILE)
        
        if os.path.exists(corrections_path):
            try:
                with open(corrections_path, 'r', encoding='utf-8') as f:
                    content = f.read().strip()
                    if content:
                        corrections_data = json.loads(content)
                        if isinstance(corrections_data, list):
                            loaded_count = 0
                            for correction in corrections_data:
                                narration = correction.get("narration", "").strip()
                                category = correction.get("category", "").strip()
                                if narration and category:
                                    # Preprocess narration to match how model preprocesses input
                                    # This ensures corrections work even if raw narration format differs
                                    if PREPROCESSING_AVAILABLE:
                                        preprocessed_narration = preprocess_upi_narration(narration, preserve_p2p_clues=True)
                                    else:
                                        # Fallback: use clean_text from distilbert_inference
                                        try:
                                            preprocessed_narration = clean_text(narration)
                                        except:
                                            preprocessed_narration = narration.strip()
                                    
                                    # Only store if preprocessing didn't make it empty
                                    if preprocessed_narration and preprocessed_narration.strip():
                                        # Store in lowercase for case-insensitive matching
                                        key = preprocessed_narration.lower().strip()
                                        _corrections_cache[key] = category
                                        loaded_count += 1
                                    else:
                                        # If preprocessing made it empty, store original (fallback)
                                        key = narration.lower().strip()
                                        _corrections_cache[key] = category
                                        loaded_count += 1
                            
                            sys.stderr.write(f"✅ Loaded {loaded_count} user corrections into memory (preprocessed)\n")
                        else:
                            sys.stderr.write(f"⚠️ Corrections file is not a list, skipping\n")
            except Exception as e:
                sys.stderr.write(f"⚠️ Failed to load corrections: {e}\n")
        else:
            sys.stderr.write(f"💡 No corrections file found: {corrections_path}\n")
    
    return _corrections_cache


def get_corrected_category(description: str) -> str:
    """
    Check if description has a user correction.
    
    Preprocesses the description the same way the model does before lookup.
    This ensures corrections match even if raw narration format differs.
    
    Args:
        description: Transaction narration/description (raw, will be preprocessed)
        
    Returns:
        Corrected category if found, None otherwise
    """
    if not description or not description.strip():
        return None
    
    corrections = load_corrections()
    if not corrections:
        return None
    
    # Preprocess description to match how corrections were stored
    # This ensures "UPI-STARBUCKS-123" matches correction stored as "STARBUCKS"
    if PREPROCESSING_AVAILABLE:
        preprocessed_desc = preprocess_upi_narration(description, preserve_p2p_clues=True)
    else:
        # Fallback: use clean_text from distilbert_inference
        try:
            preprocessed_desc = clean_text(description)
        except:
            preprocessed_desc = description.strip()
    
    if not preprocessed_desc or not preprocessed_desc.strip():
        # If preprocessing made it empty, try original
        preprocessed_desc = description.strip()
    
    # Check match (case-insensitive, preprocessed)
    key = preprocessed_desc.lower().strip()
    return corrections.get(key)


def load_model():
    """Load DistilBERT model once (singleton)"""
    global _predictor
    
    if _predictor is None:
        # Change to script directory to find models relative to script
        script_dir = os.path.dirname(os.path.abspath(__file__))
        original_cwd = os.getcwd()
        os.chdir(script_dir)
        
        try:
            # Load corrections first (fast, in-memory)
            load_corrections()
            
            # Load model
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
    Predict category for transaction description.
    
    Priority Order (same as distilbert_inference.py):
    1. User corrections (highest priority - from user_corrections.json, preprocessed)
    2. Keyword matching (from categories.yml - rule-based)
    3. DistilBERT model (lowest priority - ML prediction)
    
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
    
    # STEP 1: Check user corrections first (fast in-memory lookup, preprocessed)
    corrected_category = get_corrected_category(description)
    if corrected_category:
        # Extract category parts
        top_category, subcategory = extract_category_parts(corrected_category)
        
        # Still get model prediction for transaction_type and intent
        transaction_type = "N/A"
        intent = "N/A"
        if _predictor is not None:
            try:
                from distilbert_inference import clean_text
                clean_desc = clean_text(description)
                model_result = _predictor._predict_with_model(clean_desc) if clean_desc else None
                if model_result:
                    transaction_type = model_result.get("transaction_type", "N/A")
                    intent = model_result.get("intent", "N/A")
            except:
                pass  # Ignore errors, use defaults
        
        response = {
            "description": description,
            "model_type": "User_Correction",
            "transaction_type": transaction_type,
            "predicted_category": top_category,
            "intent": intent,
            "confidence": {"category": 1.0},  # User corrections have 100% confidence
            "reason": "user_correction"
        }
        
        if subcategory:
            response["predicted_subcategory"] = subcategory
        
        sys.stderr.write(f"✅ Using user correction: '{description[:50]}...' -> '{top_category}'\n")
        return response
    
    # STEP 2: No correction found - use model's predict() method
    # The model's predict() method handles the full order:
    # 1. User corrections (already checked above, but model checks for consistency)
    # 2. Keywords (from categories.yml)
    # 3. Model prediction
    if _predictor is None:
        return {
            "error": "Model not loaded",
            "predicted_category": "Uncategorized",
            "predicted_subcategory": None
        }
    
    # Use model's predict() which handles corrections, keywords, and model in order
    try:
        result = _predictor.predict(description)
        # Extract top category and subcategory from full category
        # (e.g., "Investments & Finance / Stocks & Bonds" -> "Investments & Finance" and "Stocks & Bonds")
        full_category = result.get("category", "Uncategorized")
        top_category, subcategory = extract_category_parts(full_category)
        
        # Determine model_type and reason based on what was used
        model_type = "DistilBERT"
        reason = "ml_prediction"
        if result.get("user_correction", False):
            model_type = "User_Correction"
            reason = "user_correction"
        elif result.get("keyword_matched", False):
            model_type = "Keyword_Match"
            reason = "keyword_match"
        
        # Build response with both category and subcategory
        response = {
            "description": description,
            "model_type": model_type,
            "transaction_type": result.get("transaction_type", "N/A"),
            "predicted_category": top_category,
            "intent": result.get("intent", "N/A"),
            "confidence": result.get("confidence", {}),
            "reason": reason
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
    
    Checks user corrections first, then uses model for remaining.
    
    Args:
        descriptions: List of transaction narrations/descriptions
        
    Returns:
        List of prediction dictionaries
    """
    global _predictor
    
    # Load corrections if not already loaded
    corrections = load_corrections()
    
    if _predictor is None:
        return [{
            "error": "Model not loaded",
            "predicted_category": "Uncategorized",
            "predicted_subcategory": None
        }] * len(descriptions)
    
    results = []
    descriptions_for_model = []  # Narrations that need model prediction
    indices_for_model = []  # Indices of narrations that need model prediction
    
    # First pass: Check corrections for all descriptions
    for i, desc in enumerate(descriptions):
        if not desc or not desc.strip():
            results.append({
                "error": "Empty description",
                "predicted_category": "Uncategorized",
                "predicted_subcategory": None
            })
        else:
            # Check for correction first
            corrected_category = get_corrected_category(desc)
            if corrected_category:
                # Use correction
                top_category, subcategory = extract_category_parts(corrected_category)
                batch_result = {
                    "description": desc,
                    "model_type": "User_Correction",
                    "transaction_type": "N/A",
                    "predicted_category": top_category,
                    "intent": "N/A",
                    "confidence": {"category": 1.0},
                    "reason": "user_correction"
                }
                if subcategory:
                    batch_result["predicted_subcategory"] = subcategory
                results.append(batch_result)
            else:
                # No correction, will use model
                results.append(None)  # Placeholder
                descriptions_for_model.append(desc)
                indices_for_model.append(i)
    
    # Second pass: Batch predict remaining descriptions with model
    if descriptions_for_model:
        try:
            # Batch predict all at once (much faster)
            model_results = []
            for desc in descriptions_for_model:
                result = _predictor.predict(desc)
                full_category = result.get("category", "Uncategorized")
                top_category, subcategory = extract_category_parts(full_category)
                
                batch_result = {
                    "description": desc,
                    "model_type": "DistilBERT",
                    "transaction_type": result.get("transaction_type", "N/A"),
                    "predicted_category": top_category,
                    "intent": result.get("intent", "N/A"),
                    "confidence": result.get("confidence", {}),
                    "reason": "ml_prediction"
                }
                
                if INCLUDE_PROBABILITIES and "probabilities" in result:
                    batch_result["all_probabilities"] = result.get("probabilities", {})
                
                if subcategory:
                    batch_result["predicted_subcategory"] = subcategory
                
                model_results.append(batch_result)
            
            # Fill in model results at correct indices
            for idx, model_result in zip(indices_for_model, model_results):
                results[idx] = model_result
                
        except Exception as e:
            sys.stderr.write(f"❌ Batch prediction error: {e}\n")
            # Fill errors for failed predictions
            for idx in indices_for_model:
                results[idx] = {
                    "error": str(e),
                    "predicted_category": "Uncategorized",
                    "predicted_subcategory": None
                }
    
    return results


def main():
    """Main entry point"""
    # Load corrections and model ONCE
    try:
        # Load corrections first (fast, in-memory)
        load_corrections()
        # Load model
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
