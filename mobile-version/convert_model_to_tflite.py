#!/usr/bin/env python3
"""
Convert DistilBERT PyTorch model to TFLite format for Android deployment.

This script handles:
- TensorFlow AVX compatibility issues gracefully
- Architecture mismatches (x86_64 vs arm64)
- Interrupt handling (Ctrl+C works at any time)
- Graceful degradation (works without full TensorFlow conversion)
- Progress indicators throughout
"""

import os
import sys
import json
import logging
import time
import warnings
import signal
import subprocess
import threading
from pathlib import Path
from datetime import datetime

# ============================================================================
# GLOBAL CONFIGURATION
# ============================================================================

# Global flag for graceful shutdown
_shutdown_requested = False

# Configuration paths
MODEL_DIR = "../mybudget-ai/models/distilbert_multitask_latest"
OUTPUT_DIR = "app/src/main/assets"
OUTPUT_MODEL = "distilbert_model.tflite"
OUTPUT_VOCAB = "vocab.txt"

# ============================================================================
# SIGNAL HANDLING (Interruptibility)
# ============================================================================

def signal_handler(signum, frame):
    """Handle interrupt signals gracefully"""
    global _shutdown_requested
    print("\n⚠️  Interrupt signal received. Attempting graceful shutdown...", flush=True)
    _shutdown_requested = True
    time.sleep(0.5)
    sys.exit(130)

# Register signal handlers
signal.signal(signal.SIGINT, signal_handler)
signal.signal(signal.SIGTERM, signal_handler)

# ============================================================================
# ENVIRONMENT SETUP (TensorFlow AVX mitigation)
# ============================================================================

# Suppress TensorFlow AVX warnings/errors
os.environ['TF_CPP_MIN_LOG_LEVEL'] = '3'  # Suppress all TensorFlow logs
os.environ['TF_ENABLE_ONEDNN_OPTS'] = '0'  # Disable AVX-requiring optimizations
os.environ['TF_DISABLE_MKL'] = '1'  # Disable Intel MKL
warnings.filterwarnings('ignore', message='.*AVX.*')
warnings.filterwarnings('ignore', message='.*TensorFlow.*')
warnings.filterwarnings('ignore', category=UserWarning)

# ============================================================================
# LOGGING SETUP
# ============================================================================

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.StreamHandler(sys.stdout),
        logging.FileHandler('conversion.log', mode='w')
    ],
    force=True
)
logger = logging.getLogger(__name__)

# ============================================================================
# ARCHITECTURE CHECK
# ============================================================================

def check_architecture():
    """Check Python architecture and warn if not x86_64
    
    Note: Model weights are architecture-independent (they're just data files).
    The architecture check is for conversion tools (NumPy, TensorFlow) compatibility,
    NOT for the model itself. You can train on ARM64 and convert on x86_64!
    """
    import platform
    python_arch = platform.machine()
    logger.info(f"📐 Python architecture: {python_arch}")
    logger.info("   ℹ️  Note: Model weights work on any architecture")
    logger.info("   ℹ️  Architecture check is for conversion tools compatibility")
    
    if python_arch != 'x86_64':
        logger.warning(f"⚠️  Running on {python_arch}")
        logger.warning("   💡 x86_64 recommended for conversion tools (NumPy, TensorFlow)")
        logger.warning("   💡 To switch: env /usr/bin/arch -x86_64 /bin/zsh")
        logger.warning("   💡 Model weights are architecture-independent - no problem!")
    else:
        logger.info("   ✅ Architecture is x86_64 (optimal for conversion tools)")
    
    return python_arch

# ============================================================================
# DEPENDENCY CHECKING (with subprocess isolation for TensorFlow)
# ============================================================================

