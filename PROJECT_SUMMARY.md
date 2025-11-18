# BudgetBuddy AI - Complete Project Summary

## 📋 Project Overview

BudgetBuddy AI is a comprehensive personal finance management application with AI-powered transaction categorization. The project consists of:

1. **Spring Boot Backend** - Web application with ML inference
2. **Android Mobile App** - Native Kotlin app with on-device ML
3. **ML Training Pipeline** - Python-based DistilBERT model training

---

## 🏗️ Architecture

### Multi-Platform Structure

```
budgetbuddy-ai/
├── src/                          # Spring Boot Backend
│   ├── main/java/com/budgetbuddy/
│   │   ├── controller/          # REST & MVC Controllers
│   │   ├── service/             # Business Logic
│   │   ├── repository/         # Data Access (JPA)
│   │   ├── model/              # Entity Models
│   │   └── config/             # Configuration
│   └── main/resources/
│       ├── templates/          # Thymeleaf HTML
│       └── application.properties
│
├── mobile-version/              # Android Mobile App
│   └── app/src/main/java/com/budgetbuddy/mobile/
│       ├── data/               # Models, DAOs, Database
│       ├── repository/         # Data Repositories
│       ├── service/            # Business Logic
│       ├── ml/                 # ML Inference (PyTorch Mobile)
│       ├── ui/                 # Jetpack Compose UI
│       └── util/               # Utilities
│
└── mybudget-ai/                # ML Training & Inference
    ├── train_distilbert.py     # Model Training
    ├── inference_local.py      # Local Inference Script
    ├── distilbert_inference.py # Core Inference Module
    ├── preprocessing_utils.py  # Text Preprocessing
    ├── add_correction.py       # Add User Corrections
    ├── export_corrections.py   # Export Corrections
    └── models/                 # Trained Models
```

---

## 🤖 ML/AI Components

### Model Architecture
- **Base Model**: DistilBERT (distilbert-base-uncased)
- **Tasks**: Multi-task learning
  - Transaction Type (P2C, P2P, P2Business)
  - Category (10+ categories with subcategories)
  - Intent (purchase, transfer, refund, subscription, bill_payment, other)
- **Format**: PyTorch Mobile (.ptl) for Android, Python for backend

### Inference Priority Order

1. **User Corrections** (Highest Priority)
   - Source: `mybudget-ai/user_corrections.json`
   - Preprocessed narrations (removes UPI tags, IDs)
   - In-memory cache for fast lookup
   - 100% confidence

2. **Commodity Corrections** (Efficient Mode - Future)
   - Source: Vector database (shared corrections from other users)
   - Similarity matching using DistilBERT embeddings
   - Cosine similarity threshold (e.g., >0.85)
   - Fast lookup for common patterns

3. **Keyword Matching** (Rule-Based)
   - Source: `categories.yml`
   - Word-boundary matching
   - Longest keyword first

4. **DistilBERT Model** (ML Prediction)
   - Multi-task predictions
   - Confidence scores
   - Subcategory extraction

### Text Preprocessing
- Removes UPI prefixes (`UPI-`, `UPI/`)
- Removes bank tags (`@YBL`, `@HDFCBANK`)
- Removes transaction IDs (long numbers)
- Normalizes separators
- Preserves P2P clues (for person-to-person transactions)

---

## 📊 Data Models

### Core Entities

#### Transaction
- `id`, `date`, `narration`, `amount`
- `withdrawalAmt`, `depositAmt`, `closingBalance`
- `userId` (foreign key)
- **ML Predictions**: `predictedCategory`, `predictedSubcategory`, `predictedTransactionType`, `predictedIntent`, `predictionConfidence`
- **User Assignment**: `categoryName`
- **Metadata**: `chequeRefNo`, `predictionReason`

#### User
- `id`, `name`, `email`, `password`

#### CategoryKeyword
- `id`, `keyword`, `categoryName`, `categoriesFor` (Taxonomy/Manual)

