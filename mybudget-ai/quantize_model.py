#!/usr/bin/env python3
"""
quantize_model.py

Quantize DistilBERT model for mobile deployment:
- Option 1: PyTorch INT8 quantization
- Option 2: Convert to TensorFlow Lite (.tflite)
- Option 3: Convert to GGUF format

Target: Reduce model size from 255 MB to < 64 MB for APK inclusion
"""

import os
import json
import logging
import torch
import torch.nn as nn
from typing import Dict, Optional
from pathlib import Path

# Import model class
import sys
sys.path.insert(0, os.path.dirname(__file__))
from train_distilbert import MultiTaskDistilBERT

# Configuration
MODEL_DIR = "models/distilbert_multitask_latest"
OUTPUT_DIR = "models/quantized"
QUANTIZATION_MODE = os.getenv("QUANT_MODE", "int8")  # int8, fp16, tflite, gguf

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(levelname)s - %(message)s"
)
logger = logging.getLogger(__name__)


def load_trained_model(model_dir: str) -> tuple:
    """Load trained model and config"""
    logger.info(f"📥 Loading model from {model_dir}")
    
    # Load config
    config_path = os.path.join(model_dir, "config.json")
    if not os.path.exists(config_path):
        raise FileNotFoundError(f"Config not found: {config_path}")
    
    with open(config_path, 'r') as f:
        config = json.load(f)
    
    tasks = config['tasks']
    model_name = config.get('model_name', 'distilbert-base-uncased')
    
    logger.info(f"   Tasks: {list(tasks.keys())}")
    logger.info(f"   Category labels: {len(tasks.get('category', {}).get('labels', []))}")
    
    # Load model - BERT base is saved via save_pretrained, task heads separately
    logger.info(f"   Loading base DistilBERT from {model_dir}...")
    from transformers import DistilBertForSequenceClassification
    
    # Initialize model architecture
    model = MultiTaskDistilBERT(model_name, tasks)
    
    # Load base BERT weights (saved via save_pretrained)
    bert_model = DistilBertForSequenceClassification.from_pretrained(
        model_dir,
        num_labels=768
    )
    model.bert = bert_model.bert  # Copy the loaded BERT
    model.bert.classifier = torch.nn.Identity()  # Remove classifier head
    
    # Load task heads
    task_heads_path = os.path.join(model_dir, "task_heads.pt")
    if os.path.exists(task_heads_path):
        model.task_heads.load_state_dict(torch.load(task_heads_path, map_location='cpu'))
        logger.info(f"   ✅ Task heads loaded from {task_heads_path}")
    else:
        logger.warning(f"   ⚠️  Task heads not found: {task_heads_path}")
    
    model.eval()
    logger.info(f"✅ Model loaded successfully")
    
    return model, config


def quantize_pytorch_int8(model: nn.Module) -> nn.Module:
    """Quantize PyTorch model to INT8"""
    logger.info("🔧 Quantizing model to INT8...")
    
    # Dynamic quantization (post-training)
    quantized_model = torch.quantization.quantize_dynamic(
        model,
        {nn.Linear, nn.Embedding},
        dtype=torch.qint8
    )
    
    logger.info("✅ Model quantized to INT8")
    return quantized_model


def quantize_pytorch_fp16(model: nn.Module) -> nn.Module:
    """Quantize PyTorch model to FP16"""
    logger.info("🔧 Quantizing model to FP16...")
    
    model_fp16 = model.half()
    
    logger.info("✅ Model quantized to FP16")
    return model_fp16


def convert_to_tflite(model: nn.Module, config: Dict, output_path: str):
    """Convert PyTorch model to TensorFlow Lite format"""
    logger.info("🔄 Converting to TensorFlow Lite...")
    
    try:
        import tensorflow as tf
    except ImportError:
        logger.error("❌ TensorFlow not installed. Install with: pip install tensorflow")
        return False
    
    logger.warning("⚠️  TFLite conversion requires PyTorch → ONNX → TensorFlow → TFLite")
    logger.warning("   This is a multi-step process. Consider using ONNX or direct PyTorch quantization.")
    
    # Step 1: Convert PyTorch to ONNX
    logger.info("   Step 1: Converting PyTorch to ONNX...")
    try:
        import torch.onnx
        onnx_path = output_path.replace('.tflite', '.onnx')
        
        # Dummy input for export
        dummy_input = torch.randint(0, 1000, (1, 128))  # Token IDs
        dummy_attention = torch.ones(1, 128)  # Attention mask
        
        torch.onnx.export(
            model,
            (dummy_input, dummy_attention),
            onnx_path,
            input_names=['input_ids', 'attention_mask'],
            output_names=['predictions'],
            dynamic_axes={'input_ids': {0: 'batch'}, 'attention_mask': {0: 'batch'}},
            opset_version=11
        )
        logger.info(f"   ✅ ONNX model saved: {onnx_path}")
    except Exception as e:
        logger.exception(f"   ❌ ONNX conversion failed: {e}")
        return False
    
    # Step 2: Convert ONNX to TensorFlow
    logger.info("   Step 2: Converting ONNX to TensorFlow...")
    try:
        import onnx
        from onnx_tf.backend import prepare
        
        onnx_model = onnx.load(onnx_path)
        tf_rep = prepare(onnx_model)
        tf_model_dir = output_path.replace('.tflite', '_tf')
        tf_rep.export_graph(tf_model_dir)
        logger.info(f"   ✅ TensorFlow model saved: {tf_model_dir}")
    except Exception as e:
        logger.exception(f"   ❌ TensorFlow conversion failed: {e}")
        logger.warning("   💡 Install onnx-tf: pip install onnx-tf")
        return False
    
    # Step 3: Convert TensorFlow to TFLite
    logger.info("   Step 3: Converting TensorFlow to TFLite...")
    try:
        converter = tf.lite.TFLiteConverter.from_saved_model(tf_model_dir)
        converter.optimizations = [tf.lite.Optimize.DEFAULT]  # Quantization
        tflite_model = converter.convert()
        
        with open(output_path, 'wb') as f:
            f.write(tflite_model)
        
        size_mb = os.path.getsize(output_path) / (1024 * 1024)
        logger.info(f"   ✅ TFLite model saved: {output_path}")
        logger.info(f"   📦 Size: {size_mb:.2f} MB")
        return True
    except Exception as e:
        logger.exception(f"   ❌ TFLite conversion failed: {e}")
        return False