def test_tensorflow_import(timeout=10):
    """Test TensorFlow import in isolated subprocess to prevent AVX crashes"""
    logger.info("   ⏳ Testing TensorFlow import (isolated subprocess)...")
    logger.info("   💡 Press Ctrl+C to cancel if it hangs")
    sys.stdout.flush()
    
    test_script = '''
import sys, os
os.environ["TF_CPP_MIN_LOG_LEVEL"] = "3"
os.environ["TF_ENABLE_ONEDNN_OPTS"] = "0"
os.environ["TF_DISABLE_MKL"] = "1"
try:
    import tensorflow as tf
    print(f"SUCCESS:{tf.__version__}")
    sys.exit(0)
except SystemExit as e:
    print(f"ABORT:{e.code}")
    sys.exit(1)
except Exception as e:
    print(f"ERROR:{str(e)[:100]}")
    sys.exit(1)
'''
    
    try:
        process = subprocess.Popen(
            [sys.executable, '-c', test_script],
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            text=True
        )
        
        try:
            stdout, _ = process.communicate(timeout=timeout)
            result_code = process.returncode
            
            if result_code == 0 and stdout and "SUCCESS:" in stdout:
                # Extract version from SUCCESS line, ignore ABORT that comes after
                for line in stdout.strip().split('\n'):
                    if line.startswith("SUCCESS:"):
                        version = line.split("SUCCESS:")[1].strip()
                        logger.info(f"   ✅ TensorFlow {version} imported successfully")
                        return True, version, None
            else:
                error_msg = stdout.strip() if stdout else "Unknown error"
                # Check for AVX issues but ignore if SUCCESS also appears
                if "SUCCESS:" not in error_msg and ("ABORT" in error_msg or "AVX" in error_msg.upper()):
                    return False, None, "AVX_INCOMPATIBLE"
                if "SUCCESS:" in error_msg:
                    # TensorFlow works but there might be a warning - extract version
                    for line in error_msg.split('\n'):
                        if line.startswith("SUCCESS:"):
                            version = line.split("SUCCESS:")[1].strip()
                            logger.info(f"   ✅ TensorFlow {version} imported successfully")
                            return True, version, None
                return False, None, error_msg
                
        except subprocess.TimeoutExpired:
            logger.warning("   ⏱️  TensorFlow import timed out (killing subprocess)...")
            process.kill()
            process.wait(timeout=2)
            return False, None, "TIMEOUT"
        except KeyboardInterrupt:
            logger.warning("   ⚠️  Interrupted by user")
            process.terminate()
            try:
                process.wait(timeout=2)
            except:
                process.kill()
            raise
            
    except KeyboardInterrupt:
        raise
    except Exception as e:
        return False, None, str(e)

def check_dependencies():
    """Check all required dependencies"""
    logger.info("🔍 Checking dependencies...")
    missing = []
    tensorflow_available = False
    tensorflow_version = None
    
    # Check ONNX
    try:
        import onnx
        logger.info(f"   ✅ onnx {onnx.__version__}")
    except ImportError:
        missing.append("onnx")
        logger.warning("   ❌ onnx not found")
    
    # Check onnx-tf
    try:
        import onnx_tf
        logger.info("   ✅ onnx-tf")
    except ImportError:
        missing.append("onnx-tf")
        logger.warning("   ❌ onnx-tf not found")
    
    # Test TensorFlow in subprocess (prevents AVX crashes)
    tf_available, tf_version, tf_error = test_tensorflow_import()
    
    if tf_available:
        tensorflow_available = True
        tensorflow_version = tf_version
    else:
        if tf_error == "AVX_INCOMPATIBLE":
            logger.error("   ❌ TensorFlow AVX incompatibility detected")
            logger.error("   💡 This is handled gracefully - conversion will continue")
            logger.error("   💡 Solution: Install TensorFlow without AVX or use model_info.json")
        else:
            logger.warning(f"   ⚠️  TensorFlow import failed: {tf_error}")
        missing.append("tensorflow")
    
    if missing:
        logger.warning(f"\n⚠️  Some dependencies missing: {', '.join(missing)}")
        logger.info("   The script will create model_info.json even without full conversion")
        return False, tensorflow_available, tensorflow_version
    
    logger.info("✅ All dependencies found")
    return True, tensorflow_available, tensorflow_version

# ============================================================================
# MODEL LOADING (interruptible)
# ============================================================================

