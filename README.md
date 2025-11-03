# BudgetBuddy - Automated AI-Based Financial Transaction Categorisation

An end-to-end autonomous transaction categorisation system that uses machine learning to classify financial transactions into user-defined categories without relying on third-party APIs.

## 🎯 Project Overview

BudgetBuddy is a production-ready transaction categorisation system that:
- **Autonomously categorises** raw transaction strings (UPI, bank descriptions)
- **Achieves 88.6% macro F1-score** (target: ≥90%)
- **Runs entirely in-house** - no external API dependencies
- **Customisable taxonomy** via YAML configuration
- **Explainable predictions** with confidence scores and feature attributions
- **Human-in-the-loop feedback** for continuous improvement
- **Mobile support** with on-device PyTorch Mobile inference

### Key Features

✅ **End-to-End Autonomous Categorisation**
- Ingest raw transaction data (Excel, CSV)
- AI-powered categorisation via DistilBERT multi-task model
- **Batch predictions** for faster processing of multiple transactions
- Automatic confidence scoring with transaction type and intent detection
- **Separate category and subcategory fields** stored in database
- **Filter by AI predictions** with distinct dropdown values

✅ **High Accuracy**
- Macro F1: 0.8859 (very close to 0.90 target)
- Weighted F1: 0.8922
- Per-class F1 scores ranging from 0.75-0.99
- 53 subcategories from hierarchical taxonomy

✅ **Customisable Taxonomy**
- Edit categories via `categories.yml` (no code changes)
- Support for 53 hierarchical subcategories
- Keyword-based matching with ML fallback
- Automatic dataset generation and balancing

✅ **Multi-Platform**
- **Web Application** (Spring Boot + Thymeleaf)
- **Android Mobile App** (Jetpack Compose + PyTorch Mobile)
- **Offline-first** mobile architecture

✅ **Explainability**
- Confidence scores for all predictions
- Probability distribution across all categories
- Top keywords per class (coefficient analysis)

---

## 🏗️ Architecture

```
┌─────────────────────┐
│  Spring Boot App   │  (Java + Thymeleaf)
│  Port: 8080        │
│  - Batch Predictions│
│  - Manual Categories│
└──────────┬──────────┘
           │ ProcessBuilder
           ▼
┌─────────────────────┐
│  Local Inference   │  (Python + DistilBERT)
│  inference_local.py│
│  - Batch Support   │
│  - Offline Mode    │
│  - No Flask needed │
└─────────────────────┘
           │
           ▼
┌─────────────────────┐
│  ML Model          │
│  DistilBERT        │
│  Multi-task        │
│  - Category        │
│  - Transaction Type│
│  - Intent          │
└─────────────────────┘
```

### Components

1. **Spring Boot Backend** (`src/main/java/`)
   - Transaction management
   - User interface (Thymeleaf templates)
   - Integration with ML service

2. **Local Inference Service** (`mybudget-ai/inference_local.py`)
   - Local model inference (no Flask server needed)
   - Called from Java via ProcessBuilder
   - Supports DistilBERT multi-task predictions
   - **Batch prediction support** for faster processing

3. **Training Pipeline** (`mybudget-ai/train_distilbert.py`)
   - DistilBERT multi-task training
   - Data preprocessing and evaluation
   - Report generation

4. **Dataset Maintenance** (`mybudget-ai/dataset_maintenance.py`)
   - Generate synthetic datasets matching categories.yml
   - Verify and auto-balance dataset coverage

5. **Mobile App** (`mobile-version/`)
   - Android app with on-device ML inference
   - PyTorch Mobile integration
   - Offline-first architecture

---

## 📋 Prerequisites

### Required Software

- **Java 17+** (for Spring Boot)
- **Python 3.9+** (for ML service)
- **MySQL 8.0+** (for data persistence)
- **Gradle 7+** (included via wrapper)
- **Android Studio Hedgehog+** (for mobile app)
- **JDK 17** (Corretto 17.0.13) for Android

