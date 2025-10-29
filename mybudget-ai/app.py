from flask import Flask, request, jsonify
import joblib
import logging
import os
import csv

# Step 1: Initialize Flask app
app = Flask(__name__)
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Step 2: Load model and vectorizer
# ✅ Load trained model and vectorizer
MODEL_PATH = "models/category_model_latest.pkl"
VECTORIZER_PATH = "models/tfidf_vectorizer_latest.pkl"

logger.info("🔁 Loading trained model and vectorizer...")
model = joblib.load(MODEL_PATH)
vectorizer = joblib.load(VECTORIZER_PATH)
logger.info("✅ Model and vectorizer loaded successfully.")


@app.route("/")
def home():
    return jsonify({"message": "AI Categorization Engine is up 🚀"})

@app.route("/health", methods=["GET"])
def health_check():
    """Health check endpoint for monitoring"""
    return jsonify({
        "status": "healthy",
        "model_loaded": model is not None,
        "vectorizer_loaded": vectorizer is not None
    })

# Step 3: API endpoint to predict category
@app.route("/predict", methods=["POST"])
def predict_category():
    """
    Predict category for a transaction description.
    
    Returns:
        - predicted_category: The predicted category
        - confidence: Confidence score (0-1) for the prediction
        - all_probabilities: Dictionary of all category probabilities (for explainability)
    """
    data = request.get_json()
    description = data.get("description")

    if not description:
        return jsonify({"error": "Missing 'description' field"}), 400

    # Transform input text using same TF-IDF vectorizer
    X_input = vectorizer.transform([description])
    
    # Get prediction
    category = model.predict(X_input)[0]
    
    # Get probabilities for confidence score and explainability
    probabilities = model.predict_proba(X_input)[0]
    confidence = float(probabilities.max())
    
    # Create dictionary of all category probabilities for explainability
    all_probabilities = {
        model.classes_[i]: float(probabilities[i]) 
        for i in range(len(model.classes_))
    }

    return jsonify({
        "description": description,
        "predicted_category": category,
        "confidence": confidence,
        "all_probabilities": all_probabilities
    })

@app.route("/feedback", methods=["POST"])
def submit_feedback():
    """
    Submit feedback for incorrect predictions.
    This feeds into the training pipeline via feedback.csv.
    
    Expected JSON:
    {
        "description": "transaction description",
        "correct_category": "actual category name"
    }
    """
    data = request.get_json()
    description = data.get("description")
    correct_category = data.get("correct_category")
    
    if not description or not correct_category:
        return jsonify({"error": "Missing 'description' or 'correct_category' field"}), 400
    
    # Append to feedback.csv (create if doesn't exist)
    feedback_file = "feedback.csv"
    file_exists = os.path.exists(feedback_file)
    
    try:
        with open(feedback_file, "a", newline="", encoding="utf-8") as f:
            writer = csv.writer(f)
            # Write header if new file
            if not file_exists:
                writer.writerow(["description", "category"])
            writer.writerow([description, correct_category])
        
        logger.info(f"✅ Feedback recorded: {description} -> {correct_category}")
        return jsonify({
            "status": "success",
            "message": "Feedback recorded successfully",
            "description": description,
            "correct_category": correct_category
        })
    except Exception as e:
        logger.error(f"❌ Error saving feedback: {e}")
        return jsonify({"error": "Failed to save feedback"}), 500

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=8000, debug=True)
