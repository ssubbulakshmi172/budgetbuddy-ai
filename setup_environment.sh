#!/bin/bash

# BudgetBuddy AI - Automated Environment Setup Script
# This script automates the complete setup of the BudgetBuddy AI development environment
# Run with: bash setup_environment.sh
# Or make executable: chmod +x setup_environment.sh && ./setup_environment.sh

set -e  # Exit on any error

# Get the directory where this script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Change to project root directory (where this script is located)
cd "$SCRIPT_DIR"

# Setup log file with timestamp
LOG_FILE="setup_environment_$(date +%Y%m%d_%H%M%S).log"
LOG_FILE="${SCRIPT_DIR}/${LOG_FILE}"

echo "🚀 BudgetBuddy AI - Environment Setup"
echo "======================================"
echo "📁 Project root: $SCRIPT_DIR"
echo "📝 Log file: $LOG_FILE"
echo ""

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to log to both console and file
log_step() {
    local timestamp=$(date '+%Y-%m-%d %H:%M:%S')
    local message="[${timestamp}] $1"
    echo "$message" | tee -a "$LOG_FILE"
}

log_info() {
    local timestamp=$(date '+%Y-%m-%d %H:%M:%S')
    local message="[${timestamp}] INFO: $1"
    echo -e "${BLUE}ℹ️  $1${NC}" | tee -a "$LOG_FILE"
}

log_success() {
    local timestamp=$(date '+%Y-%m-%d %H:%M:%S')
    local message="[${timestamp}] SUCCESS: $1"
    echo -e "${GREEN}✅ $1${NC}" | tee -a "$LOG_FILE"
}

log_warning() {
    local timestamp=$(date '+%Y-%m-%d %H:%M:%S')
    local message="[${timestamp}] WARNING: $1"
    echo -e "${YELLOW}⚠️  $1${NC}" | tee -a "$LOG_FILE"
}

log_error() {
    local timestamp=$(date '+%Y-%m-%d %H:%M:%S')
    local message="[${timestamp}] ERROR: $1"
    echo -e "${RED}❌ $1${NC}" | tee -a "$LOG_FILE"
}

# Initialize log file
log_step "=========================================="
log_step "BudgetBuddy AI - Environment Setup Started"
log_step "Project root: $SCRIPT_DIR"
log_step "Timestamp: $(date)"
log_step "=========================================="
echo ""

# Step 1: Verify Prerequisites
log_step "=========================================="
log_step "STEP 1: Verifying Prerequisites"
log_step "=========================================="
echo ""

# Check Java
log_info "Checking Java installation..."
if command -v java &> /dev/null; then
    JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
    if [ "$JAVA_VERSION" -ge 17 ]; then
        log_success "Java $JAVA_VERSION found"
    else
        log_error "Java 17+ required. Found version $JAVA_VERSION"
        exit 1
    fi
else
    log_error "Java not found. Please install Java 17+"
    exit 1
fi

# Check Python
log_info "Checking Python installation..."
if command -v python3 &> /dev/null; then
    PYTHON_VERSION=$(python3 --version 2>&1 | awk '{print $2}' | cut -d'.' -f1,2)
    PYTHON_MAJOR=$(echo $PYTHON_VERSION | cut -d'.' -f1)
    PYTHON_MINOR=$(echo $PYTHON_VERSION | cut -d'.' -f2)
    if [ "$PYTHON_MAJOR" -ge 3 ] && [ "$PYTHON_MINOR" -ge 9 ]; then
        log_success "Python $PYTHON_VERSION found"
    else
        log_error "Python 3.9+ required. Found version $PYTHON_VERSION"
        exit 1
    fi
else
    log_error "Python3 not found. Please install Python 3.9+"
    exit 1
fi

# Check MySQL
log_info "Checking MySQL installation..."
if command -v mysql &> /dev/null; then
    log_success "MySQL client found"
else
    log_warning "MySQL client not found. Please ensure MySQL is installed"
fi

# Check Gradle wrapper
log_info "Checking Gradle wrapper..."
if [ -f "./gradlew" ]; then
    log_success "Gradle wrapper found"
else
    log_error "Gradle wrapper not found. Are you in the project root?"
    exit 1
fi

echo ""
log_step "=========================================="
log_step "STEP 2: Setting up Python Virtual Environment"
log_step "=========================================="
echo ""

# Navigate to ML service directory
cd mybudget-ai

# Create virtual environment if it doesn't exist
log_info "Checking for existing virtual environment..."
if [ ! -d "venv" ]; then
    log_info "Creating Python virtual environment..."
    python3 -m venv venv 2>&1 | tee -a "$LOG_FILE"
    log_success "Virtual environment created"
else
    log_info "Virtual environment already exists"
