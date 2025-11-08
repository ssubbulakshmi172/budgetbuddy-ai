"""
Category Mapping for Consolidated Taxonomy

Maps old category names to new consolidated category names.
This ensures all existing transactions are properly mapped to the new taxonomy.
"""

import pandas as pd

# Mapping from old category names to new consolidated categories
CATEGORY_MAPPING = {
    # Group 1: Dining & Food
    "Dining": "Dining & Food / Dining & Food Delivery",
    "Dining & Food Delivery / Dining / Restaurants": "Dining & Food / Dining & Food Delivery",
    "Dining & Food Delivery / Food Delivery / Takeaway": "Dining & Food / Dining & Food Delivery",
    "Dining & Food Delivery / Dining & Food Delivery": "Dining & Food / Dining & Food Delivery",
    
    # Group 2: Groceries
    "Groceries": "Groceries / Groceries & Household Supplies",
    "Household & Pets / Groceries / Household Supplies": "Groceries / Groceries & Household Supplies",
    "Groceries & Household Supplies / Groceries & Household Supplies": "Groceries / Groceries & Household Supplies",
    
    # Group 3: Shopping
    "Shopping": "Shopping / Shopping & Fashion",
    "Personal & Lifestyle / Shopping / Fashion": "Shopping / Shopping & Fashion",
    "Shopping & Fashion / Shopping & Fashion": "Shopping / Shopping & Fashion",
    
    # Group 4: Investments
    "Investments & Finance / Stocks & Bonds": "Investments / Stocks & Bonds",
    "Investments & Finance / Banking & Payments": "Investments / Banking & Payments",
    "Investments & Finance / Investments & Savings": "Investments / Investments & Savings",
    "Investments & Finance / Loans & EMI": "Investments / Loans & EMI",
    "Investments & Finance / SIP / Mutual Funds": "Investments / Investments & Savings",
    "Investments & Finance / FD / Fixed Deposit": "Investments / Investments & Savings",
    "Investments & Finance / Digital / Physical Gold": "Investments / Investments & Savings",
    "Investments & Finance / Personal Loan": "Investments / Loans & EMI",
    "Investments & Finance / Housing Loan / Home Loan": "Investments / Loans & EMI",
    "Investments & Finance / Vehicle / Auto Loan": "Investments / Loans & EMI",
    "Investments & Finance / Credit Card Payment": "Investments / Banking & Payments",
    "Investments & Finance / Bank & Investment Fees": "Investments / Banking & Payments",
    "Investments & Finance / Transfers & Payments": "Investments / Banking & Payments",
    
    # Group 5: Transportation
    "Transport": "Transportation / Transport & Commuting",
    "Transportation & Vehicles / Transport / Commuting": "Transportation / Transport & Commuting",
    "Transportation & Vehicles / Transport & Commuting": "Transportation / Transport & Commuting",
    "Transportation & Vehicles / Fuel / Petrol / Diesel": "Transportation / Transport & Commuting",
    "Transportation & Vehicles / Auto Maintenance & Repair": "Transportation / Vehicle Expenses",
    "Transportation & Vehicles / Auto Accessories & Parking": "Transportation / Vehicle Expenses",
    "Transportation & Vehicles / Parking & Toll": "Transportation / Vehicle Expenses",
    "Transportation & Vehicles / Vehicle Purchase": "Transportation / Vehicle Expenses",
    "Transportation & Vehicles / Vehicle Expenses": "Transportation / Vehicle Expenses",
    
    # Group 6: Personal & Lifestyle
    "Personal & Lifestyle / Fitness / Gym / Yoga": "Personal & Lifestyle / Fitness",
    "Fitness": "Personal & Lifestyle / Fitness",
    "Entertainment": "Personal & Lifestyle / Entertainment & Leisure",
    "Personal & Lifestyle / Entertainment / Leisure": "Personal & Lifestyle / Entertainment & Leisure",
    "Personal & Lifestyle / Personal / Lifestyle": "Personal & Lifestyle / Personal Care & Lifestyle",
    "Personal & Lifestyle / Personal Health & Wellness": "Personal & Lifestyle / Personal Care & Lifestyle",
    "Personal & Lifestyle / Clothing & Footwear": "Personal & Lifestyle / Personal Care & Lifestyle",
    "Personal & Lifestyle / Hobby / Craft Supplies": "Personal & Lifestyle / Personal Care & Lifestyle",
    "Personal & Lifestyle / Subscriptions / Memberships": "Personal & Lifestyle / Subscriptions & Software",
    "Personal & Lifestyle / Software & Cloud Services": "Personal & Lifestyle / Subscriptions & Software",
    
    # Group 7: Education
    "Education & Children / Education / Kids": "Education / Education & Children",
    "Education & Children / Childcare / Kids Activities": "Education / Education & Children",
    "Education & Children / Books & Self-Improvement": "Education / Education & Children",
    "Education & Children / Education & Children": "Education / Education & Children",
    
    # Group 8: Insurance & Taxes
    "Insurance & Taxes / Insurance": "Insurance & Taxes / Insurance",
    "Insurance & Taxes / Taxes / Government": "Insurance & Taxes / Taxes & Legal",
    "Insurance & Taxes / Legal & Administrative": "Insurance & Taxes / Taxes & Legal",
    "Education & Children / Child Support / Alimony": "Insurance & Taxes / Taxes & Legal",
    
    # Group 9: Real Estate
    "Utilities": "Housing / Utilities & Household Bills",
    "Real Estate & Housing / Utilities / Household Bills": "Housing / Utilities & Household Bills",
    "Real Estate & Housing / Real Estate / Housing": "Housing / Real Estate & Housing",
    "Real Estate & Housing / Property Taxes & Fees": "Housing / Real Estate & Housing",
    "Real Estate & Housing / Home Maintenance / Repairs": "Housing / Real Estate & Housing",
    "Real Estate & Housing / Real Estate & Housing": "Housing / Real Estate & Housing",
    "Real Estate & Housing / Utilities & Household Bills": "Housing / Utilities & Household Bills",
    "Household & Miscellaneous / Household & Miscellaneous": "Housing / Household & Miscellaneous",
    
    # Group 10: Travel
    "Travel": "Travel & Vacation / Travel & Vacation",
    "Travel & Vacation / Flights / Air Travel": "Travel & Vacation / Travel & Vacation",
    "Travel & Vacation / Hotels / Accommodation": "Travel & Vacation / Travel & Vacation",
    "Travel & Vacation / Travel Expenses (General)": "Travel & Vacation / Travel & Vacation",
    
    # Group 11: Household
    "Household & Pets / Home Appliances / Electronics": "Household & Miscellaneous / Household & Miscellaneous",
    "Household & Pets / Pets": "Household & Miscellaneous / Household & Miscellaneous",
    "Household & Pets / Emergency / Miscellaneous": "Household & Miscellaneous / Household & Miscellaneous",
    
    # Group 12: Gifts & Social
    "Charity": "Gifts & Social / Charity & Donations",
    "Gifts & Social / Charity / Donations": "Gifts & Social / Charity & Donations",
    "Gifts & Social / Gifts & Occasions": "Gifts & Social / Gifts & Occasions",
    "Gifts & Social / Gifts Received (Income)": "Gifts & Social / Gifts & Occasions",
    "Gifts & Social / Business Expenses": "Gifts & Social / Business & Professional Services",
    "Gifts & Social / Professional Services": "Gifts & Social / Business & Professional Services",
    
    # Group 13: Cash Management
    "Cash Management / ATM / Cash Withdrawal": "Cash Management / Cash Management & Transfers",
    "Cash Management / Cash Deposit": "Cash Management / Cash Management & Transfers",
    "Cash Management / Transfers": "Cash Management / Cash Management & Transfers",
    
    # Group 14: Friends
    "Friends & Shared Expenses / Money Lent / Borrowed": "Friends & Shared Expenses / Friends & Social Expenses",
    "Friends & Shared Expenses / Social / Outings": "Friends & Shared Expenses / Friends & Social Expenses",
    
    # Group 15: Healthcare
    "Healthcare": "Healthcare / Healthcare",
}

