package com.budgetbuddy.service;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger logger = LoggerFactory.getLogger(LocalModelInferenceService.class);

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
        // Python command configuration loaded (not logged for security)
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
            // Use -u flag for unbuffered output (immediate stderr/stdout)
            String[] command = {
                pythonCommand,
                "-u",  // Unbuffered mode - ensures immediate output
                scriptPath,
                jsonArray.toString()
            };
            
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(false);  // Keep stderr separate from stdout
            pb.directory(new java.io.File(projectRoot));
            
            // Start Python process (command details not logged for security)
            Process process = pb.start();
            logger.info("✅ Python process started. PID: {} (processing {} transactions)", process.pid(), descriptions.size());
            
            // Read stdout in separate thread (non-blocking)
            StringBuilder output = new StringBuilder();
            Thread stdoutReaderThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.trim().startsWith("[") || line.trim().startsWith("{")) {
                            output.append(line);
                        }
                    }
                } catch (Exception e) {
                    logger.debug("Error reading Python stdout: {}", e.getMessage());
                }
            });
            stdoutReaderThread.setDaemon(true);
            stdoutReaderThread.start();
            logger.info("Started Python stdout reader thread");
            
            // Read stderr in separate thread (includes progress messages)
            StringBuilder errorOutput = new StringBuilder();
            Thread errorReaderThread = new Thread(() -> {
                try {
                    logger.info("Python stderr reader thread started, waiting for output...");
                    java.io.InputStream errorStream = process.getErrorStream();
                    logger.info("Error stream obtained: {}", errorStream != null);
                    BufferedReader errorReader = new BufferedReader(
                            new InputStreamReader(errorStream));
                    String line;
                    int lineCount = 0;
                    boolean firstOutput = true;
                    long lastOutputTime = System.currentTimeMillis();
                    logger.info("Starting to read from Python stderr (readLine will block until data available)...");
                    while ((line = errorReader.readLine()) != null) {
                        lastOutputTime = System.currentTimeMillis();
                        // Skip lines containing command details
                        if (line.contains("Command:") || (line.contains("python") && line.contains("-u") && line.contains("/"))) {
                            continue;  // Skip command lines
                        }
                        
                        lineCount++;
                        errorOutput.append(line).append("\n");
                        
                        // Always log first few lines to see if Python is outputting
                        if (firstOutput && lineCount <= 10) {
                            logger.info("Python[{}]: {}", lineCount, line);
                            if (lineCount >= 10) {
                                firstOutput = false;
                            }
                        }
                        
                        // Log important progress messages
                        if (line.contains("🚀") || line.contains("📝") || line.contains("✅") || 
                            line.contains("📦") || line.contains("🔄") || line.contains("📋") ||
                            line.contains("Batch") || line.contains("progress") || 
                            line.contains("Step") || line.contains("chunk") ||
                            line.contains("DistilBERT") || line.contains("YAML") ||
                            line.contains("keyword") || line.contains("ML prediction") ||
                            line.contains("Starting") || line.contains("Processing") ||
                            line.contains("complete") || line.contains("Tokenizing") ||
                            line.contains("Python inference script") || line.contains("Arguments received") ||
                            line.contains("Script path") || line.contains("Working dir")) {
                            logger.info("Python: {}", line);
                        }
                    }
                    if (lineCount > 0) {
                        logger.info("Python stderr reader finished. Total lines: {}", lineCount);
                    } else {
                        logger.warn("⚠️ Python stderr reader finished with NO output (0 lines)");
                    }
                    errorReader.close();
                } catch (Exception e) {
                    logger.error("Error reading Python stderr: {}", e.getMessage(), e);
                }
            });
            errorReaderThread.setDaemon(true);
            logger.info("About to start Python stderr reader thread...");
            errorReaderThread.start();
            logger.info("Started Python stderr reader thread");
            
            // Check if process is alive immediately
            boolean isAlive = process.isAlive();
            logger.info("Process isAlive: {}, exitValue check...", isAlive);
            try {
                int exitValue = process.exitValue();
                logger.warn("⚠️ Process already exited with code: {}", exitValue);
            } catch (IllegalThreadStateException e) {
                logger.info("✅ Process is running (hasn't exited yet)");
            }
            
            // Monitor process and stderr output
            Thread monitorThread = new Thread(() -> {
                try {
                    int checkCount = 0;
                    while (process.isAlive() && checkCount < 60) { // Check for up to 60 seconds
                        Thread.sleep(1000); // Check every second
                        checkCount++;
                        if (checkCount % 5 == 0) {
                            logger.info("⏳ Python process still running... ({}s elapsed, PID: {})", checkCount, process.pid());
                        }
                    }
                    if (process.isAlive()) {
                        logger.warn("⚠️ Python process still running after 60s, continuing to wait...");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            monitorThread.setDaemon(true);
            monitorThread.start();
            
            // Give stderr reader a moment to start, then check for initial output
            Thread.sleep(500);
            logger.info("Checking for Python output after 500ms...");
            
            // Wait with timeout (allow more time for batch processing)
            int batchTimeout = timeoutSeconds * Math.max(2, descriptions.size() / 10);
            logger.info("⏳ Waiting for Python process to complete (timeout: {}s)...", batchTimeout);
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
                    String reason = jsonResult.optString("reason", "ml_prediction");
                    boolean keywordMatched = jsonResult.optBoolean("keyword_matched", false);
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
                    
                    results.add(new PredictionResult(category, subcategory, transactionType, intent, confidence, reason, keywordMatched));
                }
            } else {
                // Fallback: create Uncategorized results
                for (int i = 0; i < descriptions.size(); i++) {
                    results.add(new PredictionResult("Uncategorized", null, null, null, 0.0, "error", false));
                }
            }
            
            // Ensure we return same number of results as input
            while (results.size() < descriptions.size()) {
                results.add(new PredictionResult("Uncategorized", null, null, null, 0.0, "error", false));
            }
            
            return results;
            
        } catch (Exception e) {
            System.err.println("⚠️ Error in batch inference: " + e.getMessage());
            e.printStackTrace();
            // Return Uncategorized for all
            java.util.List<PredictionResult> results = new java.util.ArrayList<>();
            for (int i = 0; i < descriptions.size(); i++) {
                results.add(new PredictionResult("Uncategorized", null, null, null, 0.0, "error", false));
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
            return new PredictionResult("Uncategorized", null, null, null, 0.0, "empty_input", false);
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
                "-u",  // Unbuffered mode - ensures immediate output
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
            
            // Log stderr if there's content (for debugging) - filter out command details
            if (errorOutput.length() > 0) {
                String filteredError = errorOutput.toString()
                    .replaceAll("Command:.*\\n", "")  // Remove command lines
                    .replaceAll("python.*-u.*\\n", "")  // Remove python command lines
                    .replaceAll("/Users/.*?/", "***/");  // Sanitize paths
                if (!filteredError.trim().isEmpty()) {
                    System.out.println("Python stderr: " + filteredError);
                }
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
                    // Filter out command details from error output
                    String filteredError = errorOutput.toString()
                        .replaceAll("Command:.*\\n", "")
                        .replaceAll("python.*-u.*\\n", "")
                        .replaceAll("/Users/.*?/", "***/");
                    if (!filteredError.trim().isEmpty()) {
                        errorMsg += "\nPython error output: " + filteredError;
                    }
                }
                if (output.length() > 0) {
                    errorMsg += "\nPython stdout: " + output.toString();
                } else {
                    errorMsg += "\nNo output from Python script";
                }
                throw new RuntimeException(errorMsg);
            } else if (jsonOutput.isEmpty()) {
                // Exit code is 0 but no output - also an error
                // Filter command details from error output
                String filteredError = errorOutput.toString()
                    .replaceAll("Command:.*\\n", "")
                    .replaceAll("python.*-u.*\\n", "")
                    .replaceAll("/Users/.*?/", "***/");
                System.err.println("⚠️ Empty output from inference script. Error output: " + filteredError);
                throw new RuntimeException("Empty output from inference script");
            } else if (jsonResponse == null) {
                // We have output but couldn't parse it
                System.err.println("⚠️ No JSON found in output: " + jsonOutput);
                // Filter command details from error output
                String filteredError = errorOutput.toString()
                    .replaceAll("Command:.*\\n", "")
                    .replaceAll("python.*-u.*\\n", "")
                    .replaceAll("/Users/.*?/", "***/");
                System.err.println("Error output: " + filteredError);
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
            String reason = jsonResponse.optString("reason", "ml_prediction");
            boolean keywordMatched = jsonResponse.optBoolean("keyword_matched", false);
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

            return new PredictionResult(category, subcategory, transactionType, intent, confidence, reason, keywordMatched);

        } catch (Exception e) {
            System.err.println("⚠️ Error in local inference: " + e.getMessage());
            System.err.println("Exception type: " + e.getClass().getSimpleName());
            e.printStackTrace();
            return new PredictionResult("Uncategorized", null, null, null, 0.0, "error", false);
        }
    }

    /**
     * Result class for prediction output with calibration and reason
     */
    public static class PredictionResult {
        private final String predictedCategory;
        private final String predictedSubcategory;
        private final String transactionType;
        private final String intent;
        private final double confidence;
        private final String reason;
        private final boolean keywordMatched;

        public PredictionResult(String predictedCategory, String predictedSubcategory, 
                               String transactionType, String intent, double confidence,
                               String reason, boolean keywordMatched) {
            this.predictedCategory = predictedCategory;
            this.predictedSubcategory = predictedSubcategory;
            this.transactionType = transactionType;
            this.intent = intent;
            this.confidence = confidence;
            this.reason = reason != null ? reason : "ml_prediction";
            this.keywordMatched = keywordMatched;
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

        public String getReason() {
            return reason;
        }

        public boolean isKeywordMatched() {
            return keywordMatched;
        }
    }
}