### Python Dependencies

```bash
cd mybudget-ai
pip install -r requirements.txt
```

Key packages:
- `torch` (PyTorch for DistilBERT)
- `transformers` (Hugging Face transformers library)
- `scikit-learn` (evaluation metrics)
- `pandas` (data processing)
- `numpy` (numerical operations)
- `PyYAML` (YAML configuration)

---

## 🚀 Quick Start

### 1. Setup Database

```bash
# MySQL setup
mysql -u root -p
CREATE DATABASE budgetbuddy_app;
```

Update `src/main/resources/application.properties`:
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/budgetbuddy_app
spring.datasource.username=your_username
spring.datasource.password=your_password
```

### 2. Setup Python Virtual Environment

```bash
cd mybudget-ai
python3 -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate
pip install -r requirements.txt
```

### 3. Generate or Obtain Training Dataset

**Option A: Use Existing Dataset**
- `transactions_distilbert.csv` - Real transaction data
- Automatically discovers and merges all `transactions_*.csv` files

**Option B: Generate Synthetic Dataset**
```bash
cd mybudget-ai
source venv/bin/activate
python3 dataset_maintenance.py generate
# Generates transactions_synthetic.csv and transactions_maximal.csv
```

### 4. Train the Model

**Note**: The pre-trained model file (`model.safetensors`, 255 MB) is excluded from the repository due to GitHub's file size limit.

**Train the Model** (~10-30 minutes):
```bash
cd mybudget-ai
source venv/bin/activate
python3 train_distilbert.py
```

**What Training Does:**
1. Loads `transactions_distilbert.csv` (real data)
2. Auto-discovers and merges all `transactions_*.csv` files
3. Applies UPI preprocessing (removes IDs, bank tags, etc.)
4. Trains DistilBERT multi-task model (53 subcategories)
5. Generates evaluation reports
6. Saves model to `models/distilbert_multitask_latest/`

### 5. Verify Inference

Test local inference directly:
```bash
cd mybudget-ai
source venv/bin/activate
python3 inference_local.py "UPI-CHILD CARE PHARMACY-VYAPAR.171813425600@HDFCBANK-HDFC.COMERUPI-112425210473-MEDICAL"
```

**Example Output:**
```json
{
  "model_type": "DistilBERT",
  "transaction_type": "P2C",
  "predicted_category": "Personal & Lifestyle",
  "predicted_subcategory": "Personal Health & Wellness",
  "intent": "purchase",
  "confidence": {
    "category": 0.85,
    "transaction_type": 0.92,
    "intent": 0.88
  }
}
```

**Note:** The response now includes separate `predicted_category` (top-level) and `predicted_subcategory` fields. If no subcategory exists, `predicted_subcategory` will be `null`.

**Note:** Update `application.properties` to use venv Python:
```properties
python.command=mybudget-ai/venv/bin/python3
```

### 6. Start Spring Boot Application

```bash
# From project root
./gradlew bootRun
```

Application will start on `http://localhost:8080`

### 7. Test the System

1. **Import Transactions:**
   - Navigate to `http://localhost:8080/transactions/upload`
   - Upload Excel file (.xls or .xlsx format)
   - System automatically categorises using AI

2. **View and Filter Predictions:**
   - Navigate to `http://localhost:8080/transactions/filter-form`
   - **Filter by AI Predicted Category** - dropdown with distinct categories from database
   - **Filter by AI Predicted Subcategory** - dropdown with distinct subcategories
   - Filter by month, year, user, amount, narration, and manual categories
   - View transactions with both predicted categories and subcategories

3. **Dashboard:**
   - Navigate to `http://localhost:8080/dashboard`
   - View 6-month category comparison bar chart
   - View spending analytics and recent transactions

---

## 🤖 ML Training Pipeline

### Training Flow Overview

