# How to Upload APK Files to GitHub Releases

Since APK files exceed GitHub's 100MB file size limit, they cannot be committed directly to the repository. Instead, use **GitHub Releases** to distribute APK files.

## Method 1: Using GitHub Web Interface (Easiest)

1. **Create a Release:**
   - Go to your GitHub repository: `https://github.com/ssubbulakshmi172/budgetbuddy-ai`
   - Click on "Releases" → "Create a new release"
   - Tag version: `v1.0.0` (or your version)
   - Release title: `BudgetBuddy Mobile v1.0.0`
   - Description: Add release notes
   - Click "Publish release"

2. **Attach APK Files:**
   - After creating the release, click "Edit release"
   - Scroll down to "Attach binaries"
   - Drag and drop your APK files:
     - `app-arm64-v8a-debug.apk`
     - `app-armeabi-v7a-debug.apk`
     - `app-x86-debug.apk`
     - `app-x86_64-debug.apk`
   - Or upload a single universal APK if you build one
   - Click "Update release"

## Method 2: Using GitHub CLI (gh)

```bash
# Install GitHub CLI if not installed
# macOS: brew install gh
# Then authenticate: gh auth login

# Create release and upload APKs
gh release create v1.0.0 \
  --title "BudgetBuddy Mobile v1.0.0" \
  --notes "Initial release of BudgetBuddy Mobile app" \
  mobile-version/app/build/outputs/apk/debug/*.apk
```

## Method 3: Using Git Tags + Manual Upload

```bash
# Tag your release
git tag -a v1.0.0 -m "BudgetBuddy Mobile v1.0.0"
git push origin v1.0.0

# Then go to GitHub web interface and attach APKs to the release
```

## Building Release APK (Smaller Size)

For production releases, build optimized APKs:

```bash
cd mobile-version

# Build release APK (smaller, optimized)
./gradlew assembleRelease

# APK will be at:
# mobile-version/app/build/outputs/apk/release/app-release.apk
```

Release APKs are typically 30-50% smaller than debug APKs.

## Recommended Approach

1. **Keep APKs in .gitignore** (already done ✅)
2. **Build release APKs** for distribution
3. **Upload to GitHub Releases** when ready to distribute
4. **Link to releases** in your README.md

## Example README Entry

```markdown
## 📱 Download Mobile App

Download the latest APK from [Releases](https://github.com/ssubbulakshmi172/budgetbuddy-ai/releases)

- **Latest**: [v1.0.0](https://github.com/ssubbulakshmi172/budgetbuddy-ai/releases/tag/v1.0.0)
- Choose the APK matching your device architecture:
  - ARM64 (most modern devices): `app-arm64-v8a-release.apk`
  - ARM32 (older devices): `app-armeabi-v7a-release.apk`
```

## Alternative: Git LFS (Not Recommended)

Git LFS can handle large files but:
- Requires Git LFS installation
- Has bandwidth limits on free tier
- More complex setup
- **GitHub Releases is preferred** for binary distributions

