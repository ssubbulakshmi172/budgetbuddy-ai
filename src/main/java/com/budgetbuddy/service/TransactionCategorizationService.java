package com.budgetbuddy.service;

import com.budgetbuddy.model.Transaction;
import com.budgetbuddy.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.util.List;

@Service
public class TransactionCategorizationService {

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
            System.out.println("✅ TransactionCategorizationService: LocalModelInferenceService injected successfully");
        } else {
            System.err.println("❌ TransactionCategorizationService: LocalModelInferenceService is NULL!");
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
            System.err.println("⚠️ LocalModelInferenceService is null - cannot predict category");
            return "Uncategorized";
        }
        
        try {
            String category = localModelInferenceService.getPredictedCategory(description);
            System.out.println("✅ Predicted category for '" + description + "': " + category);
            return category;
        } catch (Exception e) {
            System.err.println("⚠️ Local inference failed for '" + description + "': " + e.getMessage());
            e.printStackTrace();
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
            System.err.println("⚠️ LocalModelInferenceService is null - cannot predict categories");
            java.util.List<LocalModelInferenceService.PredictionResult> results = new java.util.ArrayList<>();
            for (int i = 0; i < descriptions.size(); i++) {
                results.add(new LocalModelInferenceService.PredictionResult("Uncategorized", null, null, null, 0.0));
            }
            return results;
        }
        
        try {
            java.util.List<LocalModelInferenceService.PredictionResult> results = 
                localModelInferenceService.predictBatchFull(descriptions);
            System.out.println("✅ Batch predicted " + results.size() + " categories");
            return results;
        } catch (Exception e) {
            System.err.println("⚠️ Batch inference failed: " + e.getMessage());
            e.printStackTrace();
            java.util.List<LocalModelInferenceService.PredictionResult> results = new java.util.ArrayList<>();
            for (int i = 0; i < descriptions.size(); i++) {
                results.add(new LocalModelInferenceService.PredictionResult("Uncategorized", null, null, null, 0.0));
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
            return new LocalModelInferenceService.PredictionResult("Uncategorized", null, null, null, 0.0);
        }
        
        if (localModelInferenceService == null) {
            System.err.println("⚠️ LocalModelInferenceService is null - cannot predict category");
            return new LocalModelInferenceService.PredictionResult("Uncategorized", null, null, null, 0.0);
        }
        
        try {
            LocalModelInferenceService.PredictionResult result = localModelInferenceService.predictFull(description);
            System.out.println("✅ Full prediction for '" + description + "': category=" + result.getPredictedCategory() 
                + ", type=" + result.getTransactionType() + ", intent=" + result.getIntent() + ", confidence=" + result.getConfidence());
            return result;
        } catch (Exception e) {
            System.err.println("⚠️ Local inference failed for '" + description + "': " + e.getMessage());
            e.printStackTrace();
            return new LocalModelInferenceService.PredictionResult("Uncategorized", null, null, null, 0.0);
        }
    }

    /**
     * Re-runs predictions for all transactions with full model predictions.
     * Uses batch processing for MUCH faster execution (loads model once).
     */
    public void refreshPredictionsForAll() {
        List<Transaction> transactions = transactionRepository.findAll();
        int totalTransactions = transactions.size();
        
        if (totalTransactions == 0) {
            System.out.println("⚠️ No transactions found to refresh");
            return;
        }
        
        System.out.println("🔄 Starting batch prediction refresh for " + totalTransactions + " transactions...");
        
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
            System.out.println("⚠️ No transactions with narrations found to refresh");
            return;
        }
        
        System.out.println("📊 Processing " + narrations.size() + " transactions with narrations in batch...");
        long startTime = System.currentTimeMillis();
        
        // Batch predict all narrations at once (MUCH FASTER - model loads once)
        java.util.List<LocalModelInferenceService.PredictionResult> predictions = 
            getBatchFullPredictions(narrations);
        
        // Update transactions with predictions
        for (int i = 0; i < transactionsToUpdate.size(); i++) {
            Transaction txn = transactionsToUpdate.get(i);
            if (i < predictions.size()) {
                LocalModelInferenceService.PredictionResult prediction = predictions.get(i);
                txn.setPredictedCategory(prediction.getPredictedCategory());
                txn.setPredictedSubcategory(prediction.getPredictedSubcategory());
                txn.setPredictedTransactionType(prediction.getTransactionType());
                txn.setPredictedIntent(prediction.getIntent());
                txn.setPredictionConfidence(prediction.getConfidence());
            } else {
                // Fallback if prediction count doesn't match
                txn.setPredictedCategory("Uncategorized");
                txn.setPredictedSubcategory(null);
                txn.setPredictedTransactionType(null);
                txn.setPredictedIntent(null);
                txn.setPredictionConfidence(0.0);
            }
        }
        
        // Save all updated transactions in batch
        transactionRepository.saveAll(transactionsToUpdate);
        
        long elapsedTime = System.currentTimeMillis() - startTime;
        System.out.println("✅ Batch refreshed predictions for " + transactionsToUpdate.size() + 
            " transactions in " + elapsedTime + "ms (" + String.format("%.2f", elapsedTime / 1000.0) + " seconds)");
    }

    /**
     * Re-run prediction for a specific transaction with full model predictions.
     */
    public void refreshPredictionForTransaction(Long transactionId) {
        transactionRepository.findById(transactionId).ifPresent(txn -> {
            if (txn.getNarration() != null && !txn.getNarration().isEmpty()) {
                LocalModelInferenceService.PredictionResult prediction = getFullPrediction(txn.getNarration());
                txn.setPredictedCategory(prediction.getPredictedCategory());
                txn.setPredictedSubcategory(prediction.getPredictedSubcategory());
                txn.setPredictedTransactionType(prediction.getTransactionType());
                txn.setPredictedIntent(prediction.getIntent());
                txn.setPredictionConfidence(prediction.getConfidence());
                transactionRepository.save(txn);
                System.out.println("✅ Updated prediction for Transaction ID: " + transactionId);
            }
        });
    }
}
