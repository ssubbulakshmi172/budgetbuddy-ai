# Project Requirements Evaluation: Automated AI-Based Financial Transaction Categorisation

**Evaluation Date:** January 2025  
**Project:** BudgetBuddy - Transaction Categorisation System

---

## Executive Summary

Your project has a **solid foundation** for the competition requirements, with most core components implemented. However, there are **critical gaps** that need to be addressed before submission:

- ✅ **End-to-End Autonomous Categorisation** - Implemented
- ✅ **Customisable Taxonomy** - YAML configuration supported
- ✅ **Evaluation Pipeline** - Confusion matrix & F1 scores generated
- ✅ **Explainability** - Top keywords per class computed
- ⚠️ **F1 Score Target** - **CRITICAL:** Currently **0.14**, needs **≥0.90**
- ⚠️ **Confidence Scores** - Not returned by API (easy fix)
- ⚠️ **Feedback Loop UI** - Mechanism exists but no user interface
- ❌ **Robustness Testing** - Not documented
- ❌ **Bias Mitigation** - Not addressed
- ❌ **Performance Benchmarks** - Missing

**Overall Status:** ~60% Complete - **Needs significant improvements before submission**

---

## Detailed Requirement Analysis

### 1. End-to-End Autonomous Categorisation ✅

**Requirement:** System must ingest raw transaction data and output category + confidence score. All logic must run within team's environment (no third-party APIs).

**Current Status:** ✅ **COMPLETE**

**Evidence:**
- Flask service (`mybudget-ai/app.py`) handles inference internally
- Uses scikit-learn models loaded from local files
- No external API calls for categorisation
- Java service (`TransactionCategorizationService`) calls Flask internally

**What Works:**
- Model inference runs locally
- No third-party dependencies for categorisation
- Clean separation between Java backend and Python ML service

**Minor Improvements Needed:**
- Add confidence scores to API response (see section 2)

---

### 2. Accuracy & Evaluation ⚠️ **CRITICAL GAP**

**Requirement:** 
- Macro F1-score ≥ 0.90
- Detailed evaluation report (confusion matrix, macro/per-class F1 scores)
- End-to-end reproducibility

**Current Status:** ⚠️ **PARTIALLY COMPLETE** - **MAJOR ISSUE**

**Evidence:**
```json
// evaluation_metrics_20251028_220500.json
{
  "macro avg": {
    "f1-score": 0.14285714285714285  // ❌ FAR BELOW 0.90 TARGET
  }
}
```

**Issues Identified:**

1. **F1 Score Critical Problem:**
   - Current macro F1: **0.1428** (target: ≥0.90)
   - Test set size: Only **7 samples** - **extremely small dataset**
   - This indicates either:
     - Very small training dataset
     - Severe class imbalance
     - Poor model quality

2. **What's Working:**
   - ✅ Confusion matrix generated (`confusion_matrix_*.png`)
   - ✅ Per-class F1 scores computed
   - ✅ Macro F1 calculated and logged
   - ✅ Evaluation reports saved as JSON

3. **What's Missing:**
   - ❌ Confidence scores not returned in API response
   - ❌ Dataset too small for reliable evaluation
   - ❌ No reproducibility documentation (dataset source, preprocessing steps)

**REQUIRED FIXES:**

#### Fix 1: Add Confidence Scores to API
```python
# app.py needs update:
@app.route("/predict", methods=["POST"])
def predict_category():
    # ... existing code ...
    X_input = vectorizer.transform([description])
    category = model.predict(X_input)[0]
    probabilities = model.predict_proba(X_input)[0]  # ADD THIS
    confidence = float(probabilities.max())  # ADD THIS
    
    return jsonify({
        "description": description,
        "predicted_category": category,
        "confidence": confidence,  # ADD THIS
        "all_probabilities": {  # BONUS: explainability
            model.classes_[i]: float(probabilities[i]) 
            for i in range(len(model.classes_))
        }
    })
```

#### Fix 2: Improve Dataset Size
- Current dataset appears too small (likely <50 samples)
- **Need:** Minimum 500-1000 labeled transactions
- **Recommendation:**
  - Use public datasets (Kaggle, UCI)
  - Synthetically generate transactions with known categories
  - Crowd-source labeling
  - Document dataset source and size

#### Fix 3: Add Reproducibility Documentation
Create `DATASET.md`:
- Dataset source and licensing
- Preprocessing steps documented
- Exact command to reproduce evaluation: `python train_model.py`

---

### 3. Customisable & Transparent ✅ (with minor gaps)

**Requirement:**
- Taxonomy easily updated via config file (JSON/YAML)
- Explainability features
- Feedback loop mechanism

