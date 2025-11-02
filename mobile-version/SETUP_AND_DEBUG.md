# Setup and Debug Guide

## 📱 Project Setup

### Prerequisites
- Android Studio Hedgehog or later
- JDK 17 (Corretto 17.0.13)
- Android SDK (API 26+)
- Gradle 8.8
- ADB (Android Debug Bridge)
- **Python 3.9+ with PyTorch** (for model conversion - see MODEL_SETUP.md)

### Initial Setup

1. **Open Project in Android Studio**
   ```bash
   cd mobile-version
   # Open Android Studio and select this directory
   ```

2. **Sync Gradle**
   - In Android Studio: File → Sync Project with Gradle Files
   - Or command line: `./gradlew build`

3. **Configure JDK**
   - File → Project Structure → SDK Location
   - Set JDK path: `/Users/ssankaralingam/Library/Java/JavaVirtualMachines/corretto-17.0.13/Contents/Home`

4. **Generate Model File**
   - **IMPORTANT:** The ML model file (`distilbert_model.ptl`) is not in git (too large)
   - Generate it using: `python3 convert_to_pytorch_mobile.py`
   - See `MODEL_SETUP.md` for detailed instructions
   - Place the generated file in: `app/src/main/assets/distilbert_model.ptl`

5. **Install Dependencies**
   - Gradle will automatically download dependencies on first build
   - Includes: PyTorch Mobile, Room Database, Jetpack Compose, Apache POI

### Build Configuration

**Gradle Properties** (`gradle.properties`):
```
org.gradle.jvmargs=-Xmx8192m -XX:MaxMetaspaceSize=512m
org.gradle.java.home=/Users/ssankaralingam/Library/Java/JavaVirtualMachines/corretto-17.0.13/Contents/Home
```

**Build Commands**:
```bash
# Clean build
./gradlew clean

# Build debug APK
./gradlew assembleDebug

# Build and install on connected device
./gradlew installDebug

# Check for errors only
./gradlew compileDebugKotlin
```

## 🐛 Debugging Guide

### Common Issues and Solutions

#### 1. Build Failures

**OutOfMemoryError during build:**
```
Error: Java heap space
Solution: Already configured in gradle.properties (-Xmx8192m)
```

**Kotlin compilation errors:**
```bash
# Check specific errors
./gradlew compileDebugKotlin --stacktrace | grep "error:"

# Clean and rebuild
./gradlew clean build
```

**Missing dependencies:**
```bash
# Sync Gradle
./gradlew --refresh-dependencies
```

#### 2. Runtime Debugging

**View Logcat Output:**
```bash
# Filter by app logs
~/Library/Android/sdk/platform-tools/adb logcat | grep "BudgetBuddy"

# Filter by specific tags
~/Library/Android/sdk/platform-tools/adb logcat | grep -E "(AddTransaction|UploadScreen|Dashboard|DATABASE_DEBUG)"

# Clear logs and watch
~/Library/Android/sdk/platform-tools/adb logcat -c && ~/Library/Android/sdk/platform-tools/adb logcat
```

**Common Log Tags:**
- `BudgetBuddyApp` - Application lifecycle
- `AddTransaction` - Transaction creation
- `AddTransactionViewModel` - Transaction state management
- `UploadViewModel` - File upload processing
- `FileImporter` - Excel/CSV import
- `DATABASE_DEBUG` - Database queries
- `PyTorchMobile` - ML model inference

#### 3. Database Debugging

**View Database Data:**
1. **Android Studio Database Inspector:**
   - View → Tool Windows → App Inspection
   - Select device → `budgetbuddy_db`
   - Browse tables and run SQL queries

2. **ADB Shell:**
   ```bash
   ~/Library/Android/sdk/platform-tools/adb shell
   run-as com.budgetbuddy.mobile
   cd databases
   sqlite3 budgetbuddy_db
   
   # Example queries
   SELECT * FROM transactions;
   SELECT COUNT(*) FROM transactions;
   .quit
   exit
   ```

3. **Automatic Logging:**
   - Dashboard screen automatically logs all transactions on load
   - Check Logcat for `DATABASE_DEBUG` tag

**Pull Database File:**
```bash
~/Library/Android/sdk/platform-tools/adb pull /data/data/com.budgetbuddy.mobile/databases/budgetbuddy_db ~/Downloads/
```

#### 4. UI Issues

**Scroll Not Working:**
- Fixed: Use nested Column structure with `verticalScroll()` on outer Column
- Check: `fillMaxWidth()` instead of `fillMaxSize()` for scrollable content

**Touch/Click Not Working:**
- Verify `onClick` lambda is passed correctly
- Check for overlapping UI elements
- Ensure `enabled = true` on clickable components

**Navigation Issues:**
- Check Logcat for navigation logs
- Verify route names match in `Navigation.kt`
- Ensure `NavController` is passed correctly

