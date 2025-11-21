package com.budgetbuddy.service;

import com.budgetbuddy.model.Transaction;
import com.budgetbuddy.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.util.List;

@Service
public class TransactionCategorizationService {

    private static final Logger logger = LoggerFactory.getLogger(TransactionCategorizationService.class);
    
    private final TransactionRepository transactionRepository;
    
    // Local inference service
    @Autowired
    private LocalModelInferenceService localModelInferenceService;

    public TransactionCategorizationService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }
    
    /**
     * Verify service injection on startup
     */
    @PostConstruct
    public void init() {
        if (localModelInferenceService != null) {
            logger.info("✅ TransactionCategorizationService: LocalModelInferenceService injected successfully");
        } else {
            logger.error("❌ TransactionCategorizationService: LocalModelInferenceService is NULL!");
        }
    }

    /**
     * Predict category for a given transaction description.
     * Uses local inference (Python script).
     */
    public String getPredictedCategory(String description) {
        if (description == null || description.trim().isEmpty()) {
            return "Uncategorized";
        }
        
        if (localModelInferenceService == null) {
            logger.warn("⚠️ LocalModelInferenceService is null - cannot predict category");
            return "Uncategorized";
        }
        
        try {
            String category = localModelInferenceService.getPredictedCategory(description);
            return category;
        } catch (Exception e) {
            logger.error("⚠️ Local inference failed: {}", e.getMessage(), e);
            return "Uncategorized";
        }
    }

    /**
     * Batch get full predictions for multiple descriptions (MUCH FASTER!)
     * Uses local inference with batch processing.
     * 
     * @param descriptions List of transaction narrations/descriptions
     * @return List of prediction results (same order as input)
     */
    public java.util.List<LocalModelInferenceService.PredictionResult> getBatchFullPredictions(java.util.List<String> descriptions) {
        if (descriptions == null || descriptions.isEmpty()) {
            return new java.util.ArrayList<>();
        }
        
        if (localModelInferenceService == null) {
            logger.warn("⚠️ LocalModelInferenceService is null - cannot predict categories");
            java.util.List<LocalModelInferenceService.PredictionResult> results = new java.util.ArrayList<>();
            for (int i = 0; i < descriptions.size(); i++) {
                results.add(new LocalModelInferenceService.PredictionResult("Uncategorized", null, null, null, 0.0, "error", false));
            }
            return results;
        }
        
        try {
            logger.info("🔄 Starting batch prediction for {} descriptions...", descriptions.size());
            java.util.List<LocalModelInferenceService.PredictionResult> results = 
                localModelInferenceService.predictBatchFull(descriptions);
            logger.info("✅ Batch predicted {} categories", results.size());
            return results;
        } catch (Exception e) {
            logger.error("⚠️ Batch inference failed: {}", e.getMessage(), e);
            java.util.List<LocalModelInferenceService.PredictionResult> results = new java.util.ArrayList<>();
            for (int i = 0; i < descriptions.size(); i++) {
                results.add(new LocalModelInferenceService.PredictionResult("Uncategorized", null, null, null, 0.0, "error", false));
            }
            return results;
        }
    }
    
    /**
     * Get full prediction result (category, transaction_type, intent, confidence).
     * Uses local inference.
     */
    public LocalModelInferenceService.PredictionResult getFullPrediction(String description) {
        if (description == null || description.trim().isEmpty()) {
            return new LocalModelInferenceService.PredictionResult("Uncategorized", null, null, null, 0.0, "empty_input", false);
        }
        
        if (localModelInferenceService == null) {
            logger.warn("⚠️ LocalModelInferenceService is null - cannot predict category");
            return new LocalModelInferenceService.PredictionResult("Uncategorized", null, null, null, 0.0, "error", false);
        }
        
        try {
            LocalModelInferenceService.PredictionResult result = localModelInferenceService.predictFull(description);
            return result;
        } catch (Exception e) {
            logger.error("⚠️ Local inference failed: {}", e.getMessage(), e);
            return new LocalModelInferenceService.PredictionResult("Uncategorized", null, null, null, 0.0, "error", false);
        }
    }

    /**
     * Re-runs predictions for all transactions with full model predictions.
     * Uses batch processing for MUCH faster execution (loads model once).
     */
    public void refreshPredictionsForAll() {
        try {
            logger.info("🔄 Starting refresh predictions for all transactions...");
            List<Transaction> transactions = transactionRepository.findAll();
            int totalTransactions = transactions.size();
            
            if (totalTransactions == 0) {
                logger.warn("⚠️ No transactions found to refresh");
                return;
            }
            
            logger.info("🔄 Starting batch prediction refresh for {} transactions...", totalTransactions);
            
            // Collect all narrations that need prediction
            java.util.List<String> narrations = new java.util.ArrayList<>();
            java.util.List<Transaction> transactionsToUpdate = new java.util.ArrayList<>();
            
            for (Transaction txn : transactions) {
                if (txn.getNarration() != null && !txn.getNarration().trim().isEmpty()) {
                    narrations.add(txn.getNarration());
                    transactionsToUpdate.add(txn);
                }
            }
            
            if (narrations.isEmpty()) {
                logger.warn("⚠️ No transactions with narration fields found to refresh");
                return;
            }
            
            logger.info("📊 Processing {} transactions in batch...", narrations.size());
            long startTime = System.currentTimeMillis();
            
            // Batch predict all narrations at once (MUCH FASTER - model loads once)
            java.util.List<LocalModelInferenceService.PredictionResult> predictions = 
                getBatchFullPredictions(narrations);
            
            if (predictions == null || predictions.isEmpty()) {
                logger.error("❌ Batch prediction returned empty results");
                return;
            }
            
            if (predictions.size() != narrations.size()) {
                logger.warn("⚠️ Prediction count mismatch: expected {}, got {}", narrations.size(), predictions.size());
            }
            
            // Update transactions with predictions (batch update - O(n) complexity)
            logger.info("📝 Updating {} transactions with predictions...", transactionsToUpdate.size());
            long updateStartTime = System.currentTimeMillis();
            
            // Batch update: iterate once and update all fields (much faster than parallel with indexOf)
            for (int i = 0; i < transactionsToUpdate.size(); i++) {
                Transaction txn = transactionsToUpdate.get(i);
                if (i < predictions.size()) {
                    LocalModelInferenceService.PredictionResult prediction = predictions.get(i);
                    String predictedCategory = prediction.getPredictedCategory();
                    
                    // Update predicted fields
                    txn.setPredictedCategory(predictedCategory);
                    txn.setPredictedSubcategory(prediction.getPredictedSubcategory());
                    txn.setPredictedTransactionType(prediction.getTransactionType());
                    txn.setPredictedIntent(prediction.getIntent());
                    txn.setPredictionConfidence(prediction.getConfidence());
                    String reason = prediction.getReason();
                    txn.setPredictionReason(reason);
                    
                    // Log if correction was applied
                    if ("user_correction".equals(reason)) {
                        logger.debug("✅ Applied correction for transaction {}: {}", txn.getId(), predictedCategory);
                    }
                    
                    // If prediction reason is "user_correction", clear categoryName (we hide it in UI)
                    // Otherwise, keep categoryName as null (UI will show predictedCategory)
                    if ("user_correction".equals(prediction.getReason())) {
                        // Correction applied - clear categoryName so UI shows predictedCategory
                        txn.setCategoryName(null);
                    } else {
                        // Regular prediction - also clear categoryName to show predictedCategory
                        txn.setCategoryName(null);
                    }
                } else {
                    // Fallback if prediction count doesn't match
                    logger.warn("⚠️ No prediction for transaction at index {}, setting to Uncategorized", i);
                    txn.setPredictedCategory("Uncategorized");
                    txn.setPredictedSubcategory(null);
                    txn.setPredictedTransactionType(null);
                    txn.setPredictedIntent(null);
                    txn.setPredictionConfidence(0.0);
                    txn.setPredictionReason("error");
                    txn.setCategoryName(null);
                }
            }
            
            long updateTime = System.currentTimeMillis() - updateStartTime;
            logger.info("✅ Field updates completed in {}ms", updateTime);
            
            // Save all updated transactions in batch (JPA will batch the SQL statements)
            logger.info("💾 Saving {} transactions to database (batched)...", transactionsToUpdate.size());
            long saveStartTime = System.currentTimeMillis();
            transactionRepository.saveAll(transactionsToUpdate);
            long saveTime = System.currentTimeMillis() - saveStartTime;
            logger.info("✅ Batch save completed in {}ms", saveTime);
            
            long elapsedTime = System.currentTimeMillis() - startTime;
            logger.info("✅ Batch refreshed predictions for {} transactions in {}ms ({:.2f} seconds)", 
                transactionsToUpdate.size(), elapsedTime, elapsedTime / 1000.0);
        } catch (Exception e) {
            logger.error("❌ Error in refreshPredictionsForAll: {}", e.getMessage(), e);
            throw e; // Re-throw to be caught by controller
        }
    }

    /**
     * Re-run prediction for a specific transaction with full model predictions.
     */
    public void refreshPredictionForTransaction(Long transactionId) {
        transactionRepository.findById(transactionId).ifPresent(txn -> {
            if (txn.getNarration() != null && !txn.getNarration().isEmpty()) {
                try {
                    logger.info("🔄 Refreshing prediction for transaction ID: {}", transactionId);
                    LocalModelInferenceService.PredictionResult prediction = getFullPrediction(txn.getNarration());
                    txn.setPredictedCategory(prediction.getPredictedCategory());
                    txn.setPredictedSubcategory(prediction.getPredictedSubcategory());
                    txn.setPredictedTransactionType(prediction.getTransactionType());
                    txn.setPredictedIntent(prediction.getIntent());
                    txn.setPredictionConfidence(prediction.getConfidence());
                    txn.setPredictionReason(prediction.getReason());
                    transactionRepository.save(txn);
                    logger.info("✅ Updated prediction for Transaction ID: {}", transactionId);
                } catch (Exception e) {
                    logger.error("❌ Error refreshing prediction for transaction ID {}: {}", transactionId, e.getMessage(), e);
                    throw e;
                }
            } else {
                logger.warn("⚠️ Transaction ID {} has no narration to refresh", transactionId);
            }
        });
    }
}