Multi-task fine-tuning of DistilBERT for transaction categorization:
- **Task 1:** Transaction Type (P2C, P2P, P2Business)
- **Task 2:** Category (53 subcategories from categories.yml)
- **Task 3:** Intent (purchase, transfer, refund, subscription, bill_payment)

### Complete Training Pipeline

```
Step 1: LOAD & PREPARE DATA
├─> Load transactions_distilbert.csv (real data)
├─> Auto-discover and merge all transactions_*.csv files
├─> Remove duplicates
├─> Apply UPI preprocessing
│   └─> Clean narrations, remove IDs, normalize text
└─> Normalize category names to match taxonomy

Step 2: LOAD TAXONOMY
├─> Try Database first (categories_keywords table)
│   └─> Extract distinct category names
└─> Try categories.yml (fallback/merge)
    └─> Extract subcategories from hierarchical structure
        └─> Create flat names: "TopCategory / Subcategory"
        └─> Result: 53 categories

Step 3: PREPARE TASKS
├─> Task 1: Transaction Type (P2C, P2P, P2Business)
├─> Task 2: Category (53 subcategories)
└─> Task 3: Intent (5 classes)

Step 4: SPLIT DATA
├─> Split texts: 80% train, 20% test
└─> Create train/test datasets

Step 5: TRAIN MODEL
├─> Load DistilBERT base model
├─> Create multi-task heads
├─> Train for 4 epochs
├─> Evaluate on test set
└─> Save best model

Step 6: SAVE MODEL
├─> Save to models/distilbert_multitask_YYYYMMDD_HHMMSS/
└─> Copy to models/distilbert_multitask_latest/
```

### Training Configuration

**File:** `mybudget-ai/train_distilbert.py`

```python
DATA_FILE = "transactions_distilbert.csv"           # Main dataset
USE_SYNTHETIC_DATA = True                            # Auto-merge all CSV files
USE_PREPROCESSING = True                             # Enable UPI preprocessing
MODEL_NAME = "distilbert-base-uncased"               # Base model
BATCH_SIZE = 16                                      # Training batch size
EPOCHS = 4                                           # Number of epochs
LEARNING_RATE = 2e-5                                 # Learning rate
```

### Multi-Task Learning Architecture

```
Input Text
    ↓
[DistilBERT Encoder] → 768-dim embeddings
    ↓
    ├─> [Task Head 1] → Transaction Type (3 classes)
    ├─> [Task Head 2] → Category (53 classes)
    └─> [Task Head 3] → Intent (5 classes)
```

**Loss Calculation:**
- Each task has its own CrossEntropyLoss
- Final loss = average of all task losses
- All tasks trained jointly (multi-task learning)

### Running Training

```bash
cd mybudget-ai
source venv/bin/activate
python3 train_distilbert.py
```

Training will:
1. Load and prepare data (auto-discovers all CSV files)
2. Extract taxonomy from categories.yml (53 subcategories)
3. Train multi-task model for 4 epochs
4. Save best model based on test F1 score
5. Generate metrics report

### Python File Usage

#### 1. `train_distilbert.py` - Training Script

**Purpose:** Train multi-task DistilBERT model on transaction data.

**Usage:**
```bash
cd mybudget-ai
source venv/bin/activate
python3 train_distilbert.py
```

**Output:**
- Model saved to `models/distilbert_multitask_YYYYMMDD_HHMMSS/`
- Metrics saved to `reports/distilbert_metrics_YYYYMMDD_HHMMSS.json`
- Logs saved to `logs/train_distilbert_YYYYMMDD_HHMMSS.log`

#### 2. `inference_local.py` - Standalone Inference Script

**Purpose:** Command-line inference tool, called from Java or used standalone.

**Usage - Single Prediction:**
```bash
cd mybudget-ai
source venv/bin/activate
python3 inference_local.py "UPI-STARBUCKS-COFFEE-1234567890@paytm"
```

