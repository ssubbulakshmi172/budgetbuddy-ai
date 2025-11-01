# Requirements Gap Analysis

## Comparison with Theme Statement Requirements

### ✅ **COMPLETED REQUIREMENTS**

#### 1. End-to-End Autonomous Categorisation
- ✅ Ingest raw transaction data (Excel/CSV upload implemented)
- ✅ Output category and confidence score (DistilBERT multi-task model)
- ✅ All inference in-house (local inference, no external APIs)
- ✅ Batch predictions for performance

#### 2. Accuracy & Evaluation
- ⚠️ **Macro F1-score: 0.8859** (Target: ≥0.90) - **97% of target, but not quite there**
- ✅ Evaluation reports exist (`mybudget-ai/reports/distilbert_metrics_*.json`)
- ✅ Confusion matrices included in JSON reports
- ✅ Per-class F1 scores available
- ✅ Reproducibility (training scripts, dataset documented)

#### 3. Customisable Taxonomy
- ✅ Categories managed via database (UI-based editing)
- ✅ YAML fallback (`categories.yml` - though file may not exist yet)
- ✅ Admin-driven changes without code edits (UI interface)

#### 4. Batch Inference Performance
- ✅ Batch predictions implemented
- ⚠️ **Missing: Performance metrics documentation** (throughput, latency benchmarks)

---

## ❌ **MISSING REQUIREMENTS**

### **CRITICAL GAPS (Must Address)**

#### 1. **Accuracy Target Not Met** ⚠️
- **Current**: Macro F1 = 0.8859
- **Required**: Macro F1 ≥ 0.90
- **Gap**: 0.0141 (1.41 percentage points)
- **Action**: Need to improve model to reach ≥0.90 target
  - Consider hyperparameter tuning
  - Data augmentation
  - Model ensemble
  - Additional training data

#### 2. **Missing Evaluation Report Format** 📊
- ✅ Metrics exist in JSON format
- ❌ **Missing**: Standalone formatted metrics report document
- ❌ **Missing**: Visual confusion matrix images (PNG files) - mentioned in README but not found
- **Action**: 
  - Generate confusion matrix visualizations
  - Create a comprehensive metrics report document (PDF/Markdown)
  - Include macro F1, weighted F1, per-class metrics, confusion matrices

#### 3. **Explainability UI Missing** 🎯
- ✅ Confidence scores available in backend
- ✅ Probability distributions available
- ❌ **Missing**: UI component showing explainability features
- ❌ **Missing**: Feature attributions in user interface
- ❌ **Missing**: "Top keywords per class" visualization in UI
- **Current**: Explainability exists in backend but not exposed to users
- **Action**: 
  - Add explainability section to transaction detail view
  - Show confidence scores visually
  - Display probability distribution across categories
  - Show top contributing features/keywords (if available from model)

#### 4. **Feedback Loop Not Fully Implemented** 🔄
- ✅ Users can edit categories manually (via UI)
- ❌ **Missing**: Explicit feedback mechanism for low-confidence predictions
- ❌ **Missing**: Automated feedback collection workflow
- ❌ **Missing**: CSV-based feedback storage system
- ❌ **Missing**: Automatic retraining integration from feedback
- **Current**: Manual category editing exists, but not structured as a feedback loop
- **Action**:
  - Implement feedback collection UI for low-confidence predictions
  - Create feedback storage system (CSV/database)
  - Build retraining pipeline that incorporates feedback

#### 5. **Demo Missing Key Elements** 🎥
- ✅ Demo recording scripts exist (`demo_ui_recording.sh`, `record_with_ffmpeg.sh`)
- ❌ **Missing**: Demo of taxonomy modification via config file
- ❌ **Missing**: Documented demo script showing config changes
- **Action**:
  - Create demo showing YAML taxonomy modification
  - Record/template demo showing category changes via config
  - Update demo script to include taxonomy modification

---

### **IMPORTANT GAPS (Should Address)**

#### 6. **Robustness Testing Not Documented** 🛡️
- ✅ System handles empty/null transactions
- ❌ **Missing**: Explicit robustness testing documentation
- ❌ **Missing**: Test cases for noisy/variable transaction strings
- ❌ **Missing**: Examples of edge cases handled
- **Action**:
  - Document robustness features
  - Add test cases for edge cases
  - Include examples of noisy input handling