#### 5. ML Model Issues

**PyTorch Mobile Initialization:**
```bash
# Check model loading
~/Library/Android/sdk/platform-tools/adb logcat | grep "PyTorchMobile"

# Verify model files exist
~/Library/Android/sdk/platform-tools/adb shell ls -lh /data/data/com.budgetbuddy.mobile/files/
```

**Model Prediction Errors:**
- Check Logcat for `AddTransactionViewModel` tags
- Verify input text is not empty
- Check model file path and permissions

#### 6. File Import Issues

**Excel/CSV Import Failures:**
```bash
# Watch import process
~/Library/Android/sdk/platform-tools/adb logcat | grep -E "(FileImporter|UploadViewModel)"

# Check file permissions
~/Library/Android/sdk/platform-tools/adb shell ls -l /sdcard/Download/
```

**Push Test File:**
```bash
# Push Excel file for testing
~/Library/Android/sdk/platform-tools/adb push ~/Downloads/transactions.xlsx /sdcard/Download/
```

## 🔧 Development Commands

### Build and Install
```bash
# Build debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug

# Or manually install
~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Testing
```bash
# Run unit tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest

# Check for lint errors
./gradlew lint
```

### Cleaning
```bash
# Clean build artifacts
./gradlew clean

# Clean Gradle cache (if issues persist)
rm -rf .gradle
rm -rf app/build
./gradlew clean build
```

## 📊 Performance Debugging

### Check APK Size
```bash
# View APK details
unzip -l app/build/outputs/apk/debug/app-debug.apk | head -20

# Check DEX file count
./gradlew assembleDebug --info | grep "methods count"
```

### Memory Profiling
- Android Studio: Run → Profile 'app'
- Monitor memory usage
- Check for leaks with Memory Profiler

### Network Debugging
- Currently offline-only (no network calls)
- All ML processing happens on-device

## 🗄️ Database Schema

**Tables:**
- `transactions` - User transaction records
- `users` - User accounts
- `category_keywords` - Category mapping

**View Schema:**
```sql
-- In Database Inspector or sqlite3
.schema transactions
.schema users
.schema category_keywords
```

## 📱 Device Setup

### Connect Device
```bash
# Check connected devices
~/Library/Android/sdk/platform-tools/adb devices

# Enable USB debugging on device
# Settings → Developer Options → USB Debugging

# Authorize computer on device (first time)
```

### Enable Developer Options
1. Settings → About Phone
2. Tap "Build Number" 7 times
3. Go back → Developer Options
4. Enable "USB Debugging"

## 🚨 Quick Fixes

### App Crashes
1. Check Logcat for stack trace
2. Verify all required permissions are granted
3. Check database initialization
4. Verify ML model files exist

### Build Errors
1. Clean project: `./gradlew clean`
2. Invalidate caches: File → Invalidate Caches
3. Sync Gradle: File → Sync Project
4. Rebuild: `./gradlew build`

### Import Errors
1. Check file format (XLSX/XLS/CSV)
2. Verify file permissions
3. Check Logcat for parsing errors
4. Verify file structure matches expected format

## 📝 Debug Checklist

Before reporting issues, check:
- [ ] Gradle sync successful
- [ ] No build errors
- [ ] Device/emulator connected
- [ ] USB debugging enabled
- [ ] Logcat shows app logs
- [ ] Database initialized
- [ ] ML model files present
- [ ] Permissions granted

## 🔗 Useful Resources

- **Android Studio**: https://developer.android.com/studio
- **Jetpack Compose**: https://developer.android.com/jetpack/compose
- **Room Database**: https://developer.android.com/training/data-storage/room
- **PyTorch Mobile**: https://pytorch.org/mobile/home/
- **ADB Commands**: https://developer.android.com/tools/adb



# View recent logs (last buffer)
~/Library/Android/sdk/platform-tools/adb logcat -d | tail -20

# View filtered logs (BudgetBuddy app only)
~/Library/Android/sdk/platform-tools/adb logcat -d | grep -i "BudgetBuddy\|FileImporter\|UploadViewModel" | tail -20

# View last 50 lines of all logs
~/Library/Android/sdk/platform-tools/adb logcat -d -t 50

# View logs in real-time (live, press Ctrl+C to stop)
~/Library/Android/sdk/platform-tools/adb logcat | grep -i "FileImporter\|UploadViewModel"

# Clear logs first, then view new logs
~/Library/Android/sdk/platform-tools/adb logcat -c && ~/Library/Android/sdk/platform-tools/adb logcat


 ~/Library/Android/sdk/platform-tools/adb shell "run-as com.budgetbuddy.mobile ls -lh /data/data/com.budgetbuddy.mobile/databases/ 2>/dev/null"