#### Financial Guidance Entities
- **SpendingPattern**: Detected spending patterns (daily/weekly/monthly)
- **SpendingPrediction**: Future spending forecasts
- **FinancialNudge**: Personalized financial advice
- **Trend**: Spending trend analysis

---

## 🔧 Technology Stack

### Backend (Spring Boot)
- **Framework**: Spring Boot 3.x
- **Database**: MySQL (JPA/Hibernate)
- **ML Inference**: Python scripts (ProcessBuilder)
- **UI**: Thymeleaf templates
- **Build**: Gradle

### Mobile (Android)
- **Language**: Kotlin
- **UI**: Jetpack Compose
- **Database**: Room (SQLite)
- **ML**: PyTorch Mobile 2.1.0
- **Architecture**: MVVM
- **File Parsing**: Apache POI (Excel), OpenCSV
- **Charts**: MPAndroidChart
- **Build**: Gradle (Android)

### ML Training
- **Framework**: PyTorch
- **Model**: DistilBERT (Hugging Face Transformers)
- **Training**: Multi-task learning
- **Quantization**: INT8 for mobile
- **Format**: TorchScript Lite (.ptl)

---

## ✨ Key Features

### Spring Boot Backend

#### Transaction Management
- ✅ Add/Edit/Delete transactions
- ✅ Bulk import (CSV/Excel)
- ✅ Filter by month, year, category, amount, narration
- ✅ Sort by date, amount, category
- ✅ Bulk delete by filters
- ✅ Duplicate detection

#### ML Categorization
- ✅ Automatic category prediction
- ✅ Keyword matching (rule-based)
- ✅ User corrections (saved to JSON)
- ✅ Batch prediction refresh
- ✅ Confidence scores
- ✅ Subcategory extraction
- ⏳ Efficient Mode (cloud sync - infrastructure ready)

#### Financial Guidance
- ✅ Spending Patterns (daily/weekly/monthly)
- ✅ Trend Analysis (6-month trends, spikes, dips)
- ✅ Spending Predictions (future forecasts)
- ✅ Financial Nudges (personalized advice)

#### Category Management
- ✅ View all categories
- ✅ Add/Edit/Delete category keywords
- ✅ Taxonomy vs Manual categories
- ✅ Keyword-based matching

#### Dashboard
- ✅ Current month spending
- ✅ Category breakdown
- ✅ Month-over-month comparison
- ✅ 6-month trends
- ✅ Recent transactions
- ✅ Uncategorized count

### Android Mobile App

#### Transaction Management
- ✅ Add/Edit/Delete transactions
- ✅ Transaction list with filters
- ✅ File import (CSV/Excel)
- ✅ ML prediction on add/edit

#### ML Categorization
- ✅ On-device PyTorch Mobile inference
- ✅ Keyword matching (from database)
- ✅ User corrections support
- ✅ Preprocessed text matching
- ✅ Confidence scores
- ✅ Subcategory extraction

#### Financial Guidance
- ✅ Spending Patterns view
- ✅ Trend Analysis
- ✅ Spending Predictions
- ✅ Financial Nudges
- ✅ Interactive charts

#### UI/UX
- ✅ Modern Material Design 3
- ✅ Jetpack Compose
- ✅ Dark/Light theme support
- ✅ Responsive layouts
- ✅ Smooth animations

---

## 🔄 Data Flow

### Transaction Upload Flow

1. **User uploads CSV/Excel** → File parsed
2. **For each transaction**:
   - Preprocess narration
   - Check user corrections (preprocessed)
   - If match → Use correction
   - Else → Check keywords
   - If match → Use keyword category
   - Else → Run ML model
3. **Save to database** with predictions
4. **Display in UI** with confidence scores

### User Correction Flow

