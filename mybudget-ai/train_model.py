#!/usr/bin/env python3
"""
train_model.py

Train a TF-IDF + LogisticRegression classifier for transaction categorisation.

Features:
- Loads transactions.csv (description, category)
- Optionally merges feedback.csv if present
- Cleans noisy UPI text
- Loads categories.yml if present (for logging / validation)
- TF-IDF (unigram+bigram) vectorization
- Handles class imbalance with class_weight='balanced'
- Stratified train/test split (fallback if stratify fails)
- Evaluates and saves: JSON metrics, confusion matrix PNG, top keywords per class
- Saves model & vectorizer with timestamped filenames
- Logs to console and to a timestamped logfile
"""

import os
import re
import json
import logging
from datetime import datetime
import joblib
import pandas as pd
import yaml
import numpy as np
import matplotlib.pyplot as plt

from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.model_selection import train_test_split
from sklearn.metrics import classification_report, confusion_matrix, f1_score

# ---------- Configuration ----------
DATA_FILE = "transactions.csv"
FEEDBACK_FILE = "feedback.csv"        # optional, appended if exists
TAXONOMY_FILE = "categories.yml"     # optional, used for logging/consistency
REPORTS_DIR = "reports"
MODELS_DIR = "models"
LOGS_DIR = "logs"

TEST_SIZE = 0.20
RANDOM_STATE = 42
TFIDF_MAX_FEATURES = 3000
TFIDF_NGRAM = (1, 2)   # unigrams + bigrams
F1_TARGET = 0.90       # target macro F1 (warn if below)

# ---------- Prepare directories ----------
os.makedirs(REPORTS_DIR, exist_ok=True)
os.makedirs(MODELS_DIR, exist_ok=True)
os.makedirs(LOGS_DIR, exist_ok=True)

# ---------- Logging ----------
ts = datetime.now().strftime("%Y%m%d_%H%M%S")
log_file = os.path.join(LOGS_DIR, f"train_{ts}.log")
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(levelname)s - %(message)s",
    handlers=[
        logging.FileHandler(log_file),
        logging.StreamHandler()
    ]
)
logging.info("🔰 Starting training pipeline")

# ---------- Utility: clean text ----------
def clean_text(text: str) -> str:
    """
    Clean transaction description:
    - lowercasing
    - remove UPI tokens and emails/ids
    - remove digits
    - remove punctuation except spaces
    - normalize whitespace
    """
    if pd.isna(text):
        return ""
    s = str(text).lower()
    # remove common UPI/bank ID patterns (a conservative approach)
    s = re.sub(r'\bupi\b', ' ', s)
    s = re.sub(r'\btxnid\b', ' ', s)
    # remove emails / merchant tokens like something@BANK or .COM-like fragments
    s = re.sub(r'\S+@\S+', ' ', s)
    # remove long alphanumeric IDs
    s = re.sub(r'\b[a-z0-9]{12,}\b', ' ', s)
    # remove digits and special characters (keep letters and spaces)
    s = re.sub(r'[^a-z\s]', ' ', s)
    # collapse whitespace
    s = re.sub(r'\s+', ' ', s).strip()
    return s

# ---------- Load taxonomy (optional) ----------
taxonomy = None
if os.path.exists(TAXONOMY_FILE):
    try:
        with open(TAXONOMY_FILE, "r", encoding="utf-8") as f:
            taxonomy = yaml.safe_load(f)
        logging.info(f"✅ Loaded taxonomy from {TAXONOMY_FILE} ({len(taxonomy.get('categories', []))} categories)")
    except Exception as ex:
        logging.exception("⚠️ Failed to load taxonomy.yml — continuing without it")

# ---------- Load main dataset ----------
if not os.path.exists(DATA_FILE):
    logging.error(f"❌ Data file not found: {DATA_FILE}")
    raise FileNotFoundError(f"Missing {DATA_FILE} in working directory")

df = pd.read_csv(DATA_FILE)
logging.info(f"📥 Loaded {len(df)} rows from {DATA_FILE}")

# If feedback exists, append it to training data (optional)
if os.path.exists(FEEDBACK_FILE):
    try:
        fb = pd.read_csv(FEEDBACK_FILE)
        if {'description', 'category'}.issubset(fb.columns):
            logging.info(f"📥 Merging {len(fb)} feedback rows from {FEEDBACK_FILE}")
            df = pd.concat([df, fb[['description', 'category']]], ignore_index=True)
        else:
            logging.warning(f"Feedback file {FEEDBACK_FILE} missing required columns 'description,category' — skipping merge")
    except Exception:
        logging.exception("Failed to read/merge feedback file — continuing without it")

# Validate required columns
if 'description' not in df.columns or 'category' not in df.columns:
    logging.error("CSV must contain 'description' and 'category' columns")
    raise ValueError("transactions.csv must contain 'description' and 'category' columns")

# ---------- Clean text column ----------
logging.info("🧹 Cleaning transaction text")
df['clean_text'] = df['description'].astype(str).apply(clean_text)

# Optionally drop empty cleaned rows
before = len(df)
df = df[~df['clean_text'].str.strip().eq("")]
after = len(df)
if after < before:
    logging.info(f"🗑 Dropped {before-after} rows with empty/invalid cleaned text")

# ---------- Prepare X, y ----------
X = df['clean_text'].astype(str)
y = df['category'].astype(str)

# Show class distribution
dist = y.value_counts().to_dict()
logging.info(f"📊 Class distribution (label:count) - {dist}")

