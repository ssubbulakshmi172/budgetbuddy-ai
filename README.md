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
           │ HTTP POST
           ▼
┌─────────────────────┐
│  Flask API         │  (Python + Scikit-learn)
│  Port: 8000        │
│  /predict          │
│  /feedback         │
│  /health           │
└─────────────────────┘
           │
           ▼
┌─────────────────────┐
│  ML Model          │
│  TF-IDF Vectorizer │
│  Logistic Reg.     │
└─────────────────────┘
```

### Components

1. **Spring Boot Backend** (`src/main/java/`)
   - Transaction management
   - User interface (Thymeleaf templates)
   - Integration with ML service

2. **Flask ML Service** (`mybudget-ai/app.py`)
   - Model inference endpoint
   - Feedback collection
   - Health monitoring

3. **Training Pipeline** (`mybudget-ai/train_model.py`)
   - Data preprocessing
   - Model training & evaluation
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
- `flask` (API server)
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

# Train model
python train_model.py
```

### 3. Start ML Service

```bash
cd mybudget-ai
source venv/bin/activate
python app.py
```

Service will start on `http://127.0.0.1:8000`

Verify:
```bash
curl http://127.0.0.1:8000/health
```

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
1. Regenerate dataset (if needed): `python create_synthetic_dataset.py`
2. Retrain model: `python train_model.py`
3. Restart Flask API to load new model

### Adjust Model Parameters

Edit `mybudget-ai/train_model.py`:

```python
TFIDF_MAX_FEATURES = 3000  # Increase for more features
TFIDF_NGRAM = (1, 2)        # unigrams + bigrams
F1_TARGET = 0.90
```

---

## 📡 API Endpoints

### Flask ML Service (`http://127.0.0.1:8000`)

#### `POST /predict`

Predict category for a transaction description.

**Request:**
```json
{
  "description": "UPI/PAY/1234567890/STARBUCKS/..."
}
```

**Response:**
```json
{
  "description": "UPI/PAY/1234567890/STARBUCKS/...",
  "predicted_category": "Dining",
  "confidence": 0.92,
  "all_probabilities": {
    "Dining": 0.92,
    "Groceries": 0.05,
    "Entertainment": 0.02,
    ...
  }
}
```

#### `POST /feedback`

Submit feedback for incorrect predictions.

**Request:**
```json
{
  "description": "UPI/PAY/1234567890/STARBUCKS/...",
  "predicted_category": "Dining",
  "correct_category": "Entertainment"
}
```

**Response:**
```json
{
  "status": "feedback_recorded",
  "message": "Feedback saved to feedback.csv"
}
```

#### `GET /health`

Health check endpoint.

**Response:**
```json
{
  "status": "healthy",
  "model_loaded": true,
  "vectorizer_loaded": true
}
```

---

## 🧪 Testing & Evaluation

### Reproduce Evaluation

```bash
cd mybudget-ai
python train_model.py
```

This will:
1. Load `transactions.csv`
2. Split into train/test (80/20)
3. Train TF-IDF + Logistic Regression model
4. Generate evaluation reports
5. Save model artifacts

### View Results

```bash
# JSON metrics
cat reports/evaluation_metrics_*.json

# Confusion matrix
open reports/confusion_matrix_*.png

# Top keywords
cat reports/top_keywords_per_class_*.json
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
│   ├── app.py              # Flask API server
│   ├── train_model.py      # Training pipeline
│   ├── create_synthetic_dataset.py  # Dataset generator
│   ├── categories.yml      # Taxonomy configuration
│   ├── transactions_synthetic.csv  # Synthetic training data
│   ├── models/             # Saved model files (*_latest.pkl)
│   ├── reports/            # Evaluation reports (confusion matrix, metrics)
│   └── logs/               # Training logs
├── build.gradle            # Gradle dependencies
├── README.md               # Main documentation
├── DATASET.md              # Dataset documentation
└── PROJECT_COMPLIANCE.md   # Compliance report
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
   python train_model.py
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

### Flask API Connection Refused

```bash
# Check if Flask is running
curl http://127.0.0.1:8000/health

# If not, start it:
cd mybudget-ai
source venv/bin/activate
python app.py
```

### Model Not Found Error

Ensure models exist:
```bash
ls mybudget-ai/models/category_model_latest.pkl
```

If missing, train model:
```bash
cd mybudget-ai
python train_model.py
```

### Database Connection Issues

Verify MySQL is running:
```bash
mysqladmin -u root -p status
```

Check `application.properties` has correct credentials.

---

## 📚 Additional Documentation

- [PROJECT_COMPLIANCE.md](PROJECT_COMPLIANCE.md) - Detailed compliance report against competition requirements
- [DATASET.md](DATASET.md) - Dataset source, preprocessing, and reproducibility
- [mybudget-ai/requirements.txt](mybudget-ai/requirements.txt) - Python dependencies

---

## 🔗 Repository

**GitHub:** https://github.com/ssubbulakshmi172/budgetbuddy-ai

---

## 🙏 Acknowledgments

- **Dataset:** Hugging Face `deepakjoshi1606/mock-upi-txn-data`
- **ML Libraries:** Scikit-learn, NumPy, Pandas, Matplotlib
- **Web Framework:** Spring Boot, Flask
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

📋 **For detailed compliance analysis, see:** [PROJECT_COMPLIANCE.md](PROJECT_COMPLIANCE.md)

---

**Last Updated:** January 2025  
**Version:** 1.0.0  
**Status:** Production Ready