**Output (JSON to stdout):**
```json
{
  "model_type": "DistilBERT",
  "transaction_type": "P2C",
  "predicted_category": "Dining & Restaurants",
  "predicted_subcategory": "Dining / Restaurants",
  "intent": "purchase",
  "confidence": {
    "category": 0.85,
    "transaction_type": 0.92,
    "intent": 0.88
  }
}
```

**Note:** Categories are now split into separate `predicted_category` (top-level) and `predicted_subcategory` fields. Both are stored in the database.

**Java Integration:**
```java
ProcessBuilder pb = new ProcessBuilder(
    "python3", 
    "inference_local.py", 
    transactionNarration
);
Process process = pb.start();
// Read JSON from stdout
```

#### 3. `distilbert_inference.py` - Inference Module

**Purpose:** Python module for programmatic inference.

**Usage:**
```python
from distilbert_inference import get_predictor

predictor = get_predictor()
result = predictor.predict("UPI-STARBUCKS-COFFEE-1234567890@paytm")
# Note: distilbert_inference returns full category path
# inference_local.py splits it into category and subcategory
print(result["category"])  # "Dining & Restaurants / Dining / Restaurants"
```

#### 4. `preprocessing_utils.py` - Preprocessing Utilities

**Purpose:** Text preprocessing functions for UPI transaction narrations.

**Main Function:**
```python
from preprocessing_utils import preprocess_upi_narration

narration = "UPI-CHILD CARE PHARMACY-VYAPAR.171813425600@HDFCBANK-HDFC.COMERUPI-112425210473-MEDICAL"
cleaned = preprocess_upi_narration(narration)
print(cleaned)  # "CHILD CARE PHARMACY MEDICAL"
```

**Features:**
- Removes UPI prefixes, bank tags, transaction IDs
- Normalizes stock market transactions
- Preserves P2P transaction clues
- Normalizes common misspellings

#### 5. `dataset_maintenance.py` - Dataset Maintenance

**Purpose:** Generate and verify synthetic transaction datasets.

**Command Line Usage:**
```bash
# Generate new dataset
python3 dataset_maintenance.py generate

# Verify and balance dataset
python3 dataset_maintenance.py verify

# Both operations
python3 dataset_maintenance.py both
```

**Python API:**
```python
from dataset_maintenance import generate_dataset, verify_and_balance_dataset

# Generate dataset (50 samples per category)
generate_dataset(num_per_category=50)

# Verify and balance
result = verify_and_balance_dataset()
```

**Features:**
- Generates synthetic transactions matching `categories.yml`
- Verifies coverage across all 53 subcategories
- Auto-balances dataset by generating missing samples
- Saves to `transactions_synthetic.csv` and `transactions_maximal.csv`

---

## 📱 Mobile App Setup

### Overview

BudgetBuddy Mobile is an Android app that provides on-device transaction categorization using PyTorch Mobile. Features:
- On-device ML inference (no internet required)
- Manual transaction entry
- Bulk import from Excel/CSV files
- Category management
- Spending analytics
- Offline-first architecture

### Prerequisites

- Android Studio Hedgehog or later
- JDK 17 (Corretto 17.0.13)
- Android SDK (API 26+)
- ADB (Android Debug Bridge)
- Python 3.9+ with PyTorch (for model conversion)

### Setup Steps

1. **Open Project**
   ```bash
   cd mobile-version
   # Open in Android Studio
   ```

2. **Generate ML Model**
   - See "Model Setup for Mobile" section below
   - Run: `python3 convert_to_pytorch_mobile.py`
   - Place model file: `app/src/main/assets/distilbert_model.ptl`

3. **Sync Gradle**
   - File → Sync Project with Gradle Files
   - Or: `./gradlew build`

4. **Build and Install**
   ```bash
   ./gradlew assembleDebug
   ./gradlew installDebug
   ```

### Architecture

