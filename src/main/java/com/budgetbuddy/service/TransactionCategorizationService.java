package com.budgetbuddy.service;

import com.budgetbuddy.model.Transaction;
import com.budgetbuddy.repository.TransactionRepository;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
public class TransactionCategorizationService {

    private final RestTemplate restTemplate;
    private final TransactionRepository transactionRepository;

    // ✅ Load from application.properties or application.yml
    @Value("${flask.api.url:http://127.0.0.1:8000/predict}")
    private String FLASK_API_URL;

    public TransactionCategorizationService(RestTemplate restTemplate,
                                            TransactionRepository transactionRepository) {
        this.restTemplate = restTemplate;
        this.transactionRepository = transactionRepository;
    }

    /**
     * Calls Flask model API to predict category for a given transaction description.
     */
    public String getPredictedCategory(String description) {
        try {
            JSONObject request = new JSONObject();
            request.put("description", description);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(request.toString(), headers);
            ResponseEntity<String> response =
                    restTemplate.postForEntity(FLASK_API_URL, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JSONObject jsonResponse = new JSONObject(response.getBody());
                return jsonResponse.optString("predicted_category", "Uncategorized");
            }
        } catch (Exception e) {
            System.err.println("⚠️ Error calling Flask API: " + e.getMessage());
        }
        return "Uncategorized";
    }

    /**
     * Re-runs predictions for all transactions.
     */
    public void refreshPredictionsForAll() {
        List<Transaction> transactions = transactionRepository.findAll();
        for (Transaction txn : transactions) {
            String newPrediction = getPredictedCategory(txn.getNarration());
            txn.setPredictedCategory(newPrediction);
        }
        transactionRepository.saveAll(transactions);
        System.out.println("✅ Re-predicted categories for all transactions.");
    }

    /**
     * Re-run prediction for a specific transaction.
     */
    public void refreshPredictionForTransaction(Long transactionId) {
        transactionRepository.findById(transactionId).ifPresent(txn -> {
            String newPrediction = getPredictedCategory(txn.getNarration());
            txn.setPredictedCategory(newPrediction);
            transactionRepository.save(txn);
            System.out.println("✅ Updated prediction for Transaction ID: " + transactionId);
        });
    }
}
