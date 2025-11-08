#!/usr/bin/env python3
"""
Generate confusion matrix visualizations from evaluation metrics JSON files.
Creates heatmaps for category, transaction_type, and intent tasks.
"""

import json
import os
import sys
import numpy as np
import matplotlib.pyplot as plt
import seaborn as sns
from pathlib import Path
from typing import Dict, List, Optional

# Set style for better-looking plots
sns.set_style("whitegrid")
plt.rcParams['figure.figsize'] = (16, 12)
plt.rcParams['font.size'] = 8

def load_metrics(json_file: str) -> Dict:
    """Load metrics from JSON file"""
    with open(json_file, 'r') as f:
        return json.load(f)

def plot_confusion_matrix(cm: np.ndarray, labels: List[str], task_name: str, 
                          output_dir: str, model_name: str):
    """Plot confusion matrix as heatmap"""
    
    # Truncate long category names for display
    display_labels = [label.split('/')[-1][:30] if '/' in label else label[:30] 
                     for label in labels]
    
    # Create figure
    fig, ax = plt.subplots(figsize=(max(12, len(labels) * 0.5), max(10, len(labels) * 0.5)))
    
    # Normalize confusion matrix for better visualization
    cm_normalized = cm.astype('float') / (cm.sum(axis=1)[:, np.newaxis] + 1e-8)
    
    # Create heatmap
    sns.heatmap(cm_normalized, annot=True, fmt='.2f', cmap='Blues', 
                xticklabels=display_labels, yticklabels=display_labels,
                cbar_kws={'label': 'Normalized Frequency'}, ax=ax,
                linewidths=0.5, linecolor='gray')
    
    ax.set_title(f'Confusion Matrix - {task_name}\n{model_name}', 
                 fontsize=14, fontweight='bold', pad=20)
    ax.set_xlabel('Predicted Category', fontsize=12, fontweight='bold')
    ax.set_ylabel('True Category', fontsize=12, fontweight='bold')
    
    # Rotate labels for better readability
    plt.xticks(rotation=45, ha='right')
    plt.yticks(rotation=0)
    
    plt.tight_layout()
    
    # Save figure
    output_file = os.path.join(output_dir, f'confusion_matrix_{task_name.lower()}.png')
    plt.savefig(output_file, dpi=300, bbox_inches='tight')
    print(f"✅ Saved: {output_file}")
    
    plt.close()
    
    # Also create raw count version
    fig, ax = plt.subplots(figsize=(max(12, len(labels) * 0.5), max(10, len(labels) * 0.5)))
    
    sns.heatmap(cm, annot=True, fmt='d', cmap='Blues',
                xticklabels=display_labels, yticklabels=display_labels,
                cbar_kws={'label': 'Count'}, ax=ax,
                linewidths=0.5, linecolor='gray')
    
    ax.set_title(f'Confusion Matrix (Raw Counts) - {task_name}\n{model_name}', 
                 fontsize=14, fontweight='bold', pad=20)
    ax.set_xlabel('Predicted Category', fontsize=12, fontweight='bold')
    ax.set_ylabel('True Category', fontsize=12, fontweight='bold')
    
    plt.xticks(rotation=45, ha='right')
    plt.yticks(rotation=0)
    
    plt.tight_layout()
    
    output_file_raw = os.path.join(output_dir, f'confusion_matrix_{task_name.lower()}_raw.png')
    plt.savefig(output_file_raw, dpi=300, bbox_inches='tight')
    print(f"✅ Saved: {output_file_raw}")
    
    plt.close()

def generate_summary_report(metrics: Dict, output_dir: str, model_name: str):
    """Generate a text summary report"""
    report_lines = [
        f"# Evaluation Summary - {model_name}",
        "",
        f"**Model**: {model_name}",
        f"**Timestamp**: {metrics.get('timestamp', 'N/A')}",
        "",
        "## Performance Metrics",
        ""
    ]
    
    for task_name, task_metrics in metrics.get('metrics', {}).items():
        report_lines.extend([
            f"### {task_name.capitalize()}",
            "",
            f"- **Macro F1**: {task_metrics.get('macro_f1', 0):.4f}",
            f"- **Weighted F1**: {task_metrics.get('weighted_f1', 0):.4f}",
            "",
            "#### Top Performing Categories:",
            ""
        ])
        
        # Get top categories by F1 score
        if 'classification_report' in task_metrics:
            report = task_metrics['classification_report']
            category_scores = []
            
            for cat_name, cat_metrics in report.items():
                if isinstance(cat_metrics, dict) and 'f1-score' in cat_metrics:
                    category_scores.append((cat_name, cat_metrics['f1-score']))
            
            # Sort by F1 score
            category_scores.sort(key=lambda x: x[1], reverse=True)
            
            # Show top 10
            for cat_name, f1_score in category_scores[:10]:
                report_lines.append(f"- **{cat_name}**: {f1_score:.4f}")
        
        report_lines.extend(["", "---", ""])
    
    # Write report
    report_file = os.path.join(output_dir, 'evaluation_summary.md')
    with open(report_file, 'w') as f:
        f.write('\n'.join(report_lines))
    
    print(f"✅ Saved: {report_file}")

def main():
    """Main function"""
    if len(sys.argv) < 2:
        print("Usage: python3 generate_confusion_matrix.py <metrics_json_file>")
        print("\nExample:")
        print("  python3 generate_confusion_matrix.py reports/distilbert_metrics_20251107_205746.json")
        sys.exit(1)
    
    json_file = sys.argv[1]
    
    if not os.path.exists(json_file):
        print(f"❌ Error: File not found: {json_file}")
        sys.exit(1)
    
    # Load metrics
    print(f"📊 Loading metrics from: {json_file}")
    metrics = load_metrics(json_file)
    
    # Extract model name from filename
    model_name = Path(json_file).stem.replace('distilbert_metrics_', '')
    
    # Create output directory
    output_dir = os.path.join(os.path.dirname(json_file), 'visualizations', model_name)
    os.makedirs(output_dir, exist_ok=True)
    
    print(f"📁 Output directory: {output_dir}")
    
    # Generate visualizations for each task
    tasks = metrics.get('tasks', {})
    metrics_data = metrics.get('metrics', {})
    
    for task_name in tasks.keys():
        if task_name in metrics_data:
            task_metrics = metrics_data[task_name]
            labels = tasks[task_name].get('labels', [])
            cm = np.array(task_metrics.get('confusion_matrix', []))
            
            if cm.size > 0 and len(labels) > 0:
                print(f"\n📈 Generating confusion matrix for: {task_name}")
                print(f"   Categories: {len(labels)}")
                print(f"   Matrix size: {cm.shape}")
                
                # For category task with many labels, might need to skip or create summary
                if len(labels) > 30:
                    print(f"   ⚠️  Warning: {len(labels)} categories - visualization may be large")
                    print(f"   💡 Consider creating a summary version for top categories")
                
                plot_confusion_matrix(cm, labels, task_name, output_dir, model_name)
            else:
                print(f"⚠️  Skipping {task_name}: No confusion matrix data")
    
    # Generate summary report
    print(f"\n📝 Generating summary report...")
    generate_summary_report(metrics, output_dir, model_name)
    
    print(f"\n✅ All visualizations generated in: {output_dir}")

if __name__ == '__main__':
    main()