fi

# Activate virtual environment
log_info "Activating virtual environment..."
source venv/bin/activate

# Upgrade pip
log_info "Upgrading pip..."
pip install --upgrade pip --quiet 2>&1 | tee -a "$LOG_FILE"
log_success "Pip upgraded"

# Install Python dependencies
log_info "Installing Python dependencies (this may take a few minutes)..."
log_step "Running: pip install -r requirements.txt"
pip install -r requirements.txt 2>&1 | tee -a "$LOG_FILE"
log_success "Python dependencies installation completed"

# Verify installation
log_info "Verifying Python dependencies..."
if python3 -c "import torch; import transformers" 2>/dev/null; then
    log_success "Python dependencies verified (torch and transformers imported successfully)"
else
    log_error "Failed to verify Python dependencies"
    exit 1
fi

# Return to project root
cd ..

echo ""
log_step "=========================================="
log_step "STEP 3: Setting up MySQL Database"
log_step "=========================================="
echo ""

# Check if database exists
log_info "Checking for existing database 'budgetbuddy_app'..."
DB_EXISTS=$(mysql -u root -e "SHOW DATABASES LIKE 'budgetbuddy_app';" 2>/dev/null | grep -c "budgetbuddy_app" || echo "0")

if [ "$DB_EXISTS" -eq 0 ]; then
    log_info "Creating database 'budgetbuddy_app'..."
    mysql -u root -e "CREATE DATABASE IF NOT EXISTS budgetbuddy_app;" 2>&1 | tee -a "$LOG_FILE" || {
        log_warning "Could not create database automatically. Please create it manually:"
        log_step "  mysql -u root -p"
        log_step "  CREATE DATABASE budgetbuddy_app;"
    }
    log_success "Database setup complete"
else
    log_success "Database 'budgetbuddy_app' already exists"
fi

echo ""
log_step "=========================================="
log_step "STEP 4: Checking Model Files"
log_step "=========================================="
echo ""

# Check if model files exist
log_info "Checking for model files in mybudget-ai/models/..."
if [ -f "mybudget-ai/models/pytorch_model.bin" ] || [ -f "mybudget-ai/models/pytorch_model.pt" ]; then
    log_success "Model files found"
    TRAIN_MODEL=false
else
    log_warning "Model files not found"
    # Automatically train the model if files are missing (required for Cursor AI automation)
    if [ "${SKIP_TRAINING:-}" = "true" ]; then
        log_warning "SKIP_TRAINING=true: Skipping model training"
        log_warning "To train the model later, run: cd mybudget-ai && source venv/bin/activate && python3 train_distilbert.py"
        TRAIN_MODEL=false
    else
        log_info "Model files missing - will automatically train the model (this may take 10-30 minutes)"
        log_info "To skip training, set SKIP_TRAINING=true before running this script"
        TRAIN_MODEL=true
    fi
fi

if [ "$TRAIN_MODEL" = true ]; then
    echo ""
    log_step "=========================================="
    log_step "STEP 5: Training Model"
    log_step "=========================================="
    echo ""
    cd mybudget-ai
    source venv/bin/activate
    log_info "Starting model training (this will take 10-30 minutes)..."
    log_step "Running: python3 train_distilbert.py"
    python3 train_distilbert.py 2>&1 | tee -a "$LOG_FILE" || {
        log_error "Model training failed"
        cd ..
        exit 1
    }
    log_success "Model training completed"
    cd ..
fi

echo ""
log_step "=========================================="
log_step "STEP 6: Building Spring Boot Application"
log_step "=========================================="
echo ""

# Build the application
log_info "Building Spring Boot application..."
log_step "Running: ./gradlew clean build"
./gradlew clean build --quiet 2>&1 | tee -a "$LOG_FILE" || {
    log_warning "Build failed with quiet mode. Trying with verbose output..."
    ./gradlew clean build 2>&1 | tee -a "$LOG_FILE"
    if [ $? -ne 0 ]; then
        log_error "Build failed"
        exit 1
    fi
}
log_success "Spring Boot application built successfully"

echo ""
log_step "=========================================="
log_step "SETUP COMPLETE!"
log_step "=========================================="
echo ""
log_success "All components are set up and ready to use"
log_step "Log file saved to: $LOG_FILE"
echo ""
log_info "Next Steps:"
log_step "  1. Start the application: ./gradlew bootRun"
log_step "  2. Open browser: http://localhost:8080"
log_step "  3. Test inference: cd mybudget-ai && source venv/bin/activate && python3 inference_local.py \"UPI-TEST-MERCHANT\""
echo ""
log_step "For more information, see README.md"
log_step "=========================================="
log_step "Setup completed at: $(date)"
log_step "=========================================="
echo ""