def map_category(old_category: str) -> str:
    """
    Map old category name to new consolidated category name.
    
    Args:
        old_category: Old category name from dataset
        
    Returns:
        New consolidated category name, or original if no mapping exists
    """
    if not old_category or pd.isna(old_category):
        return "Other"
    
    old_category = str(old_category).strip()
    
    # Direct mapping
    if old_category in CATEGORY_MAPPING:
        return CATEGORY_MAPPING[old_category]
    
    # Return original if no mapping (will be handled by taxonomy filter)
    return old_category

def apply_category_mapping(df):
    """
    Apply category mapping to a DataFrame.
    
    Args:
        df: DataFrame with 'category' column
        
    Returns:
        DataFrame with mapped categories
    """
    if 'category' not in df.columns:
        return df
    
    df = df.copy()
    df['category'] = df['category'].apply(map_category)
    
    return df

def update_datasets():
    """
    Update all transaction CSV files with mapped categories.
    Creates backup files and updates in-place.
    """
    import os
    import glob
    
    current_dir = os.path.dirname(os.path.abspath(__file__)) or '.'
    csv_files = glob.glob(os.path.join(current_dir, 'transactions_*.csv'))
    
    print(f"🔄 Updating {len(csv_files)} dataset files with category mapping...")
    print("")
    
    total_mapped = 0
    
    for csv_file in csv_files:
        filename = os.path.basename(csv_file)
        
        # Skip backup files
        if 'backup' in filename:
            print(f"⏭️  Skipping {filename} (backup file)")
            continue
        
        try:
            # Read dataset
            df = pd.read_csv(csv_file)
            
            if 'category' not in df.columns:
                print(f"⚠️  {filename}: No 'category' column, skipping")
                continue
            
            # Create backup
            backup_file = csv_file.replace('.csv', '_backup.csv')
            df.to_csv(backup_file, index=False)
            
            # Apply mapping
            before_cats = set(df['category'].unique())
            df = apply_category_mapping(df)
            after_cats = set(df['category'].unique())
            
            # Save updated file
            df.to_csv(csv_file, index=False)
            
            mapped_count = len([c for c in before_cats if c in CATEGORY_MAPPING])
            total_mapped += mapped_count
            
            print(f"✅ {filename:40s} {len(df):5d} rows, {mapped_count:3d} categories mapped")
            print(f"   Backup saved: {os.path.basename(backup_file)}")
            
        except Exception as e:
            print(f"❌ {filename}: Error - {e}")
    
    print(f"\n✅ Updated {len(csv_files)} files, {total_mapped} categories mapped")
    print("💡 Backup files created with '_backup.csv' suffix")

if __name__ == "__main__":
    update_datasets()