```
┌─────────────────────────────────┐
│  Jetpack Compose UI            │
│  - Screens (Add, Dashboard, etc)│
└──────────────┬──────────────────┘
               │
┌──────────────▼──────────────────┐
│  ViewModels                     │
│  - State management            │
│  - Business logic              │
└──────────────┬──────────────────┘
               │
┌──────────────▼──────────────────┐
│  Repository Layer               │
│  - Room Database                │
│  - File Import                  │
└──────────────┬──────────────────┘
               │
┌──────────────▼──────────────────┐
│  ML Service                     │
│  - PyTorch Mobile               │
│  - On-device inference          │
└─────────────────────────────────┘
```

### Configuration

**Gradle Properties** (`gradle.properties`):
```
org.gradle.jvmargs=-Xmx8192m -XX:MaxMetaspaceSize=512m
org.gradle.java.home=/path/to/jdk17
```

### Viewing Logs

```bash
# View recent logs
~/Library/Android/sdk/platform-tools/adb logcat -d | tail -20

# Filter by app
~/Library/Android/sdk/platform-tools/adb logcat -d | grep -i "BudgetBuddy" | tail -20

# Real-time logs
~/Library/Android/sdk/platform-tools/adb logcat | grep "BudgetBuddy"
```

**Common Log Tags:**
- `BudgetBuddyApp` - Application lifecycle
- `AddTransaction` - Transaction creation
- `FileImporter` - Excel/CSV import
- `PyTorchMobile` - ML model inference
- `DATABASE_DEBUG` - Database operations

---

## 🔄 Model Setup for Mobile

### ⚠️ Important: Model File Too Large for GitHub

The PyTorch Mobile model file (`distilbert_model.ptl`) is **~255 MB**, which exceeds GitHub's 100 MB file size limit. Therefore, **the model file is NOT committed to git** and must be generated locally.

### Prerequisites

1. **Python 3.9+** with PyTorch installed
2. **Trained DistilBERT model** at: `../mybudget-ai/models/distilbert_multitask_latest/`
3. **PyTorch Mobile** library (will be installed during conversion)

### Generating the Model File

#### Step 1: Install Dependencies

```bash
cd mobile-version
pip install torch torchvision torchaudio
```

#### Step 2: Run Conversion Script

The conversion script will:
- Load the trained DistilBERT model
- Convert it to TorchScript
- Optimize for mobile with INT8 quantization
- Save as `.ptl` (PyTorch Lite) format

```bash
cd mobile-version
python3 convert_to_pytorch_mobile.py
```

**Expected Output:**
```
🚀 PyTorch Mobile Conversion
📥 Loading model from ../mybudget-ai/models/distilbert_multitask_latest/...
⏳ Converting to TorchScript...
✅ Model traced successfully
💾 Saving optimized model to app/src/main/assets/distilbert_model.ptl...
✅ CONVERSION COMPLETE!
📦 Model file: app/src/main/assets/distilbert_model.ptl
📊 Model size: ~255 MB
```

#### Step 3: Verify Model File

After conversion, verify the file exists:
```bash
ls -lh mobile-version/app/src/main/assets/distilbert_model.ptl
```

You should see a file ~255 MB in size.

### Model File Location

The model file should be placed in:
```
mobile-version/app/src/main/assets/distilbert_model.ptl
```

**Note:** The `assets/` directory is already in git, but `distilbert_model.ptl` is ignored via `.gitignore`.

### Required Files in Assets

1. ✅ `distilbert_model.ptl` - Generated by conversion script (not in git)
2. ✅ `model_info.json` - Model metadata (committed)
3. ✅ `vocab.txt` - Vocabulary file (committed)

### Troubleshooting

#### Model Directory Not Found

**Error:** `Model directory not found`

**Solution:** Ensure the trained model exists:
```bash
ls ../mybudget-ai/models/distilbert_multitask_latest/
```

Should contain:
- `config.json`
- `model.safetensors` or `pytorch_model.bin`
- `tokenizer.json`
- `vocab.txt`

#### Import Errors

**Error:** `ImportError: No module named 'distilbert_inference'`