# ---------- Train/Test split with stratify and fallback ----------
try:
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=TEST_SIZE, random_state=RANDOM_STATE, stratify=y
    )
    logging.info("✂️ Performed stratified train/test split")
except Exception as e:
    logging.warning("⚠️ Stratified split failed (possibly few examples per class). Falling back to random split.")
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=TEST_SIZE, random_state=RANDOM_STATE
    )

logging.info(f"📚 Train size: {len(X_train)}, Test size: {len(X_test)}")

# ---------- Vectorization ----------
logging.info("🔣 Building TF-IDF vectorizer")
vectorizer = TfidfVectorizer(stop_words='english', max_features=TFIDF_MAX_FEATURES, ngram_range=TFIDF_NGRAM)
X_train_tfidf = vectorizer.fit_transform(X_train)
X_test_tfidf = vectorizer.transform(X_test)
logging.info(f"✅ TF-IDF shape train={X_train_tfidf.shape} test={X_test_tfidf.shape}")

# ---------- Train classifier ----------
logging.info("🏋️ Training LogisticRegression (class_weight='balanced')")

model = LogisticRegression(max_iter=2000, class_weight='balanced', n_jobs=-1)
model.fit(X_train_tfidf, y_train)
logging.info("✅ Model training finished")

# ---------- Evaluation ----------
logging.info("📈 Running evaluation on test set")
y_pred = model.predict(X_test_tfidf)
report_dict = classification_report(y_test, y_pred, output_dict=True)
macro_f1 = report_dict.get('macro avg', {}).get('f1-score', None)
weighted_f1 = report_dict.get('weighted avg', {}).get('f1-score', None)

# Save JSON report
report_path = os.path.join(REPORTS_DIR, f"evaluation_metrics_{ts}.json")
with open(report_path, "w", encoding="utf-8") as f:
    json.dump(report_dict, f, indent=2, ensure_ascii=False)
logging.info(f"💾 Saved evaluation metrics to {report_path}")

# Confusion matrix and plot
cm = confusion_matrix(y_test, y_pred, labels=model.classes_)
plt.figure(figsize=(8, 6))
plt.imshow(cm, interpolation='nearest', cmap='Blues')
plt.colorbar()
tick_marks = np.arange(len(model.classes_))
plt.xticks(tick_marks, model.classes_, rotation=45, ha='right')
plt.yticks(tick_marks, model.classes_)
plt.ylabel('True label')
plt.xlabel('Predicted label')
plt.title('Confusion Matrix')
# annotate
thresh = cm.max() / 2.
for i, j in np.ndindex(cm.shape):
    plt.text(j, i, format(cm[i, j], 'd'),
             horizontalalignment="center",
             color="white" if cm[i, j] > thresh else "black")
plt.tight_layout()
cm_path = os.path.join(REPORTS_DIR, f"confusion_matrix_{ts}.png")
plt.savefig(cm_path)
plt.close()
logging.info(f"💾 Saved confusion matrix image to {cm_path}")

# Log macro F1
if macro_f1 is not None:
    logging.info(f"🏆 Macro F1-score: {macro_f1:.4f}")
    logging.info(f"📌 Weighted F1-score: {weighted_f1:.4f}")
    print(f"🏆 Macro F1-score: {macro_f1:.4f}")
    if macro_f1 < F1_TARGET:
        logging.warning(f"⚠️ Macro F1 {macro_f1:.4f} is below target {F1_TARGET}. Consider adding more labeled data or augmenting under-represented classes.")
else:
    logging.warning("⚠️ Could not compute macro F1-score from report")

# ---------- Explainability: top keywords per class ----------
logging.info("🧠 Computing top keywords per class for explainability")
feature_names = vectorizer.get_feature_names_out()
top_keywords = {}
coefs = model.coef_
# Note: for multiclass (one-vs-rest), coef_ shape is (n_classes, n_features)
for idx, class_label in enumerate(model.classes_):
    # pair (coef_value, feature_name)
    coef_feature = list(zip(coefs[idx], feature_names))
    # sort descending by coefficient (higher -> more indicative of class)
    coef_feature_sorted = sorted(coef_feature, key=lambda x: x[0], reverse=True)[:25]
    top_keywords[class_label] = [feat for _, feat in coef_feature_sorted]

top_keywords_path = os.path.join(REPORTS_DIR, f"top_keywords_per_class_{ts}.json")
with open(top_keywords_path, "w", encoding="utf-8") as f:
    json.dump(top_keywords, f, indent=2, ensure_ascii=False)
logging.info(f"💾 Saved top keywords per class to {top_keywords_path}")

# ---------- Save model & vectorizer (timestamped) ----------
model_name = os.path.join(MODELS_DIR, f"category_model_{ts}.pkl")
vectorizer_name = os.path.join(MODELS_DIR, f"tfidf_vectorizer_{ts}.pkl")
joblib.dump(model, model_name)
joblib.dump(vectorizer, vectorizer_name)
logging.info(f"💾 Saved model to {model_name}")
logging.info(f"💾 Saved vectorizer to {vectorizer_name}")

# Also save a "latest" symlink/copy for convenience (overwrite)
latest_model = os.path.join(MODELS_DIR, "category_model_latest.pkl")
latest_vectorizer = os.path.join(MODELS_DIR, "tfidf_vectorizer_latest.pkl")
joblib.dump(model, latest_model)
joblib.dump(vectorizer, latest_vectorizer)
logging.info(f"💾 Updated latest model files: {latest_model}, {latest_vectorizer}")

logging.info("✅ Training pipeline complete. Artifacts are in the 'models' and 'reports' directories.")
print("✅ Training complete. Check logs for details:", log_file)
