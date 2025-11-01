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

### Key Features

✅ **End-to-End Autonomous Categorisation**
- Ingest raw transaction data (Excel, CSV)
- AI-powered categorisation via TF-IDF + Logistic Regression
- Automatic confidence scoring

✅ **High Accuracy**
- Macro F1: 0.8859 (very close to 0.90 target)
- Weighted F1: 0.8922
- Per-class F1 scores ranging from 0.75-0.99

✅ **Customisable Taxonomy**
- Edit categories via `categories.yml` (no code changes)
- Support for 10+ categories (Dining, Groceries, Healthcare, etc.)
- Keyword-based matching with ML fallback

✅ **Explainability**
- Confidence scores for all predictions
- Probability distribution across all categories
- Top keywords per class (coefficient analysis)

✅ **Feedback Loop**
- API endpoint for user corrections
- Automatic retraining integration
- CSV-based feedback storage

---

## 🏗️ Architecture

```
┌─────────────────────┐
│  Spring Boot App   │  (Java + Thymeleaf)
│  Port: 8080        │
└──────────┬──────────┘
           │ ProcessBuilder
           ▼
┌─────────────────────┐
│  Local Inference   │  (Python + DistilBERT)
│  inference_local.py│
│  Offline Mode      │
│  No Flask needed   │
└─────────────────────┘
           │
           ▼
┌─────────────────────┐
│  ML Model          │
│  DistilBERT        │
│  Multi-task        │
│  Multi-task Heads  │
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

3. **Training Pipeline** (`mybudget-ai/train_distilbert.py`)
   - DistilBERT multi-task training
   - Data preprocessing and evaluation
   - Report generation

4. **Synthetic Data Generator** (`mybudget-ai/create_synthetic_dataset.py`)
   - Downloads Hugging Face dataset
   - Generates realistic UPI transactions
   - Balanced category distribution

---

## 📋 Prerequisites

### Required Software

- **Java 17+** (for Spring Boot)
- **Python 3.9+** (for ML service)
- **MySQL 8.0+** (for data persistence)
- **Gradle 7+** (included via wrapper)

### Python Dependencies

```bash
cd mybudget-ai
pip install -r requirements.txt
```

Key packages:
- `scikit-learn` (TF-IDF, Logistic Regression)
- `distilbert` (local inference)
- `pandas` (data processing)
- `numpy` (numerical operations)
- `matplotlib` (evaluation plots)
- `datasets` (Hugging Face integration)

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

### 2. Generate Training Data (Optional)

If you want to retrain the model:

```bash
cd mybudget-ai
source venv/bin/activate  # or `venv\Scripts\activate` on Windows

# Generate synthetic dataset from Hugging Face
python create_synthetic_dataset.py --num-samples 7000

# Train DistilBERT model
python3 train_distilbert.py
```

### 3. Verify Inference

The Spring Boot application uses local inference by default. No Flask server needed.
For testing local inference directly:
```bash
cd mybudget-ai
python3 inference_local.py "UPI/PAY/1234567890/STARBUCKS/txn@paytm"
```

The Spring Boot application will automatically use local inference when started.

### 4. Start Spring Boot Application

```bash
# From project root
./gradlew bootRun
```

Application will start on `http://localhost:8080`

### 5. Test the System

1. **Import Transactions:**
   - Navigate to `http://localhost:8080/transactions/upload`
   - Upload Excel file (.xls or .xlsx format)
   - System automatically categorises using AI

2. **View Predictions:**
   - Navigate to `http://localhost:8080/transactions/filter`
   - Check "Predicted Category" column

3. **Dashboard:**
   - Navigate to `http://localhost:8080/dashboard`
   - View 6-month category comparison bar chart
   - View spending analytics and recent transactions
   
4. **Transaction Dashboard (Filtered):**
   - Navigate to `http://localhost:8080/transactions/dashboard`
   - Filter by year and user
   - View category-wise month comparison

---

## 📊 Model Performance

### Latest Evaluation Results

**Dataset:**
- Training samples: 3,920
- Test samples: 980
- Categories: 10

**Metrics:**
- **Macro F1:** 0.8859 (target: ≥0.90)
- **Weighted F1:** 0.8922
- **Accuracy:** 0.8898

**Per-Class F1 Scores:**
| Category | F1-Score | Precision | Recall | Support |
|----------|----------|-----------|--------|---------|
| Fitness | 0.9884 | 1.00 | 0.98 | 131 |
| Healthcare | 0.9773 | 1.00 | 0.96 | 90 |
| Utilities | 0.9749 | 0.97 | 0.98 | 99 |
| Groceries | 0.9518 | 1.00 | 0.91 | 87 |
| Dining | 0.9399 | 1.00 | 0.89 | 97 |
| Charity | 0.9036 | 1.00 | 0.82 | 108 |
| Entertainment | 0.8340 | 0.76 | 0.93 | 111 |
| Shopping | 0.7759 | 0.69 | 0.89 | 101 |
| Transport | 0.7654 | 0.67 | 0.90 | 69 |
| Travel | 0.7482 | 1.00 | 0.60 | 87 |

**Detailed Reports:**
- Confusion Matrix: `mybudget-ai/reports/confusion_matrix_*.png`
- Evaluation Metrics: `mybudget-ai/reports/evaluation_metrics_*.json`
- Top Keywords: `mybudget-ai/reports/top_keywords_per_class_*.json`

---

## 🔧 Customisation

### Modify Taxonomy

Edit `mybudget-ai/categories.yml`:

