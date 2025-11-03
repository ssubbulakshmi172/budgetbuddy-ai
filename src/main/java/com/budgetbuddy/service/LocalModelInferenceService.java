package com.budgetbuddy.service;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * Local Model Inference Service
 * 
 * Performs model inference locally using Python script (no Flask server required).
 * Calls inference_local.py via ProcessBuilder for offline inference.
 */
@Service
public class LocalModelInferenceService {

    @Value("${python.inference.script:mybudget-ai/inference_local.py}")
    private String inferenceScriptPath;

    @Value("${python.command:python3}")
    private String pythonCommand;

    @Value("${python.inference.timeout:30}")
    private int timeoutSeconds;

    /**
     * Initialize service - verify configuration on startup
     */
    @PostConstruct
    public void init() {
        System.out.println("✅ LocalModelInferenceService initialized");
        System.out.println("   Python command: " + pythonCommand);
        System.out.println("   Inference script: " + inferenceScriptPath);
        System.out.println("   Timeout: " + timeoutSeconds + " seconds");
        
        // Verify script exists
        String projectRoot = System.getProperty("user.dir");
        String scriptPath = inferenceScriptPath;
        if (!new java.io.File(scriptPath).isAbsolute()) {
            scriptPath = new java.io.File(projectRoot, scriptPath).getAbsolutePath();
        }
        
        java.io.File scriptFile = new java.io.File(scriptPath);
        if (scriptFile.exists()) {
            System.out.println("   ✅ Script found: " + scriptPath);
        } else {
            System.err.println("   ⚠️ Script not found: " + scriptPath);
        }
    }

    /**
     * Predict category for transaction description using local model
     * 
     * @param description Transaction narration/description
     * @return Predicted category name
     */
    public String getPredictedCategory(String description) {
        if (description == null || description.trim().isEmpty()) {
            return "Uncategorized";
        }

        try {
            PredictionResult result = predictFull(description);
            return result.getPredictedCategory();
        } catch (Exception e) {
            System.err.println("⚠️ Error in local inference: " + e.getMessage());
            return "Uncategorized";
        }
    }

