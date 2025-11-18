#!/bin/bash
# Script to convert PyTorch model using virtual environment

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🚀 Setting up virtual environment and converting model..."
echo ""

# Activate virtual environment
if [ ! -d "venv" ]; then
    echo "📦 Creating virtual environment..."
    python3 -m venv venv
fi

echo "🔧 Activating virtual environment..."
source venv/bin/activate

# Install PyTorch if not already installed
if ! python -c "import torch" 2>/dev/null; then
    echo "📥 Installing PyTorch (ARM64 compatible)..."
    pip install --upgrade pip setuptools wheel
    pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cpu
else
    echo "✅ PyTorch already installed"
fi

# Verify PyTorch
echo ""
echo "🔍 Verifying PyTorch installation..."
python -c "import torch; print(f'✅ PyTorch {torch.__version__} ready')"

# Run conversion
echo ""
echo "🔄 Converting model to PyTorch Mobile format..."
python3 convert_to_pytorch_mobile.py

echo ""
echo "✅ Conversion complete!"
echo "📦 Model file: app/src/main/assets/distilbert_model.ptl"

