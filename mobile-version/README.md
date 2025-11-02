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
- No internet connection required
- Real-time predictions as you type

✅ **Transaction Management**
- Manual transaction entry
- Bulk import from Excel/CSV files
- Category assignment and editing

✅ **Offline-First**
- All data stored locally (Room Database)
- No network dependencies
- Works completely offline

✅ **Category Management**
- View and manage transaction categories
- Custom category keywords
- ML-powered automatic categorization

## 🚀 Quick Start

### Prerequisites

- Android Studio Hedgehog or later
- JDK 17 (Corretto 17.0.13)
- Android SDK (API 26+)
- ADB (Android Debug Bridge)
- Python 3.9+ (for model conversion)

### Setup Steps

1. **Open Project**
   ```bash
   cd mobile-version
   # Open in Android Studio
   ```

2. **Generate ML Model**
   - See `MODEL_SETUP.md` for detailed instructions
   - Run: `python3 convert_to_pytorch_mobile.py`
   - Place model file: `app/src/main/assets/distilbert_model.ptl`

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
   - Verify `distilbert_model.ptl` exists in assets
   - Check Logcat for PyTorchMobile errors
   - See [MODEL_SETUP.md](MODEL_SETUP.md)

3. **File Import Fails:**
   - Check file format (XLSX/XLS/CSV)
   - Verify file permissions
   - Check Logcat for parsing errors

**For detailed troubleshooting, see:** [SETUP_AND_DEBUG.md](SETUP_AND_DEBUG.md)

## 📚 Documentation

- **[SETUP_AND_DEBUG.md](SETUP_AND_DEBUG.md)** - Complete setup and debugging guide
- **[MODEL_SETUP.md](MODEL_SETUP.md)** - ML model conversion and setup

## 🔗 Related Projects

- **Backend/Web App:** See main [README.md](../README.md)
- **ML Training:** See [mybudget-ai/](../mybudget-ai/) directory

## 📝 Recent Updates (v1.2.0)

- ✅ On-device ML inference with PyTorch Mobile
- ✅ Excel/CSV file import
- ✅ Offline-first architecture
- ✅ Real-time category predictions
- ✅ Room database for local storage

---

**Last Updated:** November 2025  
**Version:** 1.2.0  
**Status:** Production Ready

