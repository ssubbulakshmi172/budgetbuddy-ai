# Model Files

## Note: Large Model File Excluded from Git

The `model.safetensors` file (255 MB) exceeds GitHub's 100 MB file size limit and has been excluded from the repository.

## How to Obtain the Model

### Option 1: Train the Model Locally (Recommended)

Train the model yourself to ensure you have the latest version:

```bash
cd mybudget-ai
pip install -r requirements.txt
python3 train_distilbert.py
```

This will:
1. Load taxonomy from database or `categories.yml`
2. Load training data from `transactions_distilbert.csv`
3. Train the DistilBERT multi-task model
4. Save model files to `mybudget-ai/models/distilbert_multitask_latest/`

### Option 2: Download from Release/External Storage

If available, download the pre-trained model from:
- GitHub Releases (if uploaded separately)
- External storage link (if provided)
- Cloud storage bucket (if configured)

### Required Model Files

The following files are included in the repository:
- ✅ `config.json` - Model configuration
- ✅ `tokenizer_config.json` - Tokenizer configuration
- ✅ `special_tokens_map.json` - Special tokens mapping
- ✅ `task_heads.pt` - Task-specific classification heads
- ✅ `vocab.txt` - Vocabulary file
- ❌ `model.safetensors` - **Large file, excluded from Git** (must be generated/downloaded)

### Verification

After obtaining the model file, verify the directory structure:

```bash
ls -lh mybudget-ai/models/distilbert_multitask_latest/
```

You should see:
- `config.json`
- `tokenizer_config.json`
- `special_tokens_map.json`
- `task_heads.pt`
- `vocab.txt`
- `model.safetensors` ← **This file must be present for inference**

### Quick Start

If you just need to run inference and don't want to train:

1. Train the model once (takes ~10-30 minutes depending on hardware):
   ```bash
   cd mybudget-ai
   python3 train_distilbert.py
   ```

2. Or download the model file from a trusted source if available.

The application will automatically use the model for local inference once it's present.

