#!/usr/bin/env python3
"""
Generate keywords.json from categories.yml for Android app

This script reads categories.yml and generates a JSON file that can be
loaded into the Android app's assets and used to populate the database.
"""
import yaml
import json
import sys
from pathlib import Path

def generate_keywords_json(yaml_path: str, output_path: str):
    """Generate keywords.json from categories.yml"""
    
    with open(yaml_path, 'r', encoding='utf-8') as f:
        config = yaml.safe_load(f)
    
    categories = config.get('categories', [])
    keywords_list = []
    
    for category in categories:
        top_category_name = category.get('name', '')
        if not top_category_name:
            continue
        
        # Check for subcategories
        subcategories = category.get('subcategories', [])
        
        if subcategories:
            # Process subcategories
            for subcat in subcategories:
                subcat_name = subcat.get('name', '')
                if not subcat_name:
                    continue
                
                full_category_name = f"{top_category_name} / {subcat_name}"
                keywords = subcat.get('keywords', [])
                
                for keyword in keywords:
                    if keyword and isinstance(keyword, str) and keyword.strip():
                        keywords_list.append({
                            "categoryName": full_category_name,
                            "keyword": keyword.strip(),
                            "categoriesFor": "Taxonomy"
                        })
        else:
            # Top-level category without subcategories
            keywords = category.get('keywords', [])
            for keyword in keywords:
                if keyword and isinstance(keyword, str) and keyword.strip():
                    keywords_list.append({
                        "categoryName": top_category_name,
                        "keyword": keyword.strip(),
                        "categoriesFor": "Taxonomy"
                    })
    
    # Write JSON file
    with open(output_path, 'w', encoding='utf-8') as f:
        json.dump(keywords_list, f, indent=2, ensure_ascii=False)
    
    print(f"✅ Generated {len(keywords_list)} keywords")
    print(f"📄 Output: {output_path}")
    return len(keywords_list)

if __name__ == "__main__":
    script_dir = Path(__file__).parent.parent.absolute()
    project_root = script_dir.parent
    yaml_path = project_root / "mybudget-ai" / "categories.yml"
    output_path = script_dir / "app" / "src" / "main" / "assets" / "keywords.json"
    
    if not yaml_path.exists():
        print(f"❌ categories.yml not found: {yaml_path}")
        sys.exit(1)
    
    try:
        count = generate_keywords_json(str(yaml_path), str(output_path))
        print(f"✅ Successfully generated keywords.json with {count} keywords")
        sys.exit(0)
    except Exception as e:
        print(f"❌ Error: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)