#### 7. **Responsible AI / Bias Mitigation Missing** ⚖️
- ❌ **Missing**: Discussion of ethical AI considerations
- ❌ **Missing**: Bias mitigation analysis
- ❌ **Missing**: Documentation on potential biases (merchant, region, amount-based)
- ❌ **Missing**: Mitigation strategies
- **Action**:
  - Create a "Responsible AI" section in documentation
  - Analyze potential biases in the dataset/model
  - Document mitigation strategies
  - Include fairness metrics in evaluation

#### 8. **Performance Benchmarks Missing** ⚡
- ✅ Batch predictions implemented
- ❌ **Missing**: Throughput metrics (transactions/second)
- ❌ **Missing**: Latency benchmarks (inference time per transaction/batch)
- ❌ **Missing**: Performance measurement notes
- **Action**:
  - Add performance benchmarking script
  - Document throughput and latency
  - Include batch vs. individual prediction comparisons

#### 9. **Categories.yml File May Be Missing** 📝
- ✅ Code references `categories.yml`
- ✅ Database-based taxonomy exists
- ⚠️ **Unclear**: If `categories.yml` file actually exists
- **Action**:
  - Verify if `categories.yml` exists
  - Create example `categories.yml` if missing
  - Document how to use YAML vs. database taxonomy

#### 10. **Confusion Matrix Visualizations Missing** 📈
- ✅ Confusion matrices in JSON format
- ❌ **Missing**: PNG image files of confusion matrices
- **Action**:
  - Generate confusion matrix visualizations (already in training code, verify output)
  - Ensure images are saved to reports directory
  - Reference images in documentation

---

### **DOCUMENTATION GAPS**

#### 11. **Missing Requirements Compliance Document** 📋
- ✅ README exists and is comprehensive
- ❌ **Missing**: Explicit requirements compliance document
- ❌ **Missing**: Mapping of implementation to theme statement requirements
- **Note**: README mentions `REQUIREMENTS_COMPLIANCE.md` but file doesn't exist

#### 12. **Demo Documentation Incomplete** 📚
- ✅ Demo recording guide exists
- ❌ **Missing**: Step-by-step demo script showing all required elements:
  - Pipeline execution
  - Evaluation demonstration
  - Sample predictions with confidence
  - Taxonomy modification demo
- **Action**: Create comprehensive demo script/documentation

---

## 📊 **PRIORITY RECOMMENDATIONS**

### **High Priority (Must Fix Before Submission)**
1. **Reach ≥0.90 Macro F1-score** - Critical requirement
2. **Create formatted metrics report** - Required deliverable
3. **Add explainability UI** - Bonus objective, but important
4. **Complete feedback loop implementation** - Bonus objective
5. **Create demo with all required elements** - Required deliverable

### **Medium Priority (Strongly Recommended)**
6. Document robustness features
7. Add Responsible AI/bias mitigation discussion
8. Create performance benchmarks
9. Verify/generate confusion matrix visualizations
10. Ensure categories.yml exists and is documented

### **Low Priority (Nice to Have)**
11. Create requirements compliance mapping document
12. Enhance demo documentation

---

## 🔧 **QUICK WINS**

1. **Confusion Matrix Images**: Training code likely generates these - verify and document location
2. **Performance Metrics**: Add simple benchmarking script to measure inference time
3. **Explainability UI**: Add basic confidence/probability display to transaction detail page
4. **Feedback Collection**: Add a simple "Was this correct?" button for low-confidence predictions
5. **Bias Discussion**: Write a 1-2 page document discussing potential biases and mitigation

---

## 📝 **SUMMARY**

**Completion Status: ~75-80%**

- **Core Requirements**: Mostly complete (accuracy slightly below target)
- **Bonus Objectives**: Partially complete
- **Documentation**: Good but missing some specific deliverables

**Estimated Work Needed**:
- **Accuracy improvement**: 2-4 hours (hyperparameter tuning, additional training)
- **UI Explainability**: 2-3 hours (add components to existing templates)
- **Feedback Loop**: 3-4 hours (backend + UI)
- **Documentation**: 2-3 hours (reports, bias discussion, benchmarks)
- **Total**: ~9-14 hours of focused work

---

## 🎯 **ACTION CHECKLIST**

- [ ] Improve model to reach ≥0.90 macro F1
- [ ] Generate confusion matrix PNG visualizations
- [ ] Create comprehensive metrics report document
- [ ] Add explainability UI components
- [ ] Implement complete feedback loop
- [ ] Create performance benchmarks
- [ ] Write Responsible AI/bias mitigation document
- [ ] Verify/create categories.yml file
- [ ] Update demo script with all required elements
- [ ] Document robustness testing
- [ ] Create requirements compliance mapping document