**Solution:** The script automatically adds the project paths. If it fails:
```bash
export PYTHONPATH="${PYTHONPATH}:../mybudget-ai"
python3 convert_to_pytorch_mobile.py
```

#### Quantization Backend Errors

**Error:** Backend optimization fails

**Solution:** The script will automatically fall back to non-quantized model. This is fine - the model will work but be slightly larger.

---

## 📊 Model Performance

### Latest Evaluation Results

**Dataset:**
- **Real Data:** 3,000 samples (`transactions_distilbert.csv`)
- **Synthetic Data:** 7,000+ samples (`transactions_synthetic.csv`)
- **Total Training Samples:** ~10,000+ (with preprocessing)
- **Test samples:** ~2,000 (20% split)
- **Categories:** 53 subcategories

**Metrics:**
- **Macro F1:** 0.8859 (target: ≥0.90)
- **Weighted F1:** 0.8922
- **Accuracy:** 0.8898

**Detailed Reports:**
- Confusion Matrix: `mybudget-ai/reports/confusion_matrix_*.png`
- Evaluation Metrics: `mybudget-ai/reports/distilbert_metrics_*.json`
- Training Logs: `mybudget-ai/logs/train_distilbert_*.log`

---

## 🔧 Customisation

### Modify Taxonomy

The system supports **dual-source taxonomy** - categories can be managed via:
1. **Database** (Primary): UI-based category management via `/categories`
2. **YAML File** (Secondary/Fallback): `mybudget-ai/categories.yml`

**Edit via YAML File:**
Edit `mybudget-ai/categories.yml`:

```yaml
categories:
  - name: Household & Pets
    subcategories:
      - name: Groceries / Household Supplies
        keywords:
          - grocery
          - supermarket
          - vegetables
          - fruits
          - milk
          - meat
  - name: Dining & Restaurants
    subcategories:
      - name: Dining / Restaurants
        keywords:
          - restaurant
          - cafe
          - food
          - starbucks
```

**After modification:**
1. Regenerate dataset: `python3 dataset_maintenance.py generate`
2. Retrain model: `python3 train_distilbert.py`
3. Regenerate mobile model: `python3 convert_to_pytorch_mobile.py` (mobile)

### Adjust Model Parameters

Edit `mybudget-ai/train_distilbert.py`:

```python
EPOCHS = 4              # Training epochs
LEARNING_RATE = 2e-5    # Learning rate
BATCH_SIZE = 16         # Batch size
```

---

## 📡 Local Inference

The application uses local inference (no API server needed). Inference is performed via Python script called from Java using ProcessBuilder.

### How It Works

1. **Java Service** (`LocalModelInferenceService`) calls Python script
2. **Python Script** (`inference_local.py`) loads DistilBERT model
3. **Model Prediction** returns JSON with category, transaction_type, intent, and confidence
4. **Java Service** parses JSON and returns results

### Configuration

In `application.properties`:
```properties
inference.mode=local
python.command=python3
python.inference.script=mybudget-ai/inference_local.py
```

### Testing Locally

**Single Prediction:**
```bash
cd mybudget-ai
python3 inference_local.py "UPI/PAY/1234567890/STARBUCKS/txn@paytm"
```

**Example Output:**
```json
{
  "description": "UPI/PAY/1234567890/STARBUCKS/txn@paytm",
  "model_type": "DistilBERT",
  "transaction_type": "P2C",
  "predicted_category": "Dining & Food Delivery",
  "predicted_subcategory": "Dining / Restaurants",
  "intent": "purchase",
  "confidence": {
    "transaction_type": 0.92,
    "category": 0.85,
    "intent": 0.88
  }
}
```

**Clearing Corporation Transaction Example:**
```bash
cd mybudget-ai
python3 inference_local.py "ACH D- INDIAN CLEARING CORP-000000RZVBRM"
```

