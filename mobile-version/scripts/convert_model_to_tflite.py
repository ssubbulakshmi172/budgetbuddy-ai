#!/usr/bin/env python3
"""
Convert DistilBERT PyTorch model to TensorFlow Lite format for Android deployment.

This script:
1. Loads the PyTorch DistilBERT model
2. Converts to TensorFlow SavedModel
3. Converts SavedModel to TFLite
4. Applies quantization for smaller model size

Usage:
    python convert_model_to_tflite.py --model_path ../mybudget-ai/models/distilbert_multitask_latest --output_dir ./app/src/main/assets
"""

import argparse
import os
import sys
import json
import logging
import time
from datetime import datetime
import torch
import numpy as np
from pathlib import Path

# Setup logging FIRST
# Suppress warnings from urllib3 and TensorFlow AVX
import warnings
warnings.filterwarnings('ignore', category=UserWarning, module='urllib3')
warnings.filterwarnings('ignore', message='.*AVX.*')

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

# Also suppress warnings in Python
import os
os.environ['TF_CPP_MIN_LOG_LEVEL'] = '2'  # Suppress TensorFlow INFO and WARNING

# Add parent directory to path to import distilbert_inference
sys.path.insert(0, str(Path(__file__).parent.parent.parent / "mybudget-ai"))

try:
    from distilbert_inference import DistilBertPredictor
except ImportError:
    print("Error: Could not import distilbert_inference. Make sure the model directory exists.")
    sys.exit(1)

try:
    import tensorflow as tf
    from tensorflow import keras
    from transformers import TFAutoModel
except ImportError:
    print("Error: TensorFlow and transformers required for conversion.")
    print("Install: pip install tensorflow transformers")
    sys.exit(1)