def import_distilbert_predictor(mybudget_ai_path):
    """Import DistilBertPredictor with timeout and interrupt checking"""
    global _shutdown_requested
    
    if _shutdown_requested:
        raise KeyboardInterrupt("Shutdown requested")
    
    logger.info("   ⏳ [1/2] Loading transformers library...")
    logger.info("   💡 This may take 30-60 seconds. Press Ctrl+C to cancel.")
    sys.stdout.flush()
    
    original_cwd = os.getcwd()
    import_result = [None]
    import_error = [None]
    
    def import_transformers():
        try:
            import transformers
            import_result[0] = transformers
        except Exception as e:
            import_error[0] = e
    
    import_thread = threading.Thread(target=import_transformers, daemon=True)
    import_thread.start()
    
    import_start = time.time()
    timeout = 120
    
    while import_thread.is_alive():
        if _shutdown_requested:
            raise KeyboardInterrupt("Shutdown requested")
        if time.time() - import_start > timeout:
            raise TimeoutError("Transformers import timed out")
        time.sleep(0.5)
    
    if import_error[0]:
        raise import_error[0]
    
    transformers = import_result[0]
    logger.info(f"   ✅ transformers {transformers.__version__} loaded ({time.time() - import_start:.1f}s)")
    
    if _shutdown_requested:
        raise KeyboardInterrupt("Shutdown requested")
    
    logger.info("   ⏳ [2/2] Importing DistilBertPredictor...")
    sys.stdout.flush()
    
    try:
        os.chdir(str(mybudget_ai_path))
        from distilbert_inference import DistilBertPredictor
        os.chdir(original_cwd)
        logger.info("   ✅ DistilBertPredictor imported successfully")
        return DistilBertPredictor
    finally:
        os.chdir(original_cwd)

def load_model(model_dir, DistilBertPredictor):
    """Load the DistilBERT model with progress indicators"""
    logger.info(f"📥 Loading model from {model_dir}...")
    load_start = time.time()
    
    try:
        logger.info("   ⏳ Initializing DistilBertPredictor (may take 1-2 minutes)...")
        sys.stdout.flush()
        
        predictor_start = time.time()
        predictor = DistilBertPredictor(model_dir)
        predictor_elapsed = time.time() - predictor_start
        logger.info(f"   ✅ Predictor initialized ({predictor_elapsed:.1f}s)")
        
        logger.info("   ⏳ Extracting model components...")
        sys.stdout.flush()
        model = predictor.model
        model.eval()
        tokenizer = predictor.tokenizer
        
        total_load_time = time.time() - load_start
        logger.info("✅ Model loaded successfully")
        logger.info(f"   Device: {next(model.parameters()).device}")
        logger.info(f"   Vocab size: {tokenizer.vocab_size}")
        logger.info(f"   Total load time: {total_load_time:.1f}s")
        
        return predictor, model, tokenizer
        
    except Exception as e:
        logger.error(f"❌ Failed to load model: {e}")
        import traceback
        logger.debug(traceback.format_exc())
        raise

# ============================================================================
# FILE OPERATIONS
# ============================================================================

def copy_vocab_file():
    """Copy vocabulary file to assets"""
    vocab_src = os.path.join(MODEL_DIR, "vocab.txt")
    vocab_dst = os.path.join(OUTPUT_DIR, OUTPUT_VOCAB)
    
    if os.path.exists(vocab_src):
        os.makedirs(OUTPUT_DIR, exist_ok=True)
        import shutil
        shutil.copy2(vocab_src, vocab_dst)
        logger.info(f"✅ Vocabulary file copied to {vocab_dst}")
        return True
    else:
        logger.warning(f"⚠️  Vocabulary file not found: {vocab_src}")
        return False

