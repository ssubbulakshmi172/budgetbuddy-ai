# BudgetBuddy AI - Complete Project Summary

**Version**: 2.2.0  
**Last Updated**: 2025-01-21  
**Status**: ✅ Production Ready

---

## 📋 Project Overview

BudgetBuddy AI is a comprehensive personal finance management application with AI-powered transaction categorization and intelligent financial guidance. The system automatically categorizes bank transactions using machine learning and provides actionable insights to help users understand and optimize their spending patterns.

### Key Highlights
- 🤖 **AI-Powered Categorization**: DistilBERT multi-task model with 90%+ accuracy
- 📊 **Financial Guidance**: 10+ rule-based analytics features
- 🔄 **Event-Driven Updates**: Automatic recalculation when transactions change
- 🎯 **User Corrections**: Learn from user feedback to improve accuracy
- 📱 **Multi-Platform**: Spring Boot web app + Android mobile app

---

## 🏗️ Architecture

### System Components

```
budgetbuddy-ai/
├── src/main/java/                    # Spring Boot Backend
│   ├── controller/                   # Web Controllers (8 controllers)
│   │   ├── TransactionController     # Transaction CRUD, import, filtering
│   │   ├── FinancialGuidanceController  # Financial insights dashboard
│   │   ├── CategoryKeywordController # Category management
│   │   ├── DashboardController       # Spending analytics dashboard
│   │   ├── HomeController            # Home page
│   │   ├── UserController            # User management
│   │   ├── ReportController          # REST API for reports
│   │   └── ReportViewController      # Reports view page
│   ├── service/                      # Business Logic (14 services)
│   │   ├── TransactionService        # Core transaction operations
│   │   ├── TransactionCategorizationService  # ML categorization orchestration
│   │   ├── LocalModelInferenceService # Python script execution
│   │   ├── MoneyLeakService          # Top spending leaks detection
│   │   ├── CategoryOverspendingService # Category overspending alerts
│   │   ├── SavingsProjectionService   # Year-end savings projection
│   │   ├── WeekendOverspendingService # Weekend spending analysis
│   │   ├── FinancialAnalyticsService  # Grocery vs dining, investments, subscriptions
│   │   ├── SpendingPatternService     # Spending pattern detection
│   │   ├── CategoryKeywordService     # Keyword management
│   │   ├── TaxonomyLoaderService      # Load categories from YAML
│   │   ├── DataCleanupService         # Data cleanup utilities
│   │   └── UserService                # User management
│   ├── repository/                   # Data Access Layer (9 repositories)
│   ├── model/                        # JPA Entities (9 models)
│   ├── event/                        # Spring Events
│   │   ├── TransactionChangedEvent   # Transaction change events
│   │   └── FinancialGuidanceUpdateListener  # Event-driven updates
│   ├── util/                         # Utilities
│   │   └── NarrationPreprocessor     # Text cleaning utilities
│   └── config/                       # Configuration
│
├── mybudget-ai/                      # ML Training & Inference
│   ├── train_distilbert.py           # Model training
│   ├── inference_local.py            # Local inference script
│   ├── anomaly_detection.py          # ML-based anomaly detection
│   ├── preprocessing_utils.py        # Text preprocessing utilities
│   ├── preprocess_narration.py       # Narration preprocessing script
│   ├── add_correction.py             # Add user corrections
│   ├── get_categories.py             # Extract categories from YAML
│   ├── categories.yml                # Category definitions
│   ├── user_corrections.json         # User corrections storage
│   └── models/                       # Trained models
│       └── distilbert_multitask_latest/
│
└── mobile-version/                   # Android Mobile App (Kotlin)
    └── app/src/main/java/com/budgetbuddy/mobile/
        ├── data/                     # Room Database, DAOs, Models
        ├── service/                  # Business Logic
        ├── ml/                       # PyTorch Mobile inference
        └── ui/                       # Jetpack Compose UI
```

---

## 🤖 ML/AI Components

### Model Architecture
- **Base Model**: DistilBERT (distilbert-base-uncased)
- **Tasks**: Multi-task learning
  - Transaction Type: P2C, P2P, P2Business
  - Category: 26+ categories with subcategories
  - Intent: purchase, transfer, refund, subscription, bill_payment, other
- **Performance**: 89.61% macro F1-score, 90.10% weighted F1-score
- **Format**: PyTorch (.pt) for backend, PyTorch Mobile (.ptl) for Android

### Inference Priority Order

