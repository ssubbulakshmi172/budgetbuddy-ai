# BudgetBuddy Mobile - Android App

Android mobile application for BudgetBuddy AI transaction categorization system.

## 📱 Overview

BudgetBuddy Mobile is an Android app that provides on-device transaction categorization using PyTorch Mobile. The app allows users to:
- Add transactions manually
- Import transactions from Excel/CSV files
- View categorized transactions
- Manage categories
- See spending analytics

## 🎯 Key Features

✅ **On-Device ML Inference**
- Uses PyTorch Mobile for category prediction
- DistilBERT multi-task model (category, transaction type, intent)
- No internet connection required
- Real-time predictions as you type
- User corrections support (stored locally)

✅ **Transaction Management**
- Manual transaction entry
- Bulk import from Excel/CSV files
- Category assignment and editing
- Filter and search transactions
- Transaction history view

✅ **Offline-First**
- All data stored locally (Room Database)
- No network dependencies
- Works completely offline
- Local ML inference (no API calls)

✅ **Category Management**
- View and manage transaction categories
- Custom category keywords (from categories.yml)
- ML-powered automatic categorization
- Keyword matching fallback
- User correction support

✅ **Financial Guidance** (Planned)
- Spending pattern detection
- Category-wise spending analysis
- Trend visualization
- Budget tracking

## 🚀 Quick Start

### Prerequisites

- Android Studio Hedgehog or later
- JDK 17 (Corretto 17.0.13)
- Android SDK (API 26+)
- ADB (Android Debug Bridge)
- Python 3.9+ (for model conversion)
- **For Apple Silicon Macs**: Use virtual environment with ARM64 PyTorch (see Model Setup below)

### Setup Steps

1. **Open Project**
   ```bash
   cd mobile-version
   # Open in Android Studio
   ```

2. **Generate ML Model** (Using Virtual Environment - Recommended)
   
   **Option A: Using Helper Script (Easiest)**
   ```bash
   cd mobile-version
   ./convert_model.sh
   ```
   This script automatically:
   - Creates a virtual environment (`venv`)
   - Installs ARM64-compatible PyTorch
   - Converts the model to `.ptl` format
   - Places it in `app/src/main/assets/distilbert_model.ptl`
   
   **Option B: Manual Setup**
   ```bash
   cd mobile-version
   
   # Create virtual environment
   python3 -m venv venv
   source venv/bin/activate
   
   # Install dependencies
   pip install --upgrade pip setuptools wheel
   pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cpu
   pip install pyyaml transformers safetensors
   
   # Convert model
   python3 convert_to_pytorch_mobile.py
   ```
   
   **Note:** Using a virtual environment ensures you have the correct ARM64 PyTorch for Apple Silicon Macs. The `.ptl` file is architecture-agnostic and works on all Android devices.
   
   **See also:** `MODEL_SETUP_INSTRUCTIONS.md` for detailed information.

3. **Sync Gradle**
   - File → Sync Project with Gradle Files
   - Or: `./gradlew build`

4. **Build and Install**
   ```bash
   ./gradlew assembleDebug
   ./gradlew installDebug
   ```

5. **Run on Device/Emulator**
   - Connect device or start emulator
   - Run from Android Studio or use ADB

## 📋 Architecture

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

## 🔧 Configuration

### Gradle Properties

Edit `gradle.properties`:
```
org.gradle.jvmargs=-Xmx8192m -XX:MaxMetaspaceSize=512m
org.gradle.java.home=/path/to/jdk17
```

### Model Configuration

Model file location: `app/src/main/assets/distilbert_model.ptl`

Model info: `app/src/main/assets/model_info.json`

## 📋 Checking Logs

### View Application Logs

```bash
# View recent logs
~/Library/Android/sdk/platform-tools/adb logcat -d | tail -20

# Filter by app
~/Library/Android/sdk/platform-tools/adb logcat -d | grep -i "BudgetBuddy" | tail -20

# Real-time logs
~/Library/Android/sdk/platform-tools/adb logcat | grep "BudgetBuddy"

# View errors
~/Library/Android/sdk/platform-tools/adb logcat -d | grep -i "error\|exception" | tail -30
```

### Common Log Tags

- `BudgetBuddyApp` - Application lifecycle
- `AddTransaction` - Transaction creation
- `FileImporter` - Excel/CSV import
- `PyTorchMobile` - ML model inference
- `DATABASE_DEBUG` - Database operations