```yaml
categories:
  - name: Groceries
    keywords: ["grocery", "supermarket", "vegetables", "fruits"]
  - name: Dining
    keywords: ["restaurant", "cafe", "food", "starbucks"]
  # Add more categories...
```

After modification:
1. Regenerate dataset (if needed): `python3 create_synthetic_dataset.py --samples 3000 --output transactions_distilbert.csv`
2. Retrain model: `python3 train_distilbert.py`
3. Spring Boot will automatically use the new model (local inference)

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

```bash
cd mybudget-ai
python3 inference_local.py "UPI/PAY/1234567890/STARBUCKS/txn@paytm"
```

**Example Output:**
```json
{
  "model_type": "DistilBERT",
  "transaction_type": "P2C",
  "predicted_category": "Dining",
  "intent": "purchase",
  "confidence": {
    "transaction_type": 0.98,
    "category": 0.92,
    "intent": 0.95
  },
  "all_probabilities": {
    "transaction_type": {...},
    "category": {...},
    "intent": {...}
  }
}
```

---

## 🧪 Testing & Evaluation

### Reproduce Evaluation

```bash
cd mybudget-ai
python3 train_distilbert.py
```

This will:
1. Load taxonomy from database
2. Load `transactions_distilbert.csv`
3. Split into train/test (80/20)
4. Train DistilBERT multi-task model
5. Generate evaluation reports
6. Save model artifacts

### View Results

```bash
# JSON metrics
cat reports/distilbert_metrics_*.json | python3 -m json.tool

# Check model directory
ls -lh models/distilbert_multitask_latest/
```

---

## 📁 Project Structure

```
budgetbuddy-ai/
├── src/main/java/com/budgetbuddy/
│   ├── controller/          # Spring Boot controllers (Dashboard, Transaction, etc.)
│   ├── service/            # Business logic (TransactionService, etc.)
│   ├── model/              # JPA entities (Transaction, User, CategoryKeyword)
│   ├── repository/         # Data access layer
│   └── config/             # Configuration (AppConfig)
├── src/main/resources/
│   ├── templates/          # Thymeleaf HTML templates
│   │   ├── layouts/       # Master layout templates
│   │   ├── transaction/   # Transaction management templates
│   │   ├── categories/    # Category management templates
│   │   └── dashboard_latest.html  # Main dashboard
│   └── static/             # CSS, JS assets
├── mybudget-ai/            # ML Service
│   ├── inference_local.py  # Local inference script
│   ├── train_distilbert.py # DistilBERT training pipeline
│   ├── create_synthetic_dataset.py  # Dataset generator
│   ├── categories.yml      # Taxonomy configuration (optional)
│   ├── transactions_distilbert.csv  # Training data
│   ├── models/             # Saved model files (DistilBERT)
│   ├── reports/            # Evaluation reports (metrics JSON)
│   └── logs/               # Training logs
├── build.gradle            # Gradle dependencies
├── README.md               # Main documentation
├── DATASET.md              # Dataset documentation
```

---

## 🤝 Contributing

### Development Setup

1. **Clone repository:**
   ```bash
   git clone https://github.com/ssubbulakshmi172/budgetbuddy-ai.git
   cd budgetbuddy-ai
   ```

2. **Install dependencies** (see Prerequisites section above)

3. **Setup MySQL database** (see Quick Start section)

4. **Generate training data** (optional - pre-trained model included):
   ```bash
   cd mybudget-ai
   python create_synthetic_dataset.py --num-samples 7000
   ```

5. **Train model** (optional - pre-trained model included):
   ```bash
   python3 train_distilbert.py
   ```

6. **Start services** (see Quick Start section)

### Code Style

- **Java:** Follow Spring Boot conventions
- **Python:** PEP 8, black formatting recommended
- **Documentation:** Javadoc for Java, docstrings for Python

---

## 📝 License

This project is developed for educational/competition purposes. Dataset sourced from Hugging Face (`deepakjoshi1606/mock-upi-txn-data`).

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

---

## 📚 Additional Documentation

- [REQUIREMENTS_COMPLIANCE.md](REQUIREMENTS_COMPLIANCE.md) - **Comprehensive requirements compliance assessment** mapping implementation to specification
- [DATASET.md](DATASET.md) - Dataset source, preprocessing, and reproducibility
- [EVALUATION_SUMMARY.md](EVALUATION_SUMMARY.md) - Gap analysis and implementation roadmap
- [LOCAL_INFERENCE_GUIDE.md](LOCAL_INFERENCE_GUIDE.md) - Local inference implementation guide
- [mybudget-ai/requirements.txt](mybudget-ai/requirements.txt) - Python dependencies

---

## 🔗 Repository

**GitHub:** https://github.com/ssubbulakshmi172/budgetbuddy-ai

---

## 🙏 Acknowledgments

- **Dataset:** Hugging Face `deepakjoshi1606/mock-upi-txn-data`
- **ML Libraries:** Scikit-learn, NumPy, Pandas, Matplotlib
- **Web Framework:** Spring Boot
- **ML Framework:** PyTorch, Transformers (DistilBERT)
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

⚠️ **Near Complete:**
- Macro F1 Score: 0.8859 (target: ≥0.90) - **97% of target**

📋 **For detailed compliance analysis, see:** 
- [REQUIREMENTS_COMPLIANCE.md](REQUIREMENTS_COMPLIANCE.md) - **Requirements specification compliance**

---

**Last Updated:** January 2025  
**Version:** 1.0.0  
**Status:** Production Ready