1. **User Corrections** (Highest Priority - 100% confidence)
   - Source: `mybudget-ai/user_corrections.json`
   - Preprocessed narrations (cleaned of UPI tags, IDs)
   - In-memory cache for fast lookup
   - Persisted to `category_keyword` table with `categoriesFor = "Corrected"`

2. **Taxonomy Keywords** (Rule-Based)
   - Source: `categories.yml` → loaded into `category_keyword` table
   - Word-boundary matching
   - Longest keyword first
   - Auto-loaded on application startup via `TaxonomyLoaderService`

3. **DistilBERT Model** (ML Prediction)
   - Multi-task predictions
   - Confidence scores (0.0 to 1.0)
   - Subcategory extraction
   - Batch processing support

### Text Preprocessing
- Removes UPI prefixes (`UPI-`, `UPI/`)
- Removes bank tags (`@YBL`, `@HDFCBANK`)
- Removes transaction IDs (long numbers)
- Normalizes separators
- Preserves merchant names and semantic meaning
- Centralized in `NarrationPreprocessor` utility class

---

## 📊 Data Models

### Core Entities

#### Transaction
- `id`, `date`, `narration`, `amount`
- `withdrawalAmt`, `depositAmt`, `closingBalance`
- `userId` (foreign key to User)
- **ML Predictions**: `predictedCategory`, `predictedSubcategory`, `predictedTransactionType`, `predictedIntent`, `predictionConfidence`, `predictionReason`
- **User Assignment**: `categoryName` (manual override)

#### User
- `id`, `name`, `email`, `password`

#### CategoryKeyword
- `id`, `keyword`, `categoryName`, `categoriesFor` (Taxonomy/Manual/Corrected)
- Used for keyword matching and user corrections

### Financial Guidance Entities

#### CategoryOverspendingAlert
- Detects categories exceeding historical spending patterns
- Alert levels: LOW, MEDIUM, HIGH, CRITICAL
- Percentage increase tracking
- Projected monthly spending

#### MoneyLeak
- Top spending leaks detection
- Leak types: Subscription, Coffee Effect, ATM Spikes, Friend Covering, High-Impact One-Time, Emotional/Late-Night Spending
- Annual amount calculation
- Ranking system

#### SavingsProjection
- Year-end savings projection
- Based on current spending patterns
- Monthly and annual projections

#### WeekendOverspending
- Weekend vs weekday spending analysis
- Percentage increase tracking
- Category-wise breakdown

#### SalaryWeekAnalysis
- Salary week spending patterns
- Ratio analysis (salary week vs non-salary week)
- Anomaly detection

#### SpendingPattern
- Detected spending patterns (daily/weekly/monthly)
- Merchant pattern matching
- Frequency and confidence tracking

---

## ✨ Key Features

### 1. Transaction Management
- ✅ Add/Edit/Delete transactions
- ✅ Bulk import from CSV/Excel files
- ✅ Advanced filtering (date range, category, amount, narration)
- ✅ Sorting (date, amount, category)
- ✅ Bulk delete with filters
- ✅ Duplicate detection and removal
- ✅ Transaction correction (updates ML predictions)

### 2. AI Categorization
- ✅ Automatic category prediction using DistilBERT
- ✅ Multi-task prediction (category, type, intent)
- ✅ Confidence scores for all predictions
- ✅ Batch prediction refresh
- ✅ User corrections with persistence
- ✅ Keyword matching fallback
- ✅ Taxonomy loading from YAML

### 3. Financial Guidance Dashboard (10 Features)

#### Rule-Based Analytics:
1. **Top 3 Spending Areas** - Identifies top spending categories with amounts
2. **Category Overspending Alerts** - Flags categories exceeding historical patterns
3. **Weekend Overspending** - Analyzes weekend vs weekday spending
4. **Salary Week Analysis** - Detects spending patterns around salary dates
5. **Year-End Savings Projection** - Projects year-end savings based on current patterns
6. **Regular Monthly Spending** - Identifies recurring expenses and investments
7. **Grocery vs Eating-Out** - Compares grocery spending vs dining expenses
8. **Investment Tracking** - Tracks investment transactions separately
9. **Subscriptions Analysis** - Identifies recurring subscription payments
10. **Category Trend Visualization** - JSON data for chart visualization

#### ML-Based Analytics:
- **Unusual Spending Patterns (ML Detection)** - Isolation Forest-based anomaly detection
  - Filters out regular monthly spending
  - Identifies truly unusual transactions
  - Provides intent classification
  - Async loading with manual trigger button