**Output:**
```json
{
  "description": "ACH D- INDIAN CLEARING CORP-000000RZVBRM",
  "model_type": "DistilBERT",
  "transaction_type": "P2P",
  "predicted_category": "Investments & Finance",
  "predicted_subcategory": "Stocks & Bonds",
  "intent": "transfer",
  "confidence": {
    "transaction_type": 0.3775103688240051,
    "category": 0.02152189053595066,
    "intent": 0.1968216747045517
  }
}
```

**Note:** The preprocessing preserves "INDIAN CLEARING CORP" (removes transaction codes like `-000000RZVBRM`) so keyword matching can work correctly with keywords like "indian clearing" defined in `categories.yml`.

---

## 🗄️ Dataset Maintenance

### Auto-Discovery

The training script automatically discovers and loads all CSV files matching `transactions_*.csv` in the `mybudget-ai/` directory:
- `transactions_distilbert.csv` (real data)
- `transactions_synthetic.csv` (synthetic data)
- `transactions_maximal.csv` (merged dataset)
- Any other `transactions_*.csv` files you add

### Generate Synthetic Dataset

```bash
cd mybudget-ai
source venv/bin/activate
python3 dataset_maintenance.py generate
```

This generates:
- `transactions_synthetic.csv` - Synthetic transactions (50 per category)
- `transactions_maximal.csv` - Complete merged dataset

### Verify Dataset Coverage

```bash
python3 dataset_maintenance.py verify
```

Checks:
- Coverage of all 53 subcategories
- Sample distribution across categories
- Missing or low-coverage categories
- Auto-generates missing samples

### Dataset Format

CSV files should have columns:
- `narration` - Transaction description text
- `transaction_type` - P2C, P2P, or P2Business
- `category` - Category name (will be mapped to taxonomy)
- `intent` - purchase, transfer, refund, subscription, or bill_payment

---

## 🐛 Troubleshooting

### Local Inference Errors

If inference fails, check:

1. **Python script exists:**
   ```bash
   ls -lh mybudget-ai/inference_local.py
   ```

2. **Model exists:**
   ```bash
   ls -lh mybudget-ai/models/distilbert_multitask_latest/
   ```

3. **Test directly:**
   ```bash
   cd mybudget-ai
   python3 inference_local.py "test transaction"
   ```

If model missing, train it:
```bash
cd mybudget-ai
python3 train_distilbert.py
```

### Database Connection Issues

Verify MySQL is running:
```bash
mysqladmin -u root -p status
```

Check `application.properties` has correct credentials.

### Mobile App Issues

**Build Failures:**
```bash
cd mobile-version
./gradlew clean
./gradlew build
```

**ML Predictions Fail:**
- Verify `distilbert_model.ptl` exists in assets
- Check Logcat for PyTorchMobile errors
- Ensure model conversion completed successfully

**View Logs:**
```bash
~/Library/Android/sdk/platform-tools/adb logcat | grep "BudgetBuddy"
```

### Training Issues

**Out of Memory:**
- Reduce `BATCH_SIZE` in `train_distilbert.py`
- Use smaller dataset

**Import Errors:**
```bash
cd mybudget-ai
source venv/bin/activate
pip install -r requirements.txt
```

---

## 📁 Project Structure

```
budgetbuddy-ai/
├── src/main/java/com/budgetbuddy/
│   ├── controller/          # Spring Boot controllers
│   ├── service/            # Business logic
│   ├── model/              # JPA entities
│   └── repository/         # Data access layer
├── src/main/resources/
│   ├── templates/          # Thymeleaf HTML templates
│   └── static/             # CSS, JS assets
├── mybudget-ai/            # ML Service
│   ├── inference_local.py  # Local inference script
│   ├── train_distilbert.py # Training pipeline
│   ├── dataset_maintenance.py  # Dataset generation
│   ├── preprocessing_utils.py  # Text preprocessing
│   ├── distilbert_inference.py # Inference module
│   ├── categories.yml      # Taxonomy configuration
│   ├── transactions_*.csv  # Training datasets
│   ├── models/             # Saved model files
│   ├── reports/            # Evaluation reports
│   └── logs/               # Training logs
├── mobile-version/         # Android App
│   ├── app/
│   │   └── src/main/
│   │       ├── java/       # Kotlin source
│   │       └── assets/     # ML model files
│   ├── convert_to_pytorch_mobile.py  # Model conversion
│   └── build.gradle
├── build.gradle            # Gradle dependencies
└── README.md               # This file
```

