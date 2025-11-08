#!/usr/bin/env python3
"""
Bias monitoring and drift detection for transaction categorization.

Monitors model performance and detects bias drift over time.

Usage:
    python3 bias_monitoring.py [--baseline-file baseline_metrics.json] [--current-file current_metrics.json]
"""

import json
import sys
import os
import pandas as pd
import logging
from datetime import datetime
from pathlib import Path
import argparse

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

def load_metrics(metrics_file):
    """Load metrics from JSON file"""
    try:
        with open(metrics_file, 'r') as f:
            return json.load(f)
    except Exception as e:
        logger.error(f"Failed to load metrics from {metrics_file}: {e}")
        return None

def calculate_bias_score(metrics):
    """
    Calculate overall bias score from metrics.
    Lower score = less bias (better).
    """
    if 'metrics' not in metrics or 'category' not in metrics['metrics']:
        return None
    
    category_metrics = metrics['metrics']['category']
    
    # Get per-class F1 scores
    if 'classification_report' not in category_metrics:
        return None
    
    report = category_metrics['classification_report']
    f1_scores = []
    
    for cat_name, cat_metrics in report.items():
        if isinstance(cat_metrics, dict) and 'f1-score' in cat_metrics:
            f1_scores.append(cat_metrics['f1-score'])
    
    if not f1_scores:
        return None
    
    # Bias score = standard deviation of F1 scores
    # Higher std dev = more bias (uneven performance)
    import numpy as np
    bias_score = np.std(f1_scores)
    
    return bias_score

def detect_bias_drift(baseline_metrics, current_metrics, threshold=0.1):
    """
    Detect bias drift by comparing current metrics to baseline.
    
    Returns:
        (has_drift, drift_details)
    """
    baseline_bias = calculate_bias_score(baseline_metrics)
    current_bias = calculate_bias_score(current_metrics)
    
    if baseline_bias is None or current_bias is None:
        logger.warning("Could not calculate bias scores")
        return False, {}
    
    drift_amount = current_bias - baseline_bias
    drift_percentage = (drift_amount / baseline_bias) * 100 if baseline_bias > 0 else 0
    
    has_drift = abs(drift_percentage) > (threshold * 100)
    
    drift_details = {
        'baseline_bias_score': baseline_bias,
        'current_bias_score': current_bias,
        'drift_amount': drift_amount,
        'drift_percentage': drift_percentage,
        'has_drift': has_drift
    }
    
    return has_drift, drift_details

def check_performance_degradation(baseline_metrics, current_metrics, threshold=0.05):
    """
    Check if model performance degraded significantly.
    
    Returns:
        (has_degradation, degradation_details)
    """
    baseline_macro_f1 = baseline_metrics.get('metrics', {}).get('category', {}).get('macro_f1', 0)
    current_macro_f1 = current_metrics.get('metrics', {}).get('category', {}).get('macro_f1', 0)
    
    degradation = baseline_macro_f1 - current_macro_f1
    degradation_percentage = (degradation / baseline_macro_f1) * 100 if baseline_macro_f1 > 0 else 0
    
    has_degradation = degradation_percentage > (threshold * 100)
    
    degradation_details = {
        'baseline_macro_f1': baseline_macro_f1,
        'current_macro_f1': current_macro_f1,
        'degradation': degradation,
        'degradation_percentage': degradation_percentage,
        'has_degradation': has_degradation
    }
    
    return has_degradation, degradation_details

def monitor_category_performance(metrics_file):
    """Monitor performance per category"""
    metrics = load_metrics(metrics_file)
    if not metrics:
        return None
    
    category_metrics = metrics.get('metrics', {}).get('category', {})
    if 'classification_report' not in category_metrics:
        return None
    
    report = category_metrics['classification_report']
    
    category_performance = []
    for cat_name, cat_metrics in report.items():
        if isinstance(cat_metrics, dict) and 'f1-score' in cat_metrics:
            category_performance.append({
                'category': cat_name,
                'f1_score': cat_metrics['f1-score'],
                'precision': cat_metrics.get('precision', 0),
                'recall': cat_metrics.get('recall', 0),
                'support': cat_metrics.get('support', 0)
            })
    
    return category_performance

def generate_bias_report(baseline_file, current_file, output_file='bias_report.json'):
    """Generate comprehensive bias monitoring report"""
    logger.info("📊 Generating bias monitoring report...")
    
    baseline_metrics = load_metrics(baseline_file)
    current_metrics = load_metrics(current_file)
    
    if not baseline_metrics or not current_metrics:
        logger.error("❌ Could not load metrics files")
        return None
    
    # Check bias drift
    has_drift, drift_details = detect_bias_drift(baseline_metrics, current_metrics)
    
    # Check performance degradation
    has_degradation, degradation_details = check_performance_degradation(baseline_metrics, current_metrics)
    
    # Monitor category performance
    baseline_performance = monitor_category_performance(baseline_file)
    current_performance = monitor_category_performance(current_file)
    
    report = {
        'timestamp': datetime.now().isoformat(),
        'baseline_file': baseline_file,
        'current_file': current_file,
        'bias_drift': drift_details,
        'performance_degradation': degradation_details,
        'baseline_category_performance': baseline_performance,
        'current_category_performance': current_performance,
        'alerts': []
    }
    
    # Generate alerts
    if has_drift:
        report['alerts'].append({
            'type': 'bias_drift',
            'severity': 'warning',
            'message': f"Bias drift detected: {drift_details['drift_percentage']:.1f}% change"
        })
    
    if has_degradation:
        report['alerts'].append({
            'type': 'performance_degradation',
            'severity': 'critical',
            'message': f"Performance degraded: {degradation_details['degradation_percentage']:.1f}% decrease"
        })
    
    # Save report
    with open(output_file, 'w') as f:
        json.dump(report, f, indent=2)
    
    logger.info(f"✅ Bias report saved to {output_file}")
    
    # Print summary
    logger.info("\n📊 Bias Monitoring Summary:")
    logger.info(f"   Bias Score: {drift_details['current_bias_score']:.4f}")
    logger.info(f"   Drift: {drift_details['drift_percentage']:.1f}%")
    logger.info(f"   Performance: {degradation_details['current_macro_f1']:.4f}")
    logger.info(f"   Degradation: {degradation_details['degradation_percentage']:.1f}%")
    
    if report['alerts']:
        logger.warning("\n⚠️ Alerts:")
        for alert in report['alerts']:
            logger.warning(f"   [{alert['severity'].upper()}] {alert['message']}")
    
    return report

def main():
    parser = argparse.ArgumentParser(description='Monitor bias and detect drift')
    parser.add_argument('--baseline-file', type=str, 
                       default='reports/distilbert_metrics_20251107_205746.json',
                       help='Baseline metrics JSON file')
    parser.add_argument('--current-file', type=str,
                       help='Current metrics JSON file (default: latest in reports/)')
    parser.add_argument('--output', type=str, default='bias_report.json',
                       help='Output report file')
    
    args = parser.parse_args()
    
    # Find latest metrics file if not specified
    if not args.current_file:
        import glob
        metrics_files = glob.glob('reports/distilbert_metrics_*.json')
        if metrics_files:
            args.current_file = max(metrics_files, key=os.path.getctime)
            logger.info(f"Using latest metrics file: {args.current_file}")
        else:
            logger.error("No current metrics file found")
            return 1
    
    report = generate_bias_report(args.baseline_file, args.current_file, args.output)
    
    if report and report['alerts']:
        return 1  # Exit with error if alerts
    
    return 0

if __name__ == '__main__':
    sys.exit(main())