### 4. Event-Driven Updates
- ✅ Automatic financial guidance recalculation on transaction changes
- ✅ Debouncing mechanism (30-second window)
- ✅ Manual reload endpoint (`/guidance/reload`)
- ✅ Force update option (bypasses debounce)

### 5. Category Management
- ✅ Three-tab interface: Corrected, Taxonomy, Manual
- ✅ Corrected categories from user corrections
- ✅ Taxonomy categories from `categories.yml`
- ✅ Manual categories (user-defined)
- ✅ Edit/Delete category keywords
- ✅ Automatic taxonomy loading on startup

### 6. Data Management
- ✅ Clear all transaction and financial guidance data
- ✅ Data cleanup service with comprehensive deletion
- ✅ SQL scripts for dropping unused tables

---

## 🔧 Technology Stack

### Backend (Spring Boot)
- **Framework**: Spring Boot 3.x
- **Database**: MySQL (JPA/Hibernate)
- **ML Inference**: Python scripts via ProcessBuilder
- **UI**: Thymeleaf templates
- **Build**: Gradle
- **Java Version**: 17+

### ML/AI
- **Framework**: PyTorch
- **Model**: DistilBERT (Hugging Face Transformers)
- **Training**: Multi-task learning
- **Python**: 3.9+
- **Libraries**: transformers, torch, pandas, numpy, scikit-learn

### Mobile (Android)
- **Language**: Kotlin
- **UI**: Jetpack Compose
- **Database**: Room (SQLite)
- **ML**: PyTorch Mobile 2.1.0
- **Architecture**: MVVM

---

## 📈 Financial Guidance Features (Detailed)

### 1. Top 3 Spending Areas
- Aggregates spending by category
- Excludes investment categories
- Shows spending amounts
- Horizontal display (1, 2, 3)
- "View Transactions" link for each category

### 2. Category Overspending Alerts
- Compares current month spending vs historical average (last 6 months)
- Alert levels based on percentage increase:
  - CRITICAL: >50% increase or >2× standard deviation
  - HIGH: 25-50% increase
  - MEDIUM: 10-25% increase
  - LOW: <10% increase
- Prevents duplicates (one alert per category per month)
- "View Transactions" link

### 3. Weekend Overspending
- Analyzes weekend (Sat-Sun) vs weekday spending
- Category-wise breakdown
- Percentage increase tracking
- Monthly analysis
- "View Transactions" link

### 4. Salary Week Analysis
- Detects salary dates (large deposits)
- Analyzes spending in salary week vs non-salary weeks
- Ratio calculation
- Anomaly detection
- "View Transactions" link

### 5. Year-End Savings Projection
- Projects savings based on current spending rate
- Monthly and annual projections
- Updates automatically with new transactions

### 6. Regular Monthly Spending
- Identifies recurring expenses (3+ months)
- Separates expenses and investments
- Excludes from anomaly detection
- "View All Transactions" link

### 7. Grocery vs Eating-Out
- Compares grocery store spending vs restaurant/dining
- Monthly comparison
- Percentage breakdown

### 8. Investment Tracking
- Tracks investment transactions separately
- Excludes from expense calculations
- Category-based detection

### 9. Subscriptions Analysis
- Identifies recurring subscription payments
- Monthly frequency tracking
- Amount aggregation

### 10. Category Trend Visualization
- JSON data for chart rendering
- 6-month trend analysis
- Category-wise spending trends

### 11. ML Anomaly Detection
- Isolation Forest algorithm
- Filters regular monthly spending
- Identifies unusual transactions
- Intent classification
- Async loading with manual trigger
- "View Transactions" link for each anomaly

---

## 🗄️ Database Schema

### Core Tables
- `transaction` - All transactions with ML predictions
- `users` - User accounts
- `category_keyword` - Keyword mappings (Taxonomy/Manual/Corrected)

### Financial Guidance Tables
- `category_overspending_alert` - Category overspending alerts
- `money_leak` - Top spending leaks
- `savings_projection` - Year-end savings projections
- `weekend_overspending` - Weekend spending analysis
- `salary_week_analysis` - Salary week analysis
- `spending_patterns` - Detected spending patterns

### Removed Tables (Cleanup Completed)
- ~~`financial_nudges`~~ - Removed (unused service)
- ~~`spending_predictions`~~ - Removed (unused service)

---

## 🔄 Event-Driven Architecture