**Current Status:** ✅ **MOSTLY COMPLETE**

**Evidence:**
- ✅ `categories.yml` exists and can be modified
- ✅ Top keywords per class generated (`top_keywords_per_class_*.json`)
- ⚠️ Feedback mechanism exists but no UI

**What's Working:**
1. **Taxonomy Configuration:** ✅
   ```yaml
   # categories.yml - can be edited without code changes
   categories:
     - name: Groceries
       keywords: ["grocery", "nilgiris", "farm"]
   ```

2. **Explainability:** ✅
   - Top 25 keywords per category computed
   - Saved to JSON for inspection
   - Uses LogisticRegression coefficients (interpretable model)

**What's Missing:**
1. **Feedback Loop UI:** ⚠️
   - Code supports `feedback.csv` merge (`train_model.py:112-122`)
   - No user interface for submitting corrections
   - No mechanism to trigger retraining after feedback

**REQUIRED FIXES:**

#### Fix 1: Add Feedback Endpoint
```python
# app.py - add endpoint
@app.route("/feedback", methods=["POST"])
def submit_feedback():
    data = request.get_json()
    description = data.get("description")
    correct_category = data.get("correct_category")
    
    # Append to feedback.csv
    with open("feedback.csv", "a") as f:
        f.write(f"{description},{correct_category}\n")
    
    return jsonify({"status": "feedback recorded"})
```

#### Fix 2: Add UI for Feedback (Java Controller)
- Add endpoint in `TransactionController` to accept category corrections
- Create UI form for users to correct low-confidence predictions
- Automatically append to `feedback.csv`

---

### 4. Robustness & Responsible AI ❌ **MISSING**

**Requirement:**
- Handle noisy transaction strings robustly
- Address ethical AI aspects (bias mitigation)

**Current Status:** ❌ **NOT ADDRESSED**

**What's Working:**
- ✅ Text cleaning function exists (`clean_text()` in `train_model.py`)
- ✅ Handles UPI patterns, emails, alphanumeric IDs

**What's Missing:**
1. **Robustness Testing:** ❌
   - No documentation of noisy input handling
   - No examples of edge cases (empty strings, special chars, emojis)
   - No adversarial testing

2. **Bias Mitigation:** ❌
   - No analysis of biases by:
     - Merchant name (e.g., regional bias)
     - Transaction amount
     - User demographics
   - No fairness metrics computed
   - No discussion in documentation

**REQUIRED ADDITIONS:**

#### Fix 1: Add Robustness Documentation
Create `ROBUSTNESS.md`:
```markdown
## Robustness Testing

### Edge Cases Handled:
1. Empty/whitespace-only descriptions
2. UPI transaction codes: "UPI/PAYTM/1234567890/..."
3. Special characters: "Starbucks® #123"
4. Mixed case: "AMAZON.COM"
5. Very long descriptions (>200 chars)

### Test Results:
- [Examples with inputs/outputs]
```

#### Fix 2: Add Bias Analysis
Create `BIAS_ANALYSIS.md`:
```markdown
## Bias Mitigation Analysis

### Potential Biases Identified:
1. Regional bias: Certain merchants may be underrepresented
2. Amount-based bias: High-value transactions in specific categories
3. Language bias: English-only (may miss regional language merchants)

### Mitigation Strategies:
1. Class balancing during training (class_weight='balanced' ✅)
2. Stratified sampling for evaluation
3. [Add more strategies]

### Fairness Metrics:
[Add per-group accuracy/F1 scores if applicable]
```

---

### 5. Performance Benchmarks ❌ **BONUS MISSING**

**Requirement:** Extra credit for throughput and latency benchmarks

**Current Status:** ❌ **NOT IMPLEMENTED**

**Required:** Create benchmark script:
```python
# benchmark.py
import time
import requests
import statistics

def benchmark_throughput():
    descriptions = ["Starbucks", "Amazon.com", ...] * 100
    times = []
    
    for desc in descriptions:
        start = time.time()
        requests.post("http://localhost:8000/predict", 
                     json={"description": desc})
        times.append(time.time() - start)
    
    print(f"Latency: {statistics.mean(times):.4f}s")
    print(f"Throughput: {100/statistics.mean(times):.2f} req/s")
```

---

## Deliverables Checklist

### ✅ Source Code Repository
- [x] Code available in repository
- [x] README exists (but needs improvement)
- [ ] Dataset documentation (missing)

### ⚠️ Metrics Report
- [x] Macro F1 score computed
- [x] Per-class F1 scores computed
- [x] Confusion matrix generated
- [ ] **F1 score meets target (≥0.90)** ❌ **CRITICAL**

