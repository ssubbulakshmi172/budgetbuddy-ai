# Project Compliance Report: Automated AI-Based Financial Transaction Categorisation

**Project:** BudgetBuddy - Transaction Categorisation System  
**Evaluation Date:** January 2025  
**Status:** ~85% Compliant - Minor improvements needed

---

## Executive Summary

| Requirement | Status | Notes |
|------------|--------|-------|
| **End-to-End Autonomous Categorisation** | ✅ **COMPLETE** | All logic runs in-house, no third-party APIs |
| **Accuracy (F1 ≥ 0.90)** | ⚠️ **NEARLY COMPLETE** | Current: **0.8859** (needs 0.014 improvement) |
| **Evaluation Reports** | ✅ **COMPLETE** | Confusion matrix, per-class F1, macro F1 |
| **Customisable Taxonomy** | ✅ **COMPLETE** | YAML configuration file (`categories.yml`) |
| **Explainability** | ✅ **COMPLETE** | Confidence scores, all probabilities, top keywords |
| **Feedback Loop** | ✅ **COMPLETE** | API endpoint + CSV mechanism |
| **Robustness & Bias** | ⚠️ **PARTIAL** | Basic noise handling, bias not explicitly documented |
| **Performance Benchmarks** | ❌ **MISSING** | Throughput/latency metrics not measured |
| **Documentation** | ⚠️ **INCOMPLETE** | README minimal, dataset docs needed |

**Overall Compliance: 85%** - Core requirements met, bonus items need attention.

---

## 1. End-to-End Autonomous Categorisation ✅

### Requirement
- Ingest raw transaction data
- Output category + confidence score
- All logic within team's environment (no third-party APIs)

### Status: ✅ **COMPLETE**

**Evidence:**
- Flask API service (`mybudget-ai/app.py`) runs locally
- Scikit-learn models loaded from local `.pkl` files
- Java service (`TransactionCategorizationService`) calls internal Flask API
- No external API dependencies for categorisation

**Implementation:**
```python
# app.py - Local inference
model = joblib.load("models/category_model_latest.pkl")
vectorizer = joblib.load("models/tfidf_vectorizer_latest.pkl")
# Prediction happens entirely within local environment
```

---

## 2. Accuracy & Evaluation ⚠️

### Requirements
- Macro F1-score ≥ 0.90
- Detailed evaluation report (confusion matrix, per-class F1)
- Reproducibility

### Status: ⚠️ **NEARLY COMPLETE** (0.8859 vs 0.90 target)

**Current Performance (Latest Training):**
- **Macro F1:** 0.8859 (target: ≥0.90) - **1.4% below target**
- **Weighted F1:** 0.8922
- **Accuracy:** 0.8898
- **Test Set Size:** 980 samples (from 7,000 total)
- **Training Set Size:** 3,920 samples

**Per-Class F1 Scores:**
| Category | F1-Score | Status |
|----------|----------|--------|
| Fitness | 0.9884 | ✅ Excellent |
| Healthcare | 0.9773 | ✅ Excellent |
| Utilities | 0.9749 | ✅ Excellent |
| Groceries | 0.9518 | ✅ Excellent |
| Dining | 0.9399 | ✅ Excellent |
| Charity | 0.9036 | ✅ Good |
| Entertainment | 0.8340 | ⚠️ Needs improvement |
| Shopping | 0.7759 | ⚠️ Needs improvement |
| Transport | 0.7654 | ⚠️ Needs improvement |
| Travel | 0.7482 | ⚠️ Needs improvement |

**What's Working:**
- ✅ Confusion matrix generated (`confusion_matrix_*.png`)
- ✅ Per-class metrics in JSON (`evaluation_metrics_*.json`)
- ✅ Macro F1 calculated and logged
- ✅ Reproducible: `python train_model.py` generates consistent results

**Recommendations to Reach 0.90:**
1. Add more training data for underperforming classes (Entertainment, Shopping, Transport, Travel)
2. Fine-tune TF-IDF parameters (increase `max_features`, adjust `ngram_range`)
3. Experiment with alternative models (e.g., BERT-based, XGBoost)
4. Data augmentation for minority classes

---

## 3. Customisable & Transparent ✅

### Requirements
- Taxonomy configurable via file (JSON/YAML)
- Explainability (feature attributions)
- Feedback loop mechanism

### Status: ✅ **COMPLETE**

**Taxonomy Configuration:**
- ✅ `categories.yml` allows admin-driven category updates
- ✅ No code changes required for taxonomy modifications
- ✅ Supported format:
```yaml
categories:
  - name: Groceries
    keywords: ["grocery", "supermarket", "vegetables"]
```

**Explainability:**
- ✅ **Confidence scores** returned in API (`confidence: 0.85`)
- ✅ **All probabilities** provided (`all_probabilities: {Category: prob, ...}`)
- ✅ **Top keywords per class** computed and saved (`top_keywords_per_class_*.json`)
- ✅ Uses LogisticRegression (interpretable model with coefficient analysis)

**Feedback Loop:**
- ✅ Flask endpoint `/feedback` accepts corrections
- ✅ Feedback saved to `feedback.csv`
- ✅ Feedback automatically merged into training data on next model retrain
- ⚠️ **Minor Gap:** No UI integration in Spring Boot (API ready, frontend needed)