### Transaction Change Events
- **Event**: `TransactionChangedEvent`
- **Triggers**: Transaction created, updated, or deleted
- **Listener**: `FinancialGuidanceUpdateListener`
- **Behavior**: 
  - Async processing
  - Debouncing (30-second window per user)
  - Automatic recalculation of all guidance features
  - Force update option for manual reloads

### Update Flow
```
Transaction Change → TransactionChangedEvent → 
FinancialGuidanceUpdateListener → 
Update All Guidance Services → 
Save to Database
```

---

## 🚀 Getting Started

### Prerequisites
- Java 17+
- Python 3.9+
- MySQL 8.0+
- Gradle 8.x

### Setup Steps

1. **Clone and Setup**
   ```bash
   git clone <repository>
   cd budgetbuddy-ai
   chmod +x setup_environment.sh
   bash setup_environment.sh
   ```

2. **Database Setup**
   ```bash
   mysql -u root -p
   CREATE DATABASE budgetbuddy_app;
   ```

3. **Run Application**
   ```bash
   ./gradlew bootRun
   ```

4. **Access Web Interface**
   - Home: http://localhost:8080
   - Transactions: http://localhost:8080/transactions
   - Financial Guidance: http://localhost:8080/guidance/dashboard
   - Dashboard: http://localhost:8080/dashboard

---

## 📝 Recent Changes (v2.1)

### Code Cleanup (January 2025)
- ✅ Removed unused services: `FinancialNudgeService`, `SpendingPredictionService`, `TrendAnalysisService`
- ✅ Removed unused models: `FinancialNudge`, `SpendingPrediction`
- ✅ Removed unused repositories: `FinancialNudgeRepository`, `SpendingPredictionRepository`
- ✅ Fixed `getCurrentUser()` method in `UserService`
- ✅ Created SQL script to drop unused database tables
- ✅ Updated `DataCleanupService` to remove references to deleted repositories

### Feature Enhancements
- ✅ Fixed duplicate Category Overspending alerts
- ✅ Added "Clear All Data" functionality with UI button
- ✅ Enhanced "Unusual Patterns" section with better UI and transaction links
- ✅ Added "View Transactions" links to all guidance sections
- ✅ Improved narration preprocessing with centralized utility

### Bug Fixes
- ✅ Fixed compilation errors (getUsername → getName)
- ✅ Fixed SQL column naming issues
- ✅ Fixed Thymeleaf expression errors
- ✅ Fixed empty state handling

---

## 📊 Statistics

- **Total Controllers**: 8
- **Total Services**: 14
- **Total Repositories**: 9
- **Total Models**: 9
- **Financial Guidance Features**: 11 (10 rule-based + 1 ML-based)
- **ML Model Accuracy**: 89.61% macro F1-score, 90.10% weighted F1-score
- **Supported Categories**: 26+ categories with subcategories

---

## 🔮 Future Enhancements

### Planned Features
- [ ] Multi-user support with proper authentication
- [ ] Budget setting and tracking
- [ ] Export reports (PDF, Excel)
- [ ] Email notifications for alerts
- [ ] Mobile app sync with backend
- [ ] Efficient Mode (cloud sync for corrections)
- [ ] Vector database for commodity corrections
- [ ] Advanced chart visualizations

### Technical Improvements
- [ ] Unit tests and integration tests
- [ ] API documentation (Swagger/OpenAPI)
- [ ] Performance optimization for large datasets
- [ ] Caching layer for frequently accessed data
- [ ] Background job processing for heavy calculations

---

## 📚 Documentation

- **README.md** - Setup and installation guide
- **PROJECT_SUMMARY.md** - This document
- **UNUSED_CODE_ANALYSIS.md** - Code cleanup documentation
- **ANOMALY_DETECTION_EXPLANATION.md** - ML anomaly detection details
- **scripts/drop_unused_tables.sql** - Database cleanup script

---

## 👥 Development

### Code Structure
- **Controllers**: Handle HTTP requests and responses
- **Services**: Business logic and orchestration
- **Repositories**: Data access layer (JPA)
- **Models**: Entity classes
- **Events**: Spring event-driven updates
- **Utils**: Utility classes for common operations

### Best Practices
- ✅ Separation of concerns (Controller → Service → Repository)
- ✅ Event-driven updates for financial guidance
- ✅ Centralized text preprocessing
- ✅ Consistent error handling
- ✅ Comprehensive logging
- ✅ Transaction management with proper rollback

---

## 📄 License

[Add your license information here]

---

**Last Updated**: January 2025  
**Version**: 2.1  
**Status**: ✅ Production Ready
