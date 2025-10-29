# Dataset Documentation: BudgetBuddy Transaction Categorisation

This document provides comprehensive information about the dataset used for training and evaluating the BudgetBuddy transaction categorisation model.

---

## 📊 Dataset Overview

### Source Dataset

**Primary Source:** Hugging Face Dataset  
**Dataset Name:** `deepakjoshi1606/mock-upi-txn-data`  
**URL:** https://huggingface.co/datasets/deepakjoshi1606/mock-upi-txn-data  
**License:** Check Hugging Face dataset card for licensing terms  
**Type:** Mock/synthetic UPI transaction data

**Original Dataset Structure:**
- Contains simulated UPI transaction strings
- Includes transaction descriptions, amounts, dates
- Realistic Indian UPI format patterns

### Processed Dataset

**Final Dataset:** `mybudget-ai/transactions.csv`  
**Format:** CSV with columns: `description`, `category`  
**Size:** ~7,000 labeled transactions  
**Generation Method:** Synthetic generation with realistic merchant mapping

---

## 🏷️ Category Taxonomy

### Supported Categories (10 total)

1. **Groceries** - Supermarkets, grocery stores, fresh produce
2. **Dining** - Restaurants, cafes, food delivery
3. **Healthcare** - Pharmacies, clinics, medical consultations
4. **Transport** - Ride-sharing, fuel, parking, public transport
5. **Travel** - Flights, hotels, travel bookings
6. **Utilities** - Electricity, mobile recharge, bills
7. **Entertainment** - Movies, streaming, subscriptions
8. **Shopping** - E-commerce, retail purchases
9. **Charity** - Donations, contributions
10. **Fitness** - Gyms, fitness centers, workouts

### Category Distribution (Latest Training Run)

| Category | Training Samples | Test Samples | Percentage |
|----------|----------------|--------------|------------|
| Fitness | 655 | 131 | 9.4% |
| Entertainment | 553 | 111 | 7.9% |
| Charity | 540 | 108 | 7.7% |
| Shopping | 508 | 101 | 7.3% |
| Utilities | 494 | 99 | 7.1% |
| Dining | 486 | 97 | 6.9% |
| Healthcare | 452 | 90 | 6.5% |
| Groceries | 434 | 87 | 6.2% |
| Travel | 434 | 87 | 6.2% |
| Transport | 344 | 69 | 4.9% |
| **Total** | **4,900** | **980** | **100%** |

*Note: After cleaning, ~7,000 raw samples reduced to 4,900 valid samples*

---

## 🔄 Data Generation Process

### Synthetic Dataset Creation

**Script:** `mybudget-ai/create_synthetic_dataset.py`

**Process:**
1. **Download from Hugging Face:**
   ```python
   dataset = load_dataset("deepakjoshi1606/mock-upi-txn-data")
   ```

2. **Merchant Mapping:**
   - Maps transaction descriptions to realistic merchants
   - Uses predefined merchant lists per category
   - Example: "Starbucks" → Dining category

3. **UPI Format Generation:**
   - Generates realistic UPI transaction strings
   - Format: `UPI/PAY/[TX_ID]/[MERCHANT]/[HANDLE]@[BANK]/[REF]`
   - Includes realistic bank codes, merchant handles

4. **Balanced Sampling:**
   - Ensures roughly equal samples per category
   - Minimum 50 samples per category
   - Default: ~700 samples per category for 7,000 total

**Command:**
```bash
cd mybudget-ai
python create_synthetic_dataset.py --num-samples 7000 --output transactions.csv
```

### Real Merchant Examples

**Dining:**
- Starbucks, McDonald's, Domino's, Swiggy, Zomato

**Groceries:**
- BigBasket, Instamart, Reliance Fresh, D-Mart, Nilgiris

**Transport:**
- Uber, Ola, Rapido, Metro

**Shopping:**
- Amazon, Flipkart, Myntra, Meesho

**Entertainment:**
- Netflix, PVR, Cinepolis

---

## 🧹 Data Preprocessing

### Text Cleaning Pipeline

**Script:** `mybudget-ai/train_model.py` (function: `clean_text()`)

**Steps:**
1. **Lowercasing:** Convert to lowercase
2. **Remove UPI tokens:** Remove "upi", "txnid" patterns
3. **Remove email/IDs:** Remove patterns like `something@BANK`
4. **Remove long alphanumeric IDs:** Remove IDs >12 characters
5. **Remove digits & special chars:** Keep only letters and spaces
6. **Normalize whitespace:** Collapse multiple spaces to single space

**Example Transformation:**
```
Input:  "UPI/PAY/1234567890/STARBUCKS/txn@paytm/REF123"
Output: "starbucks paytm"
```

### Data Quality Checks

**Validation:**
- Drop rows with empty `description` after cleaning
- Drop rows with missing `category` label
- Ensure minimum length after cleaning (implicit via empty check)

**Statistics (Latest Run):**
- Raw samples: 7,000
- After cleaning: 4,900 (30% dropped)
- Drop rate: ~30% (due to insufficient text after cleaning)

---

## 📈 Train/Test Split

### Split Configuration

- **Test Size:** 20% (0.20)
- **Train Size:** 80% (0.80)
- **Stratification:** Yes (preserves class distribution)
- **Random State:** 42 (for reproducibility)