def convert_pytorch_to_tflite(model_path: str, output_dir: str):
    """
    Convert PyTorch DistilBERT model to TFLite format
    
    Args:
        model_path: Path to PyTorch model directory
        output_dir: Output directory for TFLite model
    """
    logger.info("=" * 60)
    logger.info("🚀 STARTING: convert_pytorch_to_tflite")
    logger.info(f"⏰ Started at: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    logger.info(f"📂 Model path: {model_path}")
    logger.info(f"📂 Output dir: {output_dir}")
    logger.info("=" * 60)
    start_time = time.time()
    
    logger.info(f"📥 Loading PyTorch model from {model_path}...")
    
    # Load the original model to get config
    logger.info("   Initializing DistilBertPredictor...")
    predictor = DistilBertPredictor(model_path)
    logger.info("✅ Model predictor initialized")
    
    # Load config
    logger.info("   Loading model configuration...")
    config_path = os.path.join(model_path, "config.json")
    with open(config_path, 'r') as f:
        model_config = json.load(f)
    
    tasks = model_config['tasks']
    model_name = model_config.get('model_name', 'distilbert-base-uncased')
    
    logger.info(f"✅ Model config loaded. Tasks: {list(tasks.keys())}")
    logger.info(f"   Model name: {model_name}")
    
    # Create output directory
    logger.info(f"   Creating output directory: {output_dir}")
    os.makedirs(output_dir, exist_ok=True)
    logger.info("✅ Output directory ready")
    
    # Method 1: Direct conversion using tf.lite.TFLiteConverter
    # Note: This is a simplified approach. For production, you may need to:
    # 1. Convert PyTorch -> ONNX -> TensorFlow -> TFLite
    # 2. Or use TensorFlow Lite Model Maker
    
    logger.warning("\n⚠️  Direct PyTorch to TFLite conversion is complex.")
    logger.info("Recommended approach:")
    logger.info("   1. Use ONNX as intermediate format")
    logger.info("   2. Or train the model directly in TensorFlow")
    logger.info("   3. Or use TensorFlow Lite Model Maker")
    
    # For now, create a placeholder conversion script
    # In production, use ONNX conversion:
    #   pip install onnx onnx-tf
    #   python -m onnx_tf.convert --model model.onnx --output tf_model
    
    logger.info("\n📝 Manual conversion steps:")
    logger.info("   1. Convert PyTorch -> ONNX: Use torch.onnx.export()")
    logger.info("   2. Convert ONNX -> TensorFlow: Use onnx-tf converter")
    logger.info("   3. Convert TensorFlow -> TFLite: Use TFLiteConverter")
    
    # Create a simple inference wrapper script that can be used
    logger.info("\n📋 Creating TFLite wrapper...")
    create_tflite_wrapper(model_path, output_dir, tasks)
    
    elapsed = time.time() - start_time
    logger.info("=" * 60)
    logger.info(f"✅ COMPLETED: convert_pytorch_to_tflite in {elapsed:.1f}s")
    logger.info("=" * 60)


def create_tflite_wrapper(model_path: str, output_dir: str, tasks: dict):
    """
    Create a wrapper script for TFLite inference
    This can be used as a reference for implementing TFLite inference
    """
    logger.info("   🎯 STARTING: create_tflite_wrapper")
    logger.info(f"   📝 Creating wrapper script in {output_dir}")
    
    wrapper_code = f'''"""
TFLite Inference Wrapper
Generated from PyTorch model at: {model_path}

Note: This requires the actual TFLite model file to be placed in assets/
"""
'''
    
    wrapper_path = os.path.join(output_dir, "tflite_inference_wrapper.py")
    with open(wrapper_path, 'w') as f:
        f.write(wrapper_code)
    
    logger.info(f"✅ Created wrapper script at {wrapper_path}")
    logger.info("\n📋 Next steps:")
    logger.info("   1. Convert your model using ONNX or TensorFlow")
    logger.info("   2. Place the .tflite file in app/src/main/assets/")
    logger.info("   3. Update TFLiteInferenceService.kt with correct input/output shapes")
    logger.info("   ✅ COMPLETED: create_tflite_wrapper")


def convert_via_onnx(model_path: str, output_dir: str):
    """
    Convert via ONNX (requires onnx and onnx-tf packages)
    """
    logger.info("=" * 60)
    logger.info("🚀 STARTING: convert_via_onnx")
    logger.info(f"⏰ Started at: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    logger.info(f"📂 Model path: {model_path}")
    logger.info(f"📂 Output dir: {output_dir}")
    logger.info("=" * 60)
    overall_start = time.time()
    
    try:
        logger.info("🔍 Checking ONNX dependencies...")
        import onnx
        import onnx_tf
        logger.info(f"   ✅ onnx {onnx.__version__} available")
        logger.info("   ✅ onnx-tf available")
    except ImportError as e:
        logger.error(f"⚠️  ONNX conversion not available: {e}")
        logger.info("   Install: pip install onnx onnx-tf")
        return False
    
    # Step 1: PyTorch -> ONNX
    logger.info("\n" + "-" * 60)
    logger.info("📦 STEP 1: Converting PyTorch -> ONNX")
    logger.info("-" * 60)
    step1_start = time.time()
    
    logger.info("   Loading PyTorch model...")
    predictor = DistilBertPredictor(model_path)
    logger.info("✅ Model loaded")
    
    # Export to ONNX
    logger.info("   Preparing dummy input tensor...")
    dummy_input = torch.randint(0, 1000, (1, 128))  # Example input
    logger.info(f"   Input shape: {dummy_input.shape}")
    onnx_path = os.path.join(output_dir, "model.onnx")
    
    logger.info(f"   🚀 Starting ONNX export to {onnx_path}...")
    logger.info("   ⏳ This may take several minutes...")
    try:
        torch.onnx.export(
            predictor.model,
            dummy_input,
            onnx_path,
            input_names=['input_ids'],
            output_names=['category', 'transaction_type', 'intent'],
            dynamic_axes={
                'input_ids': {0: 'batch_size'},
            }
        )
        step1_elapsed = time.time() - step1_start
        file_size = os.path.getsize(onnx_path) / (1024 * 1024)
        logger.info(f"✅ ONNX export complete! ({file_size:.2f} MB) in {step1_elapsed:.1f}s")
    except Exception as e:
        logger.error(f"❌ ONNX export failed: {e}")
        import traceback
        logger.error(traceback.format_exc())
        return False
    
    # Step 2: ONNX -> TensorFlow
    logger.info("\n" + "-" * 60)
    logger.info("📦 STEP 2: Converting ONNX -> TensorFlow")
    logger.info("-" * 60)
    step2_start = time.time()
    
    logger.info(f"   Loading ONNX model from {onnx_path}...")
    onnx_model = onnx.load(onnx_path)
    logger.info("   Validating ONNX model...")
    onnx.checker.check_model(onnx_model)
    logger.info("   ✅ ONNX model is valid")
    
    logger.info("   🚀 Converting to TensorFlow (this may take several minutes)...")
    tf_rep = onnx_tf.backend.prepare(onnx_model)
    tf_model_path = os.path.join(output_dir, "tf_model")
    tf_rep.export_graph(tf_model_path)
    
    step2_elapsed = time.time() - step2_start
    logger.info(f"✅ TensorFlow conversion complete! in {step2_elapsed:.1f}s")
    logger.info(f"   Saved to: {tf_model_path}")
    
    # Step 3: TensorFlow -> TFLite
    logger.info("\n" + "-" * 60)
    logger.info("📦 STEP 3: Converting TensorFlow -> TFLite")
    logger.info("-" * 60)
    step3_start = time.time()
    
    logger.info("   Loading TensorFlow SavedModel...")
    converter = tf.lite.TFLiteConverter.from_saved_model(tf_model_path)
    
    logger.info("   Applying optimizations (quantization)...")
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    
    logger.info("   🚀 Converting to TFLite format (this may take a few minutes)...")
    tflite_model = converter.convert()
    
    tflite_path = os.path.join(output_dir, "distilbert_model.tflite")
    logger.info(f"   Saving TFLite model to {tflite_path}...")
    
    with open(tflite_path, 'wb') as f:
        f.write(tflite_model)
    
    step3_elapsed = time.time() - step3_start
    file_size_mb = os.path.getsize(tflite_path) / (1024 * 1024)
    logger.info(f"✅ TFLite conversion complete! ({file_size_mb:.2f} MB) in {step3_elapsed:.1f}s")
    
    overall_elapsed = time.time() - overall_start
    logger.info("\n" + "=" * 60)
    logger.info(f"✅ COMPLETED: convert_via_onnx in {overall_elapsed:.1f}s")
    logger.info(f"📱 TFLite model ready: {tflite_path}")
    logger.info("=" * 60)
    return True


def quantize_model(tflite_path: str):
    """
    Apply post-training quantization to reduce model size
    """
    logger.info("=" * 60)
    logger.info("🚀 STARTING: quantize_model")
    logger.info(f"⏰ Started at: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    logger.info(f"📂 Model path: {tflite_path}")
    logger.info("=" * 60)
    start_time = time.time()
    
    logger.info(f"📥 Loading TFLite model from {tflite_path}...")
    
    # Load TFLite model
    logger.info("   Initializing TFLite interpreter...")
    interpreter = tf.lite.Interpreter(model_path=tflite_path)
    interpreter.allocate_tensors()
    logger.info("✅ Interpreter initialized")
    
    # Get input/output details
    logger.info("   Getting model input/output details...")
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()
    logger.info(f"   Input shape: {input_details[0]['shape']}")
    logger.info(f"   Outputs: {len(output_details)}")
    
    # Create representative dataset (you'll need actual data)
    logger.info("   Creating representative dataset for quantization...")
    def representative_dataset():
        # Use actual transaction narrations from your training data
        for _ in range(100):
            yield [np.random.randint(0, 1000, (1, 128), dtype=np.int32)]
    logger.info("✅ Representative dataset ready")
    
    # Quantize
    logger.info("   🚀 Starting quantization (this may take a few minutes)...")
    logger.info("   Configuring INT8 quantization...")
    converter = tf.lite.TFLiteConverter.from_saved_model(tflite_path.replace('.tflite', '_tf'))
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.representative_dataset = representative_dataset
    converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS_INT8]
    converter.inference_input_type = tf.int8
    converter.inference_output_type = tf.int8
    
    quantized_model = converter.convert()
    
    quantized_path = tflite_path.replace('.tflite', '_quantized.tflite')
    logger.info(f"   Saving quantized model to {quantized_path}...")
    with open(quantized_path, 'wb') as f:
        f.write(quantized_model)
    
    elapsed = time.time() - start_time
    file_size = os.path.getsize(quantized_path) / (1024 * 1024)
    original_size = os.path.getsize(tflite_path) / (1024 * 1024)
    reduction = ((original_size - file_size) / original_size) * 100
    
    logger.info("=" * 60)
    logger.info(f"✅ COMPLETED: quantize_model in {elapsed:.1f}s")
    logger.info(f"📱 Quantized model saved: {quantized_path}")
    logger.info(f"   Original size: {original_size:.2f} MB")
    logger.info(f"   Quantized size: {file_size:.2f} MB")
    logger.info(f"   Size reduction: {reduction:.1f}%")
    logger.info("=" * 60)


if __name__ == "__main__":
    logger.info("\n" + "=" * 60)
    logger.info("🚀 DistilBERT to TFLite Conversion Script")
    logger.info(f"⏰ Script started at: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    logger.info(f"📁 Working directory: {os.getcwd()}")
    logger.info("=" * 60)
    
    parser = argparse.ArgumentParser(description="Convert DistilBERT to TFLite")
    parser.add_argument(
        "--model_path",
        type=str,
        default="../mybudget-ai/models/distilbert_multitask_latest",
        help="Path to PyTorch model directory"
    )
    parser.add_argument(
        "--output_dir",
        type=str,
        default="./app/src/main/assets",
        help="Output directory for TFLite model"
    )
    parser.add_argument(
        "--use_onnx",
        action="store_true",
        help="Use ONNX conversion path (requires onnx packages)"
    )
    
    args = parser.parse_args()
    
    logger.info(f"\n📋 Arguments:")
    logger.info(f"   Model path: {args.model_path}")
    logger.info(f"   Output dir: {args.output_dir}")
    logger.info(f"   Use ONNX: {args.use_onnx}")
    logger.info("")
    
    script_start = time.time()
    
    if args.use_onnx:
        logger.info("🎯 Using ONNX conversion path")
        success = convert_via_onnx(args.model_path, args.output_dir)
        if success:
            tflite_path = os.path.join(args.output_dir, "distilbert_model.tflite")
            quantize_model(tflite_path)
    else:
        logger.info("🎯 Using direct conversion path")
        convert_pytorch_to_tflite(args.model_path, args.output_dir)
    
    script_elapsed = time.time() - script_start
    logger.info("\n" + "=" * 60)
    logger.info(f"✅ Script completed in {script_elapsed:.1f}s")
    logger.info(f"📄 Full log saved to: conversion.log")
    logger.info("=" * 60)