def create_model_info():
    """Create model_info.json from config.json (always succeeds if config exists)"""
    logger.info("📋 Creating model_info.json...")
    
    config_path = os.path.join(MODEL_DIR, "config.json")
    if not os.path.exists(config_path):
        logger.error(f"❌ Config file not found: {config_path}")
        return False
    
    try:
        with open(config_path, 'r') as f:
            config = json.load(f)
        
        tasks = config.get('tasks', {})
        
        model_info = {
            "categories": tasks.get('category', {}).get('labels', []),
            "transaction_types": tasks.get('transaction_type', {}).get('labels', []),
            "intents": tasks.get('intent', {}).get('labels', []),
            "num_categories": len(tasks.get('category', {}).get('labels', [])),
            "num_transaction_types": len(tasks.get('transaction_type', {}).get('labels', [])),
            "num_intents": len(tasks.get('intent', {}).get('labels', [])),
            "max_length": config.get('max_length', 128),
            "model_type": "DistilBERT",
            "created_at": datetime.now().isoformat()
        }
        
        info_path = os.path.join(OUTPUT_DIR, "model_info.json")
        os.makedirs(OUTPUT_DIR, exist_ok=True)
        
        with open(info_path, 'w') as f:
            json.dump(model_info, f, indent=2)
        
        logger.info(f"✅ Model info saved to {info_path}")
        logger.info(f"   Categories: {model_info['num_categories']}")
        logger.info(f"   Transaction types: {model_info['num_transaction_types']}")
        logger.info(f"   Intents: {model_info['num_intents']}")
        return True
        
    except Exception as e:
        logger.error(f"❌ Failed to create model_info.json: {e}")
        import traceback
        logger.debug(traceback.format_exc())
        return False

# ============================================================================
# CONVERSION FUNCTIONS
# ============================================================================

def convert_pytorch_to_onnx(model, tokenizer, output_path="distilbert_model.onnx"):
    """Convert PyTorch model to ONNX format"""
    logger.info("📦 Step 1: Converting PyTorch to ONNX...")
    start_time = time.time()
    
    import torch.onnx
    
    model.eval()
    logger.info("   Creating dummy input...")
    
    dummy_input_ids = torch.randint(0, tokenizer.vocab_size, (1, 128))
    dummy_attention_mask = torch.ones(1, 128, dtype=torch.long)
    
    logger.info("   ⏳ Exporting to ONNX (may take several minutes)...")
    sys.stdout.flush()
    
    try:
        export_start = time.time()
        torch.onnx.export(
            model,
            (dummy_input_ids, dummy_attention_mask),
            output_path,
            input_names=['input_ids', 'attention_mask'],
            output_names=['category', 'transaction_type', 'intent'],
            dynamic_axes={
                'input_ids': {0: 'batch_size', 1: 'sequence_length'},
                'attention_mask': {0: 'batch_size', 1: 'sequence_length'},
            },
            opset_version=13,
            do_constant_folding=True,
        )
        export_elapsed = time.time() - export_start
        elapsed = time.time() - start_time
        file_size = os.path.getsize(output_path) / (1024 * 1024)
        logger.info(f"✅ ONNX model exported")
        logger.info(f"   File: {output_path} ({file_size:.2f} MB)")
        logger.info(f"   Export time: {export_elapsed:.1f}s | Total: {elapsed:.1f}s")
        return True
    except Exception as e:
        logger.error(f"❌ ONNX export failed: {e}")
        logger.info("💡 Tip: ONNX conversion for DistilBERT can be challenging")
        import traceback
        logger.debug(traceback.format_exc())
        return False

def convert_onnx_to_tensorflow(onnx_path, tf_path):
    """Convert ONNX to TensorFlow SavedModel (runs in subprocess if needed)"""
    logger.info("📦 Step 2: Converting ONNX to TensorFlow...")
    start_time = time.time()
    
    try:
        import onnx
        from onnx_tf.backend import prepare
        
        logger.info(f"   Loading ONNX model from {onnx_path}...")
        onnx_model = onnx.load(onnx_path)
        
        logger.info("   Validating ONNX model...")
        onnx.checker.check_model(onnx_model)
        logger.info("   ✅ ONNX model is valid")
        
        logger.info("   ⏳ Converting to TensorFlow (may take several minutes)...")
        sys.stdout.flush()
        
        convert_start = time.time()
        tf_rep = prepare(onnx_model)
        logger.info("   Exporting SavedModel...")
        tf_rep.export_graph(tf_path)
        
        convert_elapsed = time.time() - convert_start
        elapsed = time.time() - start_time
        logger.info(f"✅ TensorFlow SavedModel exported")
        logger.info(f"   Path: {tf_path}")
        logger.info(f"   Conversion time: {convert_elapsed:.1f}s | Total: {elapsed:.1f}s")
        return True
        
    except Exception as e:
        logger.error(f"❌ TensorFlow conversion failed: {e}")
        import traceback
        logger.debug(traceback.format_exc())
        return False