**Latest Split:**
- **Training:** 3,920 samples
- **Test:** 980 samples

**Split Method:**
```python
from sklearn.model_selection import train_test_split
X_train, X_test, y_train, y_test = train_test_split(
    X, y, test_size=0.20, random_state=42, stratify=y
)
```

*Note: Falls back to non-stratified split if stratification fails (rare)*

---

## 🔒 Data Privacy & Ethics

### Synthetic Data

- **Type:** Synthetically generated using patterns from Hugging Face dataset
- **No Real User Data:** All transaction strings are synthetic
- **Privacy-Safe:** Contains no personally identifiable information

### Bias Considerations

**Potential Biases Addressed:**
1. **Class Imbalance:** Handled via `class_weight='balanced'` in model
2. **Merchant Bias:** Balanced distribution across categories
3. **Regional Bias:** Uses generic merchant names (not location-specific)

**Bias Mitigation Strategies:**
- Stratified train/test split ensures fair evaluation
- Balanced sampling in synthetic generation
- Class-weighted training reduces majority class dominance

---

## 📋 Data Schema

### Input CSV Format

**File:** `transactions.csv`

**Columns:**
- `description` (string): Transaction description/UPI string
- `category` (string): Category label (must match `categories.yml`)

**Example:**
```csv
description,category
UPI/PAY/1234567890/STARBUCKS/.../REF123,Dining
UPI/PAY/9876543210/BIGBASKET/.../REF456,Groceries
```

### Output Format (Model Training)

**After Preprocessing:**
- `clean_text`: Cleaned transaction description
- `category`: Category label (unchanged)

---

## 🔄 Data Updates & Versioning

### Dataset Versioning

**Naming Convention:** `transactions.csv` (updated in-place)

**Backup Strategy:**
- Manual backups before regeneration
- Git history tracks changes

### Retraining Workflow

1. **Update categories** (if needed): Edit `categories.yml`
2. **Regenerate dataset:**
   ```bash
   python create_synthetic_dataset.py --num-samples 7000
   ```
3. **Train model:**
   ```bash
   python train_model.py
   ```
4. **Evaluate:** Check `reports/evaluation_metrics_*.json`

### Feedback Integration

**Feedback File:** `feedback.csv` (optional)

**Format:**
```csv
description,category
UPI/PAY/.../MERCHANT/...,CorrectCategory
```

**Integration:**
- Automatically merged into training data on next `train_model.py` run
- Appended to main `transactions.csv` before preprocessing

---

## 📊 Dataset Statistics

### Latest Dataset (2025-01-29)

- **Total Samples:** 4,900 (after cleaning)
- **Training Set:** 3,920
- **Test Set:** 980
- **Categories:** 10
- **Average Length (cleaned):** ~15-20 words
- **Unique Descriptions:** ~4,900 (synthetic, some overlap possible)

### Class Balance

- **Most Represented:** Fitness (655 samples)
- **Least Represented:** Transport (344 samples)
- **Balance Ratio:** 1.9:1 (acceptable for class_weight='balanced')

---

## ✅ Reproducibility

### Exact Reproduction Steps

```bash
# 1. Install dependencies
cd mybudget-ai
pip install -r requirements.txt

# 2. Generate dataset
python create_synthetic_dataset.py --num-samples 7000

# 3. Train model (reproducible with random_state=42)
python train_model.py

# 4. View results
cat reports/evaluation_metrics_*.json
```

### Reproducibility Guarantees

- ✅ **Random State:** Fixed (`random_state=42`)
- ✅ **Dataset:** Deterministic generation (if seed set)
- ✅ **Preprocessing:** Deterministic text cleaning
- ✅ **Train/Test Split:** Stratified and fixed

### Expected Results

With the above steps, you should see:
- **Macro F1:** ~0.8859 ± 0.005
- **Weighted F1:** ~0.8922 ± 0.005
- **Accuracy:** ~0.8898 ± 0.005

*Note: Small variations possible due to:
- Dataset regeneration randomness (if no seed)
- Scikit-learn version differences*

---

## 📝 Data License & Attribution

### Source Dataset

**Hugging Face Dataset:**
- Name: `deepakjoshi1606/mock-upi-txn-data`
- Provider: deepakjoshi1606 (Hugging Face user)
- License: Check dataset card on Hugging Face

### Processed Dataset

**Our Work:**
- Synthetic generation logic (in-house)
- Category mapping and labeling (in-house)
- Preprocessing pipeline (in-house)

**Usage:**
- Training models for this project
- Research and educational purposes
- Not for commercial use without proper licensing verification

---

## 🔗 References

- **Hugging Face Dataset:** https://huggingface.co/datasets/deepakjoshi1606/mock-upi-txn-data
- **Dataset Generation Script:** `mybudget-ai/create_synthetic_dataset.py`
- **Preprocessing Code:** `mybudget-ai/train_model.py` (function: `clean_text`)
- **Category Configuration:** `mybudget-ai/categories.yml`

---

## 📞 Contact & Support

For questions about the dataset:
1. Check this documentation
2. Review `create_synthetic_dataset.py` source code
3. Inspect generated `transactions.csv`

---

**Last Updated:** January 2025  
**Dataset Version:** 1.0 (Synthetic, 7,000 samples)  
**Maintainer:** BudgetBuddy Development Team