def convert_to_gguf(model: nn.Module, config: Dict, output_path: str):
    """Convert PyTorch model to GGUF format"""
    logger.info("🔄 Converting to GGUF format...")
    
    logger.warning("⚠️  GGUF conversion is complex and requires specialized tools")
    logger.warning("   Recommended approach:")
    logger.warning("   1. Use llama.cpp convert script")
    logger.warning("   2. Or use ggml/gguf Python libraries")
    logger.warning("   3. For DistilBERT, may need custom conversion")
    
    # Save model in format suitable for GGUF conversion
    logger.info("   Saving model in PyTorch format for GGUF conversion...")
    
    # Save full model
    pt_path = output_path.replace('.gguf', '.pt')
    torch.save(model.state_dict(), pt_path)
    
    # Save config
    config_path = output_path.replace('.gguf', '_config.json')
    with open(config_path, 'w') as f:
        json.dump(config, f, indent=2)
    
    logger.info(f"   ✅ Model saved for GGUF conversion:")
    logger.info(f"      Model: {pt_path}")
    logger.info(f"      Config: {config_path}")
    logger.info(f"   💡 Next steps:")
    logger.info(f"      1. Use llama.cpp convert script")
    logger.info(f"      2. Or use gguf Python library: pip install gguf")
    
    return True


def calculate_model_size(model_path: str) -> float:
    """Calculate model file size in MB"""
    if os.path.exists(model_path):
        return os.path.getsize(model_path) / (1024 * 1024)
    return 0.0


def main():
    """Main quantization function"""
    logger.info("=" * 60)
    logger.info("🔧 Model Quantization Pipeline")
    logger.info("=" * 60)
    
    # Check model exists
    if not os.path.exists(MODEL_DIR):
        logger.error(f"❌ Model directory not found: {MODEL_DIR}")
        logger.error("   Train model first: python train_distilbert.py")
        return
    
    # Create output directory
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    
    # Load model
    try:
        model, config = load_trained_model(MODEL_DIR)
    except Exception as e:
        logger.exception(f"❌ Failed to load model: {e}")
        return
    
    # Calculate original size
    original_size = calculate_model_size(os.path.join(MODEL_DIR, "model.safetensors"))
    logger.info(f"📊 Original model size: {original_size:.2f} MB")
    
    # Quantize based on mode
    if QUANTIZATION_MODE == "int8":
        quantized_model = quantize_pytorch_int8(model)
        output_path = os.path.join(OUTPUT_DIR, "distilbert_int8.pt")
        torch.save(quantized_model.state_dict(), output_path)
        
        new_size = calculate_model_size(output_path)
        reduction = (1 - new_size / original_size) * 100
        logger.info(f"✅ INT8 quantized model saved: {output_path}")
        logger.info(f"📦 New size: {new_size:.2f} MB ({reduction:.1f}% reduction)")
        
    elif QUANTIZATION_MODE == "fp16":
        quantized_model = quantize_pytorch_fp16(model)
        output_path = os.path.join(OUTPUT_DIR, "distilbert_fp16.pt")
        torch.save(quantized_model.state_dict(), output_path)
        
        new_size = calculate_model_size(output_path)
        reduction = (1 - new_size / original_size) * 100
        logger.info(f"✅ FP16 quantized model saved: {output_path}")
        logger.info(f"📦 New size: {new_size:.2f} MB ({reduction:.1f}% reduction)")
        
    elif QUANTIZATION_MODE == "tflite":
        output_path = os.path.join(OUTPUT_DIR, "distilbert.tflite")
        success = convert_to_tflite(model, config, output_path)
        if success:
            new_size = calculate_model_size(output_path)
            reduction = (1 - new_size / original_size) * 100
            logger.info(f"✅ TFLite model saved: {output_path}")
            logger.info(f"📦 New size: {new_size:.2f} MB ({reduction:.1f}% reduction)")
        
    elif QUANTIZATION_MODE == "gguf":
        output_path = os.path.join(OUTPUT_DIR, "distilbert.gguf")
        convert_to_gguf(model, config, output_path)
        
    else:
        logger.error(f"❌ Unknown quantization mode: {QUANTIZATION_MODE}")
        logger.info("   Available modes: int8, fp16, tflite, gguf")
        return
    
    logger.info("=" * 60)
    logger.info("✅ Quantization complete!")
    logger.info(f"📁 Output directory: {OUTPUT_DIR}")
    logger.info("=" * 60)


if __name__ == "__main__":
    main()

