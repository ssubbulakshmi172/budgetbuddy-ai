# Steps to Create New GitHub Repository and Push Code

## Step 1: Create Repository on GitHub (Web UI)

1. Go to: https://github.com/new
2. **Repository name:** (choose one)
   - `budgetbuddy-ai`
   - `transaction-categorisation-ai`
   - `budgetbuddy-ml`
3. **Description:** Automated AI-Based Financial Transaction Categorisation System
4. Choose **Public** or **Private**
5. **IMPORTANT:** DO NOT check:
   - ❌ Add a README file
   - ❌ Add .gitignore
   - ❌ Choose a license
6. Click **Create repository**

## Step 2: Update Remote and Push

After creating the repo on GitHub, run these commands (replace `NEW_REPO_NAME` with your repo name):

```bash
# Remove old remote
git remote remove origin

# Add new remote (replace NEW_REPO_NAME with your actual repo name)
git remote add origin https://github.com/ssubbulakshmi172/NEW_REPO_NAME.git

# Verify remote
git remote -v

# Push to new repo
git branch -M main
git push -u origin main
```

## Quick Command (Copy-Paste Ready)

Replace `YOUR_REPO_NAME` below:

```bash
git remote remove origin && \
git remote add origin https://github.com/ssubbulakshmi172/YOUR_REPO_NAME.git && \
git branch -M main && \
git push -u origin main
```