1. **User corrects category** in UI
2. **Save to database** (update transaction)
3. **Save to JSON** (`user_corrections.json`)
   - Narration preprocessed before storing
   - Includes `userId` and `transactionId` (for Efficient Mode cloud sync)
   - Stored in-memory for fast lookup
4. **Future predictions** automatically use correction
5. **Efficient Mode** (future): 
   - Sync corrections to cloud for cross-device access
   - Generate vector embeddings using DistilBERT
   - Store in vector database for similarity matching
   - Share common corrections (commodities) with other users

### Model Training Flow

1. **Export corrections** from database → CSV
2. **Load training data** + corrections
3. **Train DistilBERT** (multi-task)
4. **Convert to PyTorch Mobile** (.ptl)
5. **Copy to mobile app** assets
6. **Mobile app** loads model on startup

---

## 📁 Key Files

### Backend
- `TransactionController.java` - REST endpoints
- `TransactionService.java` - Business logic
- `LocalModelInferenceService.java` - Python script caller
- `TransactionCategorizationService.java` - Prediction orchestration
- `SpendingPatternService.java` - Pattern detection
- `TrendAnalysisService.java` - Trend analysis
- `SpendingPredictionService.java` - Future predictions
- `FinancialNudgeService.java` - Nudge generation

### Mobile
- `BudgetBuddyApplication.kt` - App initialization
- `PyTorchMobileInferenceService.kt` - ML inference
- `KeywordMatcher.kt` - Keyword matching
- `TextPreprocessor.kt` - Text preprocessing
- `TransactionViewModel.kt` - Transaction UI logic
- `FinancialGuidanceScreen.kt` - Guidance UI
- `BudgetBuddyDatabase.kt` - Room database

### ML
- `train_distilbert.py` - Model training
- `inference_local.py` - Inference script (called from Java)
- `distilbert_inference.py` - Core inference module
- `preprocessing_utils.py` - Text preprocessing
- `add_correction.py` - Add corrections to JSON
- `export_corrections.py` - Export from database

---

## 🗄️ Database Schema

### Spring Boot (MySQL)
- `transaction` - Transactions with ML predictions
- `user` - Users
- `category_keyword` - Keyword mappings
- `spending_pattern` - Detected patterns
- `spending_prediction` - Future predictions
- `financial_nudge` - Personalized nudges

### Mobile (SQLite/Room)
- `transactions` - Transactions (same structure)
- `users` - Users
- `category_keywords` - Keyword mappings
- `spending_patterns` - Patterns
- `spending_predictions` - Predictions
- `financial_nudges` - Nudges
- **Version**: 2 (with migrations)

---

## 🚀 Recent Updates

### User Corrections System
- ✅ JSON-based corrections storage
- ✅ Preprocessed narration matching
- ✅ In-memory cache for fast lookup
- ✅ Automatic application during inference
- ✅ UI updates immediately reflect corrections

### ML Inference Order
- ✅ User corrections (highest priority)
- ✅ Keyword matching (rule-based)
- ✅ DistilBERT model (ML prediction)

### Mobile App
- ✅ PyTorch Mobile integration
- ✅ On-device ML inference
- ✅ Keyword matching from database
- ✅ Financial Guidance System
- ✅ Complete feature parity with backend

### Code Cleanup
- ✅ Removed unused code
- ✅ Removed duplicate files
- ✅ Consolidated documentation
- ✅ Fixed build issues

---

## 📱 Mobile App Details

### Build Configuration
- **minSdk**: 26
- **targetSdk**: 34
- **Kotlin**: 1.9.20
- **Compose**: 1.5.8
- **PyTorch Mobile**: 2.1.0

### Model Files (Assets)
- `distilbert_model.ptl` - PyTorch Mobile model (~255 MB, not in git)
- `model_info.json` - Model metadata
- `vocab.txt` - Tokenizer vocabulary
- `keywords.json` - Category keywords (648 keywords)

### Key Features
- Offline-first (all data local)
- On-device ML (no server needed)
- Fast predictions (in-memory corrections + keywords)
- Complete feature set matching backend

