package com.budgetbuddy.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for LocalModelInferenceService
 * 
 * Tests that the service can successfully call the Python inference script
 * and parse the JSON response correctly.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "python.command=python3",
    "python.inference.script=mybudget-ai/inference_local.py",
    "python.inference.timeout=30"
})
public class LocalModelInferenceServiceTest {

    @Autowired
    private LocalModelInferenceService localModelInferenceService;

    @BeforeEach
    public void setUp() {
        assertNotNull(localModelInferenceService, "LocalModelInferenceService should be autowired");
    }

    @Test
    public void testServiceInjection() {
        assertNotNull(localModelInferenceService, "LocalModelInferenceService should not be null");
        System.out.println("✅ LocalModelInferenceService injected successfully");
    }

    @Test
    public void testGetPredictedCategory_WithValidNarration() {
        String narration = "ZOMATO";
        String category = localModelInferenceService.getPredictedCategory(narration);
        
        assertNotNull(category, "Predicted category should not be null");
        assertNotEquals("", category, "Predicted category should not be empty");
        assertNotEquals("Uncategorized", category, "Should get a valid category prediction");
        
        System.out.println("✅ Predicted category for '" + narration + "': " + category);
    }

    @Test
    public void testGetPredictedCategory_WithEmptyNarration() {
        String category = localModelInferenceService.getPredictedCategory("");
        assertEquals("Uncategorized", category, "Empty narration should return 'Uncategorized'");
        
        category = localModelInferenceService.getPredictedCategory(null);
        assertEquals("Uncategorized", category, "Null narration should return 'Uncategorized'");
        
        System.out.println("✅ Empty/null narration handled correctly");
    }

    @Test
    public void testPredictFull_WithValidNarration() {
        String narration = "UPI/PAY/STARBUCKS";
        LocalModelInferenceService.PredictionResult result = 
            localModelInferenceService.predictFull(narration);
        
        assertNotNull(result, "Prediction result should not be null");
        assertNotNull(result.getPredictedCategory(), "Category should not be null");
        assertNotEquals("", result.getPredictedCategory(), "Category should not be empty");
        
        // Check transaction_type (may be null if not predicted)
        System.out.println("✅ Full prediction result:");
        System.out.println("   Category: " + result.getPredictedCategory());
        System.out.println("   Transaction Type: " + result.getTransactionType());
        System.out.println("   Intent: " + result.getIntent());
        System.out.println("   Confidence: " + result.getConfidence());
    }

    @Test
    public void testPredictFull_WithZomatoNarration() {
        String narration = "ZOMATO";
        LocalModelInferenceService.PredictionResult result = 
            localModelInferenceService.predictFull(narration);
        
        assertNotNull(result, "Prediction result should not be null");
        assertNotNull(result.getPredictedCategory(), "Category should not be null");
        
        // For ZOMATO, we expect a food-related category
        System.out.println("✅ ZOMATO prediction:");
        System.out.println("   Category: " + result.getPredictedCategory());
        System.out.println("   Transaction Type: " + result.getTransactionType());
        System.out.println("   Intent: " + result.getIntent());
        System.out.println("   Confidence: " + result.getConfidence());
        
        // Verify confidence is reasonable (0.0 to 1.0)
        assertTrue(result.getConfidence() >= 0.0 && result.getConfidence() <= 1.0, 
            "Confidence should be between 0.0 and 1.0");
    }

    @Test
    public void testPredictFull_WithStarbucksNarration() {
        String narration = "UPI/PAY/1234567890/STARBUCKS/txn@paytm";
        LocalModelInferenceService.PredictionResult result = 
            localModelInferenceService.predictFull(narration);
        
        assertNotNull(result, "Prediction result should not be null");
        assertNotNull(result.getPredictedCategory(), "Category should not be null");
        
        System.out.println("✅ STARBUCKS prediction:");
        System.out.println("   Category: " + result.getPredictedCategory());
        System.out.println("   Transaction Type: " + result.getTransactionType());
        System.out.println("   Intent: " + result.getIntent());
        System.out.println("   Confidence: " + result.getConfidence());
    }

    @Test
    public void testPredictFull_WithEmptyNarration() {
        LocalModelInferenceService.PredictionResult result = 
            localModelInferenceService.predictFull("");
        
        assertNotNull(result, "Prediction result should not be null");
        assertEquals("Uncategorized", result.getPredictedCategory(), 
            "Empty narration should return 'Uncategorized'");
        
        result = localModelInferenceService.predictFull(null);
        assertNotNull(result, "Prediction result should not be null");
        assertEquals("Uncategorized", result.getPredictedCategory(), 
            "Null narration should return 'Uncategorized'");
        
        System.out.println("✅ Empty/null narration handled correctly in predictFull");
    }

    @Test
    public void testPredictFull_WithVariousNarrations() {
        String[] narrations = {
            "AMAZON PAY",
            "SWIGGY FOOD",
            "UBER RIDE",
            "NETFLIX SUBSCRIPTION",
            "ELECTRICITY BILL PAYMENT"
        };
        
        for (String narration : narrations) {
            LocalModelInferenceService.PredictionResult result = 
                localModelInferenceService.predictFull(narration);
            
            assertNotNull(result, "Result should not be null for: " + narration);
            assertNotNull(result.getPredictedCategory(), 
                "Category should not be null for: " + narration);
            
            System.out.println("✅ '" + narration + "' → Category: " + 
                result.getPredictedCategory() + ", Type: " + result.getTransactionType());
        }
    }
}

