# GitHub Authentication Setup

GitHub requires a **Personal Access Token (PAT)** for authentication.

## Method 1: Create Personal Access Token (Recommended)

1. Go to: https://github.com/settings/tokens/new
2. **Note:** `budgetbuddy-ai-repo`
3. **Expiration:** Choose 90 days or custom
4. **Select scopes:** Check `repo` (full control of private repositories)
5. Click **Generate token**
6. **Copy the token immediately** (you won't see it again!)

## Method 2: Use Token in Git Command

After getting the token, you have two options:

### Option A: Enter token when prompted
```bash
git push -u origin main
# Username: ssubbulakshmi172
# Password: <paste-your-token-here>
```

### Option B: Save token in Git credential helper
```bash
# For macOS (uses Keychain)
git config --global credential.helper osxkeychain

# Then push (enter token when prompted, it will be saved)
git push -u origin main
```

### Option C: Embed in remote URL (less secure, but works)
```bash
git remote set-url origin https://YOUR_TOKEN@github.com/ssubbulakshmi172/budgetbuddy-ai.git
git push -u origin main
```

## Method 3: Use SSH Instead

If you have SSH keys set up:

```bash
git remote set-url origin git@github.com:ssubbulakshmi172/budgetbuddy-ai.git
git push -u origin main
```