**For detailed log commands, see:** [SETUP_AND_DEBUG.md](SETUP_AND_DEBUG.md#checking-logs)

## 🗄️ Database

**Room Database:** `budgetbuddy_db`

**Tables:**
- `transactions` - Transaction records
- `users` - User accounts
- `category_keywords` - Category mappings

**Access Database:**
- Android Studio: View → Tool Windows → App Inspection
- ADB: See [SETUP_AND_DEBUG.md](SETUP_AND_DEBUG.md#database-debugging)

## 🧪 Testing

```bash
# Unit tests
./gradlew test

# Instrumented tests
./gradlew connectedAndroidTest

# Lint check
./gradlew lint
```

## 📦 Build Outputs

**Debug APK:** `app/build/outputs/apk/debug/app-debug.apk`

**Install APK:**
```bash
./gradlew installDebug
# Or manually:
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 🐛 Troubleshooting

### Build Issues

1. **OutOfMemoryError:**
   - Check `gradle.properties` has `-Xmx8192m`
   - Increase if needed: `-Xmx12288m`

2. **Gradle Sync Fails:**
   ```bash
   ./gradlew --refresh-dependencies
   ```

3. **Clean Build:**
   ```bash
   ./gradlew clean
   ./gradlew build
   ```

### Runtime Issues

1. **App Crashes:**
   - Check Logcat for stack traces
   - Verify model file exists
   - Check database initialization

2. **ML Predictions Fail:**
   - Verify `distilbert_model.ptl` exists in assets (should be ~256 MB)
   - Check Logcat for PyTorchMobile errors
   - See [MODEL_SETUP_INSTRUCTIONS.md](MODEL_SETUP_INSTRUCTIONS.md)
   - **Architecture Issues**: If conversion fails with architecture errors, use the virtual environment setup (see Model Setup section)

3. **Model Conversion Fails:**
   - **Architecture Mismatch (x86_64 vs arm64)**: Use virtual environment:
     ```bash
     cd mobile-version
     python3 -m venv venv
     source venv/bin/activate
     pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cpu
     python3 convert_to_pytorch_mobile.py
     ```
   - See `MODEL_SETUP_INSTRUCTIONS.md` for architecture details
   - See `convert_model.sh` for automated setup script

3. **File Import Fails:**
   - Check file format (XLSX/XLS/CSV)
   - Verify file permissions
   - Check Logcat for parsing errors

**For detailed troubleshooting, see:** [SETUP_AND_DEBUG.md](SETUP_AND_DEBUG.md)

## 📚 Documentation

- **[SETUP_AND_DEBUG.md](SETUP_AND_DEBUG.md)** - Complete setup and debugging guide
- **[MODEL_SETUP_INSTRUCTIONS.md](MODEL_SETUP_INSTRUCTIONS.md)** - ML model conversion and setup (includes architecture info)
- **[convert_model.sh](convert_model.sh)** - Automated model conversion script
- **[PREDICTION_COMPARISON.md](PREDICTION_COMPARISON.md)** - Python vs Mobile prediction logic comparison
- **[PREDICTION_FIXES_SUMMARY.md](PREDICTION_FIXES_SUMMARY.md)** - Prediction fixes implementation summary
- **[MODELS_SUMMARY.md](MODELS_SUMMARY.md)** - Data models and database schema documentation
- **[FEATURE_VALIDATION.md](FEATURE_VALIDATION.md)** - Feature comparison with Spring Boot app
- **[COMMANDS.md](COMMANDS.md)** - Useful commands reference

## 🔗 Related Projects

- **Backend/Web App:** See main [README.md](../README.md)
- **ML Training:** See [mybudget-ai/](../mybudget-ai/) directory

## 📝 Recent Updates (v1.2.0)

- ✅ On-device ML inference with PyTorch Mobile
- ✅ Excel/CSV file import
- ✅ Offline-first architecture
- ✅ Real-time category predictions
- ✅ Room database for local storage
- ✅ **Virtual environment setup for model conversion** (ARM64 support)
- ✅ **Automated model conversion script** (`convert_model.sh`)
- ✅ **Text preprocessing** matching Python model behavior
- ✅ **Keyword matching** with 624+ keywords loaded from `categories.yml`
- ✅ **Subcategory extraction** from category strings
- ✅ **Full confidence scores** for all prediction tasks

---

**Last Updated:** November 2025  
**Version:** 1.2.0  
**Status:** Production Ready