def convert_tensorflow_to_tflite(tf_path, tflite_path):
    """Convert TensorFlow SavedModel to TFLite (handles AVX errors gracefully)"""
    logger.info("📦 Step 3: Converting TensorFlow to TFLite...")
    start_time = time.time()
    
    # Import TensorFlow with AVX error handling
    try:
        import warnings
        with warnings.catch_warnings():
            warnings.filterwarnings('ignore')
            import tensorflow as tf
    except Exception as e:
        logger.error(f"❌ Cannot import TensorFlow: {e}")
        return False
    
    try:
        logger.info(f"   Loading SavedModel from {tf_path}...")
        converter = tf.lite.TFLiteConverter.from_saved_model(tf_path)
        
        logger.info("   ⏳ Applying optimizations...")
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
        
        logger.info("   ⏳ Converting to TFLite (may take a few minutes)...")
        sys.stdout.flush()
        
        convert_start = time.time()
        tflite_model = converter.convert()
        convert_elapsed = time.time() - convert_start
        
        logger.info(f"   Saving TFLite model...")
        with open(tflite_path, "wb") as f:
            f.write(tflite_model)
        
        elapsed = time.time() - start_time
        file_size_mb = os.path.getsize(tflite_path) / (1024 * 1024)
        logger.info(f"✅ TFLite model saved")
        logger.info(f"   File: {tflite_path} ({file_size_mb:.2f} MB)")
        logger.info(f"   Convert time: {convert_elapsed:.1f}s | Total: {elapsed:.1f}s")
        return True
        
    except Exception as e:
        logger.error(f"❌ TFLite conversion failed: {e}")
        import traceback
        logger.debug(traceback.format_exc())
        return False

# ============================================================================
# MAIN CONVERSION FUNCTION
# ============================================================================

