#!/bin/bash
# Script to create a new GitHub repo and push current code

# Replace these variables
REPO_NAME="budgetbuddy-ai"
REPO_DESCRIPTION="Automated AI-Based Financial Transaction Categorisation System"
GITHUB_USERNAME="ssubbulakshmi172"
IS_PRIVATE="false"  # Set to "true" for private repo

echo "🚀 Creating new GitHub repository: $REPO_NAME"

# Step 1: Check if git repo is initialized
if [ ! -d .git ]; then
    echo "❌ Not a git repository. Run: git init"
    exit 1
fi

# Step 2: Commit all changes (if not already committed)
git add -A
git commit -m "feat: Add comprehensive documentation and fix predicted category saving

- Add README.md with setup instructions and API documentation
- Add DATASET.md documenting data source and preprocessing
- Add PROJECT_COMPLIANCE.md with detailed requirements analysis
- Fix missing setPredictedCategory() call in TransactionService
- Update dashboard with improved design and 6-month comparison chart
- Fix Thymeleaf template expression errors
- Enhance UI uniformity with unified CSS design system"

# Step 3: Create repo on GitHub (requires GitHub CLI or manual creation)
echo ""
echo "📝 To create the repo, choose one method:"
echo ""
echo "METHOD 1: Using GitHub CLI (if installed):"
echo "  gh repo create $REPO_NAME --public --description \"$REPO_DESCRIPTION\" --source=. --remote=new-origin --push"
echo ""
echo "METHOD 2: Using curl (requires GitHub token):"
echo "  curl -u $GITHUB_USERNAME -H 'Accept: application/vnd.github.v3+json' \\"
echo "    -d '{\"name\":\"$REPO_NAME\",\"description\":\"$REPO_DESCRIPTION\",\"private\":$IS_PRIVATE}' \\"
echo "    https://api.github.com/user/repos"
echo ""
echo "METHOD 3: Manual (Recommended):"
echo "  1. Go to: https://github.com/new"
echo "  2. Repository name: $REPO_NAME"
echo "  3. Description: $REPO_DESCRIPTION"
echo "  4. Choose public/private"
echo "  5. DO NOT initialize with README, .gitignore, or license"
echo "  6. Click 'Create repository'"
echo ""
echo "Press Enter after creating the repo to continue..."
read

# Step 4: Remove old remote and add new one
echo "🔄 Updating git remote..."
git remote remove origin
git remote add origin https://github.com/$GITHUB_USERNAME/$REPO_NAME.git

# Step 5: Push to new repository
echo "📤 Pushing to new repository..."
git branch -M main
git push -u origin main

echo ""
echo "✅ Done! Your code is now at:"
echo "   https://github.com/$GITHUB_USERNAME/$REPO_NAME"