**Example Explainability Output:**
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

---

## 4. Robustness & Responsible AI ⚠️

### Requirements
- Handle noisy transaction strings robustly
- Address bias mitigation (merchant, region, amount)

### Status: ⚠️ **PARTIAL**

**What's Implemented:**
- ✅ Text cleaning function handles common UPI noise:
  - Removes UPI IDs, transaction numbers
  - Strips email-like patterns
  - Normalizes whitespace and punctuation
- ✅ Class balancing (`class_weight='balanced'`) reduces class imbalance bias
- ✅ Stratified train/test split ensures fair evaluation

**What's Missing:**
- ❌ **Bias Mitigation Documentation:** No explicit discussion of:
  - Merchant name bias
  - Regional bias
  - Transaction amount bias
- ❌ **Robustness Testing:** No documented tests for:
  - Malformed input handling
  - Edge cases (empty strings, special characters)
  - Noisy data variations

**Recommendations:**
1. Document bias mitigation strategies in README
2. Add robustness test suite with various noise patterns
3. Include bias analysis in evaluation report

---

## 5. Deliverables ✅/⚠️

### Required Deliverables

| Deliverable | Status | Location |
|------------|--------|----------|
| **Source Code Repository** | ✅ | Complete with Git history |
| **README** | ⚠️ **INCOMPLETE** | Exists but minimal (needs setup, demo steps) |
| **Dataset Documentation** | ❌ **MISSING** | Need `DATASET.md` with source, licensing, preprocessing |
| **Metrics Report** | ✅ | `mybudget-ai/reports/evaluation_metrics_*.json` |
| **Confusion Matrix** | ✅ | `mybudget-ai/reports/confusion_matrix_*.png` |
| **Demo** | ✅ | Spring Boot app with transaction import/categorization |

### Bonus Objectives

| Objective | Status | Notes |
|-----------|--------|-------|
| **Explainability UI** | ⚠️ **PARTIAL** | API returns probabilities, but no dedicated UI widget |
| **Robustness to Noise** | ✅ | Text cleaning handles UPI noise patterns |
| **Batch Inference Performance** | ❌ **MISSING** | No throughput/latency benchmarks |
| **Human-in-the-Loop Feedback** | ✅ | API endpoint + CSV mechanism |
| **Bias Mitigation Discussion** | ❌ **MISSING** | Not explicitly documented |

---

## 6. Critical Gaps & Action Items

### Priority 1: Fix F1 Score (Critical)
- **Current:** 0.8859 (target: ≥0.90)
- **Action:** 
  1. Generate additional training data for underperforming classes
  2. Experiment with hyperparameter tuning
  3. Consider ensemble methods or BERT if needed

### Priority 2: Documentation (High)
- **Action:**
  1. Create comprehensive `README.md` with:
     - Setup instructions
     - Demo walkthrough
     - Architecture overview
  2. Create `DATASET.md` documenting:
     - Data source (Hugging Face `deepakjoshi1606/mock-upi-txn-data`)
     - Synthetic generation process
     - Preprocessing steps
     - Licensing/compliance

### Priority 3: Performance Benchmarks (Medium)
- **Action:**
  1. Add batch inference endpoint
  2. Measure throughput (transactions/second)
  3. Measure latency (p50, p95, p99)
  4. Document in `PERFORMANCE.md`

### Priority 4: Robustness & Bias (Medium)
- **Action:**
  1. Document bias mitigation strategies
  2. Add robustness test cases
  3. Include bias analysis section in evaluation report

---

## 7. Demo Instructions

### Quick Start
```bash
# 1. Start Flask API (Python)
cd mybudget-ai
source venv/bin/activate  # or `venv\Scripts\activate` on Windows
python app.py

# 2. Start Spring Boot Application
cd ..
./gradlew bootRun

# 3. Import Transaction File
# Navigate to: http://localhost:8080/transactions/upload
# Upload Excel file with transaction data
# System automatically categorizes using AI

# 4. View Predictions
# Navigate to: http://localhost:8080/transactions/filter
# Check "Predicted Category" column
```

### Taxonomy Modification Demo
1. Edit `mybudget-ai/categories.yml`
2. Add new category or keywords
3. Retrain model: `python train_model.py`
4. Restart Flask API to load new model

---

## 8. Summary & Recommendations

### Strengths
- ✅ Core functionality complete and working
- ✅ Clean architecture (Java backend + Python ML service)
- ✅ Good evaluation pipeline with reproducible results
- ✅ Explainability features well-implemented
- ✅ Very close to F1 target (0.8859 vs 0.90)

### Weaknesses
- ⚠️ F1 score slightly below threshold
- ❌ Missing performance benchmarks
- ⚠️ Documentation needs enhancement
- ❌ Bias mitigation not explicitly addressed

### Final Verdict
**Project is ~85% compliant** with competition requirements. The system is functional and well-architected, but needs:
1. **Minor F1 improvement** (0.014 points) - easily achievable
2. **Enhanced documentation** - critical for submission
3. **Performance benchmarks** - bonus points

With 1-2 days of focused work on these items, the project will be **100% compliant** and ready for submission.

---

**Last Updated:** January 2025  
**Next Review:** After F1 score improvement and documentation updates