---

## 🔐 Security & Privacy

- **Local-first**: All data stored locally
- **No cloud sync**: Data never leaves device (mobile)
- **Encryption**: Can be added with SQLCipher (commented out)
- **Password**: Stored as plain text (should be hashed in production)

---

## ☁️ Efficient Mode (Planned Feature)

### Overview
**Efficient Mode** is a planned cloud synchronization feature that will allow user corrections to be synced across multiple devices and instances. The infrastructure is already in place and ready for implementation. The system will leverage **vector databases** to identify and share common corrections (commodities) among users, creating a collaborative learning ecosystem.

### Current Status
- ✅ **Backend Infrastructure Ready**: User corrections already store `userId` and `transactionId` metadata
- ✅ **Data Format**: Corrections JSON includes optional `userId` and `transactionId` fields
- ⏳ **UI**: Efficient Mode button exists in UI but is currently disabled (marked as "Coming soon")
- ⏳ **Cloud Sync**: Actual sync functionality to be implemented
- ⏳ **Vector Database**: To be integrated for similarity matching and commodity sharing

### How It Works (Planned)

#### Data Structure
User corrections in `user_corrections.json` already support Efficient Mode metadata:
```json
{
  "narration": "UPI-PAYTM-MERCHANT",
  "category": "Shopping",
  "userId": "1",                    // For cloud sync
  "transactionId": "12345",         // For cloud sync
  "timestamp": "2024-11-17T10:30:00"
}
```

#### Vector Database Integration
Efficient Mode will use vector databases (e.g., Pinecone, Weaviate, or Chroma) to:

1. **Embed Transaction Narrations**: Convert preprocessed narrations into vector embeddings using the same DistilBERT model
2. **Similarity Matching**: Find similar corrections from other users using cosine similarity
3. **Commodity Detection**: Identify commonly corrected patterns across the user base
4. **Shared Learning**: Automatically suggest corrections based on what other users have corrected for similar transactions

#### Benefits
1. **Cross-Device Sync**: Corrections made on one device/instance sync to all others
2. **Centralized Learning**: All user corrections contribute to model retraining
3. **Backup**: Corrections stored in cloud, not just locally
4. **Multi-User Support**: Each user's corrections tracked separately via `userId`
5. **Commodity Sharing**: Common corrections shared among users via vector similarity
6. **Faster Learning**: New users benefit from corrections made by existing users
7. **Pattern Recognition**: Identify merchant patterns and common transaction types across users

#### Architecture (Planned)

```
User Correction Flow:
1. User makes correction → Saved locally (userId, transactionId)
2. Efficient Mode ON → Upload to cloud
3. Vector DB:
   - Embed narration (preprocessed) using DistilBERT
   - Store vector + category + metadata
   - Find similar vectors (cosine similarity > threshold)
4. Commodity Detection:
   - If similar corrections exist from multiple users → Mark as "commodity"
   - Share commodity corrections with all users
   - Use for faster predictions (commodity lookup before model inference)
```

#### Implementation Details
- **Storage**: Corrections stored with `userId` and `transactionId` for efficient cloud sync
- **Format**: JSON structure already supports metadata fields
- **Backend**: `add_correction.py` and `TransactionService.java` already pass these fields
- **UI**: Mode selector exists in transaction list page (currently disabled)
- **Vector Embeddings**: Will use DistilBERT embeddings for similarity matching
- **Commodity Threshold**: Configurable similarity threshold (e.g., 0.85) for commodity detection

#### Future Work
- Implement cloud API endpoints for sync
- Integrate vector database (Pinecone/Weaviate/Chroma)
- Add embedding generation pipeline (using DistilBERT)
- Implement similarity search and commodity detection
- Add authentication/authorization
- Implement conflict resolution (when same correction exists on multiple devices)
- Add sync status indicators in UI
- Enable Efficient Mode button when backend is ready
- Create commodity correction cache for faster lookups
- Add privacy controls (opt-in/opt-out for commodity sharing)