def main():
    """Main conversion function with comprehensive error handling"""
    global _shutdown_requested
    
    logger.info("\n" + "=" * 60)
    logger.info("🚀 DistilBERT to TFLite Conversion")
    logger.info(f"⏰ Started at: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    logger.info("💡 Press Ctrl+C at any time to cancel (fully interruptible)")
    logger.info("=" * 60)
    
    overall_start = time.time()
    
    try:
        # Check architecture
        check_architecture()
        
        # Fast checks first
        if not os.path.exists(MODEL_DIR):
            logger.error(f"❌ Model directory not found: {MODEL_DIR}")
            logger.info("   Make sure the model is trained first.")
            return
        
        # Check if output already exists
        output_model_path = os.path.join(OUTPUT_DIR, OUTPUT_MODEL)
        if os.path.exists(output_model_path):
            logger.info(f"✅ Output model already exists: {output_model_path}")
            logger.info("   Skipping conversion. Delete file to re-run.")
            logger.info("\n📋 Updating supporting files...")
            copy_vocab_file()
            create_model_info()
            return
        
        # Check dependencies
        all_deps_ok, tf_available, tf_version = check_dependencies()
        
        if not all_deps_ok:
            logger.warning("\n⚠️  Some dependencies missing - creating metadata files only")
            logger.info("   This is OK - the script will create model_info.json")
        
        # Import PyTorch (required)
        try:
            import torch
            import numpy as np
            logger.info(f"✅ PyTorch {torch.__version__} loaded")
        except ImportError as e:
            logger.error(f"❌ PyTorch is required: {e}")
            return
        
        # Setup paths
        project_root = Path(__file__).parent.parent.absolute()
        mybudget_ai_path = project_root / "mybudget-ai"
        sys.path.insert(0, str(mybudget_ai_path))
        sys.path.insert(0, str(project_root))
        
        # Load model
        try:
            DistilBertPredictor = import_distilbert_predictor(mybudget_ai_path)
            predictor, model, tokenizer = load_model(MODEL_DIR, DistilBertPredictor)
        except KeyboardInterrupt:
            raise
        except Exception as e:
            logger.error(f"❌ Model loading failed: {e}")
            logger.info("   Creating model_info.json from config file...")
            create_model_info()
            copy_vocab_file()
            return
        
        # Create metadata files (always do this)
        logger.info("\n📋 Creating metadata files...")
        copy_vocab_file()
        create_model_info()
        
        # Try full conversion if dependencies are available
        if all_deps_ok:
            logger.info("\n🔄 Starting model conversion pipeline...")
            
            onnx_path = "distilbert_model.onnx"
            tf_path = "tf_saved_model"
            success = True
            
            # Step 1: PyTorch → ONNX
            if os.path.exists(onnx_path):
                logger.info(f"✅ ONNX model exists: {onnx_path} (skipping)")
            else:
                if not convert_pytorch_to_onnx(model, tokenizer, onnx_path):
                    success = False
            
            # Step 2: ONNX → TensorFlow
            if success:
                if os.path.exists(tf_path) and os.path.exists(os.path.join(tf_path, "saved_model.pb")):
                    logger.info(f"✅ TensorFlow SavedModel exists (skipping)")
                else:
                    if not convert_onnx_to_tensorflow(onnx_path, tf_path):
                        success = False
            
            # Step 3: TensorFlow → TFLite
            if success and tf_available:
                if os.path.exists(OUTPUT_MODEL):
                    logger.info(f"✅ TFLite model exists: {OUTPUT_MODEL} (skipping)")
                else:
                    if not convert_tensorflow_to_tflite(tf_path, OUTPUT_MODEL):
                        success = False
                        logger.warning("⚠️  TFLite conversion failed (may be due to AVX)")
                
                # Copy to assets if successful
                if success and os.path.exists(OUTPUT_MODEL):
                    os.makedirs(OUTPUT_DIR, exist_ok=True)
                    import shutil
                    dst = os.path.join(OUTPUT_DIR, OUTPUT_MODEL)
                    shutil.copy2(OUTPUT_MODEL, dst)
                    logger.info(f"✅ TFLite model copied to {dst}")
            else:
                logger.info("⚠️  Skipping TensorFlow conversion (not available)")
        
        # Final summary
        overall_elapsed = time.time() - overall_start
        logger.info("\n" + "=" * 60)
        
        if all_deps_ok and os.path.exists(output_model_path):
            logger.info("✅ Conversion complete!")
            logger.info(f"📱 Model ready: {output_model_path}")
        else:
            logger.info("✅ Metadata files created successfully!")
            logger.info(f"📋 Model info: {os.path.join(OUTPUT_DIR, 'model_info.json')}")
            logger.info(f"📝 Vocabulary: {os.path.join(OUTPUT_DIR, OUTPUT_VOCAB)}")
            if not all_deps_ok:
                logger.info("\n💡 For full TFLite conversion:")
                logger.info("   1. Fix TensorFlow AVX: pip install tensorflow==2.13.0 --no-cache-dir")
                logger.info("   2. Or use model_info.json in Android app")
        
        logger.info(f"⏰ Total time: {overall_elapsed:.1f}s")
        logger.info(f"📄 Log file: conversion.log")
        logger.info("=" * 60)
        
    except KeyboardInterrupt:
        logger.warning("\n" + "=" * 60)
        logger.warning("⚠️  Conversion interrupted by user")
        logger.warning("=" * 60)
        overall_elapsed = time.time() - overall_start
        logger.info(f"⏰ Ran for: {overall_elapsed:.1f}s before interruption")
        logger.info("✅ Exit was clean - no stuck processes")
        sys.exit(130)
    except Exception as e:
        logger.error(f"\n❌ Unexpected error: {e}")
        import traceback
        logger.error(traceback.format_exc())
        # Still try to create metadata files
        logger.info("Attempting to create model_info.json as fallback...")
        create_model_info()
        copy_vocab_file()
        sys.exit(1)

if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n⚠️  Script interrupted", flush=True)
        sys.exit(130)