---

## 📚 Additional Resources

### Key Files

- **Categories:** `mybudget-ai/categories.yml`
- **Training Script:** `mybudget-ai/train_distilbert.py`
- **Inference:** `mybudget-ai/inference_local.py`
- **Dataset Maintenance:** `mybudget-ai/dataset_maintenance.py`
- **Preprocessing:** `mybudget-ai/preprocessing_utils.py`
- **Mobile Model Conversion:** `mobile-version/convert_to_pytorch_mobile.py`

### Log Locations

- **Application Logs:** `budgetbuddy.log` (project root)
- **Training Logs:** `mybudget-ai/logs/train_distilbert_*.log`
- **Mobile Logs:** Use ADB logcat (see Mobile App Setup section)

---

## 🤝 Contributing

### Development Setup

1. **Clone repository:**
   ```bash
   git clone https://github.com/ssubbulakshmi172/budgetbuddy-ai.git
   cd budgetbuddy-ai
   ```

2. **Install dependencies** (see Prerequisites section)

3. **Setup MySQL database** (see Quick Start section)

4. **Generate training data:**
   ```bash
   cd mybudget-ai
   python3 dataset_maintenance.py generate
   ```

5. **Train model:**
   ```bash
   python3 train_distilbert.py
   ```

6. **Start services** (see Quick Start section)

---

## 📝 License

This project is developed for educational/competition purposes. Dataset sourced from Hugging Face (`deepakjoshi1606/mock-upi-txn-data`).

---

## 🔗 Repository

**GitHub:** https://github.com/ssubbulakshmi172/budgetbuddy-ai

---

## 🙏 Acknowledgments

- **Dataset:** Hugging Face `deepakjoshi1606/mock-upi-txn-data`
- **ML Libraries:** PyTorch, Transformers, Scikit-learn
- **Web Framework:** Spring Boot
- **Mobile Framework:** Jetpack Compose, PyTorch Mobile
- **UI:** Bootstrap 5, Chart.js, Thymeleaf

---

## 📊 Compliance Status

**Overall Compliance: ~90%** with competition requirements

✅ **Completed Requirements:**
- End-to-End Autonomous Categorisation (100%)
- Customisable Taxonomy via YAML (100%)
- Evaluation Reports with Metrics (100%)
- Explainability Features (100%)
- Feedback Loop Mechanism (100%)
- Mobile Support (100%)

⚠️ **Near Complete:**
- Macro F1 Score: 0.8859 (target: ≥0.90) - **97% of target**

---

**Last Updated:** November 2025  
**Version:** 1.3.0  
**Status:** Production Ready

### Recent Updates

#### v1.3.0 (November 2025)
- ✨ **Unified Documentation:** Merged all docs into comprehensive README
- 🔄 **Dataset Auto-Discovery:** Automatically loads all `transactions_*.csv` files
- 📊 **Dataset Maintenance:** Unified script for generation and verification
- 🎯 **53 Subcategories:** Hierarchical taxonomy support
- 🔧 **P2P Detection:** Enhanced transaction type detection with clue preservation
- 📱 **Mobile Support:** Complete Android app with on-device inference

#### v1.2.0 (November 2025)
- ✨ **Mixed Real + Synthetic Data**: Automatically merges real and synthetic datasets
- 🔧 **UPI Preprocessing**: Removes transaction IDs, bank tags for better accuracy
- 📊 **Enhanced Training**: Improved accuracy with larger, cleaner dataset