---

## 📈 Performance

### Backend
- **Batch prediction**: Processes multiple transactions in one Python call
- **Caching**: Model loaded once (singleton)
- **Keyword matching**: O(n) where n = number of keywords
- **Corrections**: O(1) in-memory lookup

### Mobile
- **On-device inference**: No network latency
- **In-memory corrections**: Instant lookup
- **Room database**: Efficient local storage
- **Batch operations**: Optimized for bulk imports

---

## 🛠️ Development Setup

### Backend
```bash
# Prerequisites
- Java 17+
- MySQL
- Python 3.9+ with PyTorch

# Run
./gradlew bootRun
```

### Mobile
```bash
# Prerequisites
- Android Studio
- Android SDK
- Python 3.9+ (for model conversion)

# Build
cd mobile-version
./gradlew assembleDebug

# Install
./gradlew installDebug
```

### ML Model
```bash
# Generate model
cd mybudget-ai
python3 -m venv venv
source venv/bin/activate
pip install torch transformers safetensors pyyaml
python3 convert_to_pytorch_mobile.py
```

---

## 📝 Documentation Files

- `README.md` - Main project README
- `mobile-version/README.md` - Mobile app README
- `mobile-version/MODEL_SETUP_INSTRUCTIONS.md` - Model setup guide
- `mobile-version/FEATURE_VALIDATION.md` - Feature comparison
- `mobile-version/PREDICTION_COMPARISON.md` - ML logic comparison
- `mobile-version/COMMANDS.md` - Useful commands
- `PROJECT_SUMMARY.md` - This file

---

## 🎯 Project Status

### ✅ Completed
- Spring Boot backend with full features
- Android mobile app with feature parity
- ML model training and conversion
- User corrections system
- Financial Guidance System
- Keyword matching
- Text preprocessing
- Database migrations
- UI/UX improvements

### 🔄 In Progress / Future
- Advanced filtering options
- Category management UI enhancements
- Export functionality
- **Efficient Mode** (cloud sync + vector database for commodity sharing - infrastructure ready)
- Database encryption
- Performance optimizations

---

## 📊 Statistics

- **Categories**: 10+ top-level, 30+ subcategories
- **Keywords**: 648 keyword mappings
- **Model Size**: ~255 MB (quantized)
- **Training Data**: 10K+ transactions
- **Mobile APK**: ~50-60 MB (with model)

---

## 🔗 Key Workflows

### Adding a Transaction
1. User enters narration + amount
2. System preprocesses narration
3. Checks corrections → keywords → model
4. Returns prediction with confidence
5. User confirms or corrects
6. Saved to database + corrections JSON

### Training Model with Corrections
1. Export corrections from database
2. Load training data + corrections
3. Train DistilBERT (multi-task)
4. Convert to PyTorch Mobile
5. Copy to mobile assets
6. Mobile app uses updated model

### Financial Guidance Generation
1. Analyze transaction history
2. Detect spending patterns
3. Calculate trends
4. Predict future spending
5. Generate personalized nudges
6. Display in UI with charts

---

## 🎓 Learning Outcomes

This project demonstrates:
- Multi-platform development (Web + Mobile)
- ML model integration (Python → Java/Kotlin)
- On-device ML inference
- Multi-task learning
- Text preprocessing and NLP
- Database design and migrations
- Modern Android development (Compose, MVVM)
- Spring Boot best practices
- User feedback integration
- Financial data analysis

---

## 📞 Support & Maintenance

### Model Updates
- Retrain when corrections accumulate
- Convert to mobile format
- Update assets in mobile app

### Database Migrations
- Backend: JPA auto-migration
- Mobile: Room migrations (version 2)

### Corrections Management
- Stored in JSON (easy to edit)
- Preprocessed for matching
- In-memory for performance

---

*Last Updated: November 2024*