### ⚠️ Demo Requirements
- [x] Pipeline execution (train_model.py)
- [x] Evaluation output (reports/ directory)
- [ ] Sample predictions with confidence (API doesn't return confidence)
- [x] Taxonomy modification demo (categories.yml can be edited)

### ❌ Bonus Objectives
- [ ] Explainability UI (keywords available but no UI)
- [ ] Robustness documentation (not documented)
- [ ] Batch inference performance (not benchmarked)
- [ ] Human-in-the-loop feedback (mechanism exists, no UI)
- [ ] Bias mitigation discussion (not addressed)

---

## Critical Action Items (Priority Order)

### 🔴 **CRITICAL - Must Fix Before Submission**

1. **Improve F1 Score (0.14 → ≥0.90)**
   - **Priority:** #1
   - **Action:** 
     - Expand dataset to 500+ labeled transactions
     - Balance classes (ensure min 20-30 samples per category)
     - Consider data augmentation
     - Re-train and re-evaluate
   - **Estimated Time:** 2-3 days (data collection) + 1 day (training)

2. **Add Confidence Scores to API**
   - **Priority:** #2
   - **Action:** Update `app.py` to return `model.predict_proba()`
   - **Estimated Time:** 30 minutes

3. **Create Dataset Documentation**
   - **Priority:** #3
   - **Action:** Create `DATASET.md` with source, size, preprocessing
   - **Estimated Time:** 1 hour

### 🟡 **HIGH PRIORITY - Should Fix**

4. **Implement Feedback Loop UI**
   - **Priority:** #4
   - **Action:** 
     - Add feedback endpoint in Flask
     - Add UI form in Java frontend
     - Show low-confidence predictions prominently
   - **Estimated Time:** 4-6 hours

5. **Improve README**
   - **Priority:** #5
   - **Action:** Add setup instructions, dataset info, running guide
   - **Estimated Time:** 2 hours

### 🟢 **MEDIUM PRIORITY - Nice to Have**

6. **Add Robustness Documentation**
   - **Priority:** #6
   - **Estimated Time:** 2 hours

7. **Add Bias Mitigation Discussion**
   - **Priority:** #7
   - **Estimated Time:** 3-4 hours

8. **Create Performance Benchmarks**
   - **Priority:** #8 (Bonus)
   - **Estimated Time:** 2 hours

---

## Estimated Timeline to Completion

**Minimum Viable Submission (Critical fixes only):**
- Days: 3-4 days
- Focus: Dataset expansion + F1 improvement + confidence scores

**Complete Submission (all requirements + bonuses):**
- Days: 5-7 days
- Focus: All critical + high priority items

---

## Strengths to Highlight in Submission

1. **Clean Architecture:**
   - Well-separated ML service from application
   - Modular training pipeline
   - Easy to maintain and extend

2. **Explainability Built-in:**
   - Top keywords feature already implemented
   - Interpretable model (LogisticRegression)

3. **Production-Ready Infrastructure:**
   - Flask API service pattern
   - Model versioning (timestamped files)
   - Comprehensive logging

4. **Customisable Design:**
   - YAML-based taxonomy (admin-friendly)
   - No code changes needed for category updates

---

## Risks & Concerns

1. **Dataset Quality:**
   - Current dataset appears insufficient for F1 ≥ 0.90
   - **Risk:** May need synthetic data or external dataset
   - **Mitigation:** Start data collection immediately

2. **Evaluation Reproducibility:**
   - Need exact dataset and preprocessing steps documented
   - **Risk:** Judges can't reproduce results
   - **Mitigation:** Create detailed DATASET.md

3. **Testing Coverage:**
   - No robustness testing documented
   - **Risk:** Missing edge case handling
   - **Mitigation:** Add test cases and documentation

---

## Conclusion

Your project has a **strong technical foundation** with most core components in place. The main blockers are:

1. **F1 score is critically low** (0.14 vs 0.90 target) - likely due to small dataset
2. **Missing confidence scores** in API (easy fix)
3. **Incomplete documentation** for reproducibility

**Recommendation:**
- **Focus immediately on dataset expansion** - this is the highest priority blocker
- Fix confidence scores (quick win)
- Add documentation
- Then address bonus features if time permits

**With focused effort on dataset collection, this project can meet all requirements.**

---

**Overall Assessment: 6.5/10**
- Technical implementation: 8/10 ✅
- Dataset quality: 2/10 ❌
- Documentation: 4/10 ⚠️
- Requirement compliance: 60% ⚠️

---

*Generated: January 2025*