    /**
     * Batch predict categories for multiple descriptions at once (MUCH FASTER!)
     * Loads model once and processes all descriptions in a single Python call.
     * 
     * @param descriptions List of transaction narrations/descriptions
     * @return List of prediction results (same order as input)
     */
    public java.util.List<PredictionResult> predictBatchFull(java.util.List<String> descriptions) {
        if (descriptions == null || descriptions.isEmpty()) {
            return new java.util.ArrayList<>();
        }
        
        try {
            // Create JSON array of descriptions
            org.json.JSONArray jsonArray = new org.json.JSONArray();
            for (String desc : descriptions) {
                if (desc != null && !desc.trim().isEmpty()) {
                    jsonArray.put(desc);
                } else {
                    jsonArray.put("");
                }
            }
            
            String projectRoot = System.getProperty("user.dir");
            String scriptPath = inferenceScriptPath;
            if (!new java.io.File(scriptPath).isAbsolute()) {
                scriptPath = new java.io.File(projectRoot, scriptPath).getAbsolutePath();
            }
            
            // Pass JSON array as single argument
            String[] command = {
                pythonCommand,
                scriptPath,
                jsonArray.toString()
            };
            
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(false);
            pb.directory(new java.io.File(projectRoot));
            
            Process process = pb.start();
            
            // Read stdout
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().startsWith("[") || line.trim().startsWith("{")) {
                        output.append(line);
                    }
                }
            }
            
            // Read stderr in separate thread
            StringBuilder errorOutput = new StringBuilder();
            Thread errorReaderThread = new Thread(() -> {
                try (BufferedReader errorReader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = errorReader.readLine()) != null) {
                        errorOutput.append(line).append("\n");
                    }
                } catch (Exception e) {
                    // Ignore
                }
            });
            errorReaderThread.start();
            
            // Wait with timeout (allow more time for batch processing)
            int batchTimeout = timeoutSeconds * Math.max(2, descriptions.size() / 10);
            boolean finished = process.waitFor(batchTimeout, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                errorReaderThread.interrupt();
                throw new RuntimeException("Batch inference timeout after " + batchTimeout + " seconds");
            }
            
            errorReaderThread.join(1000);
            
            int exitCode = process.exitValue();
            
            // Parse JSON array output
            String jsonOutput = output.toString().trim();
            java.util.List<PredictionResult> results = new java.util.ArrayList<>();
            
            if (!jsonOutput.isEmpty() && jsonOutput.startsWith("[")) {
                org.json.JSONArray resultArray = new org.json.JSONArray(jsonOutput);
                for (int i = 0; i < resultArray.length(); i++) {
                    org.json.JSONObject jsonResult = resultArray.getJSONObject(i);
                    String category = jsonResult.optString("predicted_category", "Uncategorized");
                    String subcategory = jsonResult.optString("predicted_subcategory", null);
                    String transactionType = jsonResult.optString("transaction_type", null);
                    String intent = jsonResult.optString("intent", null);
                    double confidence = 0.0;
                    
                    if (jsonResult.has("confidence")) {
                        try {
                            Object confObj = jsonResult.get("confidence");
                            if (confObj instanceof org.json.JSONObject) {
                                org.json.JSONObject confJson = (org.json.JSONObject) confObj;
                                if (confJson.has("category")) {
                                    confidence = confJson.getDouble("category");
                                }
                            } else if (confObj instanceof Number) {
                                confidence = ((Number) confObj).doubleValue();
                            }
                        } catch (Exception e) {
                            // Ignore confidence parsing errors
                        }
                    }
                    
                    results.add(new PredictionResult(category, subcategory, transactionType, intent, confidence));
                }
            } else {
                // Fallback: create Uncategorized results
                for (int i = 0; i < descriptions.size(); i++) {
                    results.add(new PredictionResult("Uncategorized", null, null, null, 0.0));
                }
            }
            
            // Ensure we return same number of results as input
            while (results.size() < descriptions.size()) {
                results.add(new PredictionResult("Uncategorized", null, null, null, 0.0));
            }
            
            return results;
            
        } catch (Exception e) {
            System.err.println("⚠️ Error in batch inference: " + e.getMessage());
            e.printStackTrace();
            // Return Uncategorized for all
            java.util.List<PredictionResult> results = new java.util.ArrayList<>();
            for (int i = 0; i < descriptions.size(); i++) {
                results.add(new PredictionResult("Uncategorized", null, null, null, 0.0));
            }
            return results;
        }
    }
    
    /**
     * Get full prediction result (category, transaction_type, intent, confidence)
     * 
     * @param description Transaction narration/description
     * @return Full prediction result
     */
    public PredictionResult predictFull(String description) {
        if (description == null || description.trim().isEmpty()) {
            return new PredictionResult("Uncategorized", null, null, null, 0.0);
        }

        try {
            // Set working directory to project root (so Python can find models/)
            String projectRoot = System.getProperty("user.dir");
            
            // Escape description for shell safety (handle special characters)
            // Use single quotes or proper escaping
            String escapedDescription = description
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", " ");

            // Build command - use absolute path if relative
            String scriptPath = inferenceScriptPath;
            if (!new java.io.File(scriptPath).isAbsolute()) {
                scriptPath = new java.io.File(projectRoot, scriptPath).getAbsolutePath();
            }
            
            String[] command = {
                pythonCommand,
                scriptPath,
                escapedDescription
            };

            // Execute Python script
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(false); // Keep stderr separate from stdout
            pb.directory(new java.io.File(projectRoot));

            Process process = pb.start();

            // Read stdout (JSON output should be here)
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // Only append lines that look like JSON (start with { or [)
                    if (line.trim().startsWith("{") || line.trim().startsWith("[")) {
                        output.append(line);
                    } else {
                        // Log warnings but don't include in JSON parsing
                        System.out.println("Python output (non-JSON): " + line);
                    }
                }
            }
            
            // Read stderr separately (warnings/errors) - read in separate thread to avoid blocking
            StringBuilder errorOutput = new StringBuilder();
            Thread errorReaderThread = new Thread(() -> {
                try (BufferedReader errorReader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = errorReader.readLine()) != null) {
                        errorOutput.append(line).append("\n");
                    }
                } catch (Exception e) {
                    System.err.println("Error reading stderr: " + e.getMessage());
                }
            });
            errorReaderThread.start();

            // Wait for process with timeout
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                errorReaderThread.interrupt();
                throw new RuntimeException("Inference timeout after " + timeoutSeconds + " seconds");
            }

            // Wait for error reader thread to finish
            try {
                errorReaderThread.join(1000); // Wait max 1 second
            } catch (InterruptedException e) {
                // Ignore
            }

            int exitCode = process.exitValue();
            
            // Log stderr if there's content (for debugging)
            if (errorOutput.length() > 0) {
                System.out.println("Python stderr: " + errorOutput.toString());
            }

            // Parse JSON output first - even if exit code is non-zero, JSON might be valid
            String jsonOutput = output.toString().trim();
            
            // Try to parse JSON if we have output
            JSONObject jsonResponse = null;
            if (!jsonOutput.isEmpty()) {
                try {
                    // Try to find JSON object in output (might have warnings before it)
                    int jsonStart = jsonOutput.indexOf("{");
                    if (jsonStart != -1) {
                        // Extract just the JSON part
                        String jsonOnly = jsonOutput.substring(jsonStart);
                        // Find the end of JSON (last })
                        int jsonEnd = jsonOnly.lastIndexOf("}");
                        if (jsonEnd != -1) {
                            jsonOnly = jsonOnly.substring(0, jsonEnd + 1);
                        }
                        
                        jsonResponse = new JSONObject(jsonOnly);
                        System.out.println("✅ Successfully parsed JSON from Python output");
                    }
                } catch (Exception e) {
                    System.err.println("⚠️ Failed to parse JSON: " + jsonOutput);
                    System.err.println("Parse error: " + e.getMessage());
                }
            }
            
            // If we successfully parsed JSON, use it even if exit code is non-zero
            if (jsonResponse != null && jsonResponse.has("predicted_category")) {
                // Valid JSON with prediction - proceed normally
                System.out.println("✅ Got valid prediction JSON (exit code: " + exitCode + ")");
            } else if (exitCode != 0) {
                // Exit code is non-zero AND we don't have valid JSON - this is an error
                String errorMsg = "Inference script exited with code: " + exitCode;
                if (errorOutput.length() > 0) {
                    errorMsg += "\nPython error output: " + errorOutput.toString();
                }
                if (output.length() > 0) {
                    errorMsg += "\nPython stdout: " + output.toString();
                } else {
                    errorMsg += "\nNo output from Python script";
                }
                throw new RuntimeException(errorMsg);
            } else if (jsonOutput.isEmpty()) {
                // Exit code is 0 but no output - also an error
                System.err.println("⚠️ Empty output from inference script. Error output: " + errorOutput.toString());
                throw new RuntimeException("Empty output from inference script");
            } else if (jsonResponse == null) {
                // We have output but couldn't parse it
                System.err.println("⚠️ No JSON found in output: " + jsonOutput);
                System.err.println("Error output: " + errorOutput.toString());
                throw new RuntimeException("No JSON found in inference output");
            }

            // Check for error
            if (jsonResponse.has("error")) {
                System.err.println("⚠️ Inference error: " + jsonResponse.getString("error"));
            }

            // Extract results
            String category = jsonResponse.optString("predicted_category", "Uncategorized");
            String subcategory = jsonResponse.optString("predicted_subcategory", null);
            String transactionType = jsonResponse.optString("transaction_type", null);
            String intent = jsonResponse.optString("intent", null);
            double confidence = 0.0;

            if (jsonResponse.has("confidence")) {
                JSONObject confidenceObj = jsonResponse.getJSONObject("confidence");
                if (confidenceObj.has("category")) {
                    confidence = confidenceObj.getDouble("category");
                } else if (confidenceObj.has("predicted_category")) {
                    confidence = confidenceObj.getDouble("predicted_category");
                } else if (jsonResponse.has("confidence")) {
                    // Single confidence value
                    confidence = jsonResponse.getDouble("confidence");
                }
            }

            return new PredictionResult(category, subcategory, transactionType, intent, confidence);

        } catch (Exception e) {
            System.err.println("⚠️ Error in local inference for '" + description + "': " + e.getMessage());
            System.err.println("Exception type: " + e.getClass().getSimpleName());
            e.printStackTrace();
            return new PredictionResult("Uncategorized", null, null, null, 0.0);
        }
    }

    /**
     * Result class for prediction output
     */
    public static class PredictionResult {
        private final String predictedCategory;
        private final String predictedSubcategory;
        private final String transactionType;
        private final String intent;
        private final double confidence;

        public PredictionResult(String predictedCategory, String predictedSubcategory, 
                               String transactionType, String intent, double confidence) {
            this.predictedCategory = predictedCategory;
            this.predictedSubcategory = predictedSubcategory;
            this.transactionType = transactionType;
            this.intent = intent;
            this.confidence = confidence;
        }

        public String getPredictedCategory() {
            return predictedCategory;
        }

        public String getPredictedSubcategory() {
            return predictedSubcategory;
        }

        public String getTransactionType() {
            return transactionType;
        }

        public String getIntent() {
            return intent;
        }

        public double getConfidence() {
            return confidence;
        }
    }
}

