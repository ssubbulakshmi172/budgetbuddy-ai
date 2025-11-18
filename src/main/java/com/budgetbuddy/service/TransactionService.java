package com.budgetbuddy.service;

import com.budgetbuddy.model.CategoryKeyword;
import com.budgetbuddy.model.Transaction;
import com.budgetbuddy.model.User;

import com.budgetbuddy.repository.CategoryKeywordRepository;
import com.budgetbuddy.repository.TransactionRepository;
import com.budgetbuddy.repository.UserRepository;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;


import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class TransactionService {

    private static final Logger logger = LoggerFactory.getLogger(TransactionService.class);

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryKeywordRepository categoryKeywordRepository;

    @Autowired
    private TransactionCategorizationService categorizationService;


    public List<Transaction> getAllTransactions() {
        return getAllTransactions(null);
    }

    public List<Transaction> getAllTransactions(String sort) {
        logger.info("Fetching all transactions with sort: {}", sort);
        List<Transaction> transactions;
        
        if (sort != null && sort.equals("date-asc")) {
            transactions = transactionRepository.findAllByOrderByDateAsc();
        } else if (sort != null && sort.equals("date-desc")) {
            transactions = transactionRepository.findAllByOrderByDateDesc();
        } else {
            transactions = transactionRepository.findAll();
        }
        
        logger.info("Fetched {} transactions", transactions.size());
        return transactions;
    }

    public Transaction getTransactionById(Long id) {
        logger.info("Fetching transaction with ID: {}", id);
        Transaction transaction = transactionRepository.findById(id).orElseThrow(() -> new RuntimeException("Transaction not found"));
        logger.debug("Fetched transaction: {}", transaction);
        return transaction;
    }

    public Transaction saveTransaction(Transaction transaction) {
        logger.debug("Saving transaction: {}", transaction);
        Transaction savedTransaction = transactionRepository.save(transaction);
        logger.info("Transaction saved with ID: {}", savedTransaction.getId());
        return savedTransaction;
    }

    public void deleteTransaction(Long id) {
        logger.info("Deleting transaction with ID: {}", id);
        transactionRepository.deleteById(id);
        logger.info("Transaction with ID: {} deleted successfully", id);
    }



    public void importTransactions(MultipartFile file,Long userId) throws IOException {
        try (InputStream inputStream = file.getInputStream()) {
            Workbook workbook;
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yy");

            // Check file extension and create appropriate workbook for .xls or .xlsx
            if (file.getOriginalFilename().endsWith(".xlsx")) {
                workbook = new XSSFWorkbook(inputStream); // For .xlsx files
            } else if (file.getOriginalFilename().endsWith(".xls")) {
                workbook = new HSSFWorkbook(inputStream); // For .xls files
            } else {
                throw new IllegalArgumentException("Invalid file format. Only .xls or .xlsx files are supported.");
            }

            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rowIterator = sheet.iterator();
            List<CategoryKeyword> categoryKeywords = categoryKeywordRepository.findAll();

            // Skip the header row

            // Skip the first 22 rows
            for (int i = 0; i < 23 && rowIterator.hasNext(); i++) {
                rowIterator.next(); // Skip row
            }

            // First pass: Collect all transaction data (without predictions)
            List<TransactionData> transactionDataList = new ArrayList<>();
            
            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                
                // Safely check if first cell is empty (handles both STRING and NUMERIC cells)
                Cell dateCell = row.getCell(0);
                String firstCellValue = getCellValueAsString(dateCell);
                if (dateCell == null || firstCellValue == null || firstCellValue.trim().isEmpty()) {
                    break;
                }
                
                // Skip header/footer rows
                String trimmedValue = firstCellValue.trim().toUpperCase();
                if (trimmedValue.contains("STATEMENT") || 
                    trimmedValue.contains("SUMMARY") ||
                    trimmedValue.contains("OPENING BALANCE") ||
                    trimmedValue.contains("CLOSING BALANCE") ||
                    trimmedValue.matches("^[*]+$") ||
                    trimmedValue.matches("^[-=]+$")) {
                    logger.debug("Skipping header/footer row {}: {}", row.getRowNum(), trimmedValue);
                    continue;
                }
                
                try {
                    String dateString = getCellValueAsString(dateCell);
                    if (dateString == null || dateString.trim().isEmpty()) {
                        logger.warn("Skipping row {} - empty date", row.getRowNum());
                        continue;
                    }
                    LocalDate date = LocalDate.parse(dateString.trim(), dateFormatter);
                    
                    String narration = getCellValueAsString(row.getCell(1));
                    if (narration == null || narration.trim().isEmpty()) {
                        logger.warn("Skipping row {} - empty narration field", row.getRowNum());
                        continue;
                    }
                    
                    String chequeRefNo = getCellValueAsString(row.getCell(2));
                    if (chequeRefNo == null) {
                        chequeRefNo = "";
                    }
                    Double withdrawalAmt = getNumericCellValue(row.getCell(4));
                    Double depositAmt = getNumericCellValue(row.getCell(5));
                    Double closingAmt = getNumericCellValue(row.getCell(6));

                    withdrawalAmt = (withdrawalAmt == null) ? 0.0 : withdrawalAmt;
                    depositAmt = (depositAmt == null) ? 0.0 : depositAmt;
                    closingAmt = (closingAmt == null) ? 0.0 : closingAmt;
                    Double amount = withdrawalAmt <= 0.0 ? depositAmt : (-1 * withdrawalAmt);

                    transactionDataList.add(new TransactionData(
                        date, narration, chequeRefNo, withdrawalAmt, 
                        depositAmt, closingAmt, amount, row.getRowNum()
                    ));
                } catch (DateTimeParseException e) {
                    logger.warn("Skipping row {} due to date parse error: {}", row.getRowNum(), e.getMessage());
                } catch (IllegalStateException e) {
                    // Handle "Cannot get a STRING value from a NUMERIC cell" error
                    logger.warn("Skipping row {} due to cell type error (likely numeric cell read as string): {}", row.getRowNum(), e.getMessage());
                } catch (Exception e) {
                    // Catch any other parsing errors (header/footer rows, etc.)
                    logger.warn("Skipping row {} due to error: {} - {}", row.getRowNum(), e.getClass().getSimpleName(), e.getMessage());
                }
            }
            
            // Find user once
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty()) {
                throw new IllegalArgumentException("User not found: " + userId);
            }
            User user = userOpt.get();
            
            // BATCH PREDICT: Get all narrations and predict in one single batch call
            List<String> narrations = transactionDataList.stream()
                .map(td -> td.narration)
                .collect(Collectors.toList());
            
            logger.info("Batch predicting {} transactions in single batch (model loads once)...", narrations.size());
            List<LocalModelInferenceService.PredictionResult> batchPredictions;
            long batchStartTime = System.currentTimeMillis();
            
            // Start progress monitoring in background
            final int totalCount = narrations.size();
            Thread progressThread = new Thread(() -> {
                try {
                    long startTime = System.currentTimeMillis();
                    while (!Thread.currentThread().isInterrupted()) {
                        Thread.sleep(5000); // Log every 5 seconds
                        long elapsed = System.currentTimeMillis() - startTime;
                        logger.info("⏳ Batch prediction in progress... (elapsed: {}s, processing {} transactions)", 
                            elapsed / 1000, totalCount);
                    }
                } catch (InterruptedException e) {
                    // Thread interrupted, prediction completed
                }
            });
            progressThread.setDaemon(true);
            progressThread.start();
            
            try {
                batchPredictions = categorizationService.getBatchFullPredictions(narrations);
                progressThread.interrupt(); // Stop progress logging
                
                long batchTime = System.currentTimeMillis() - batchStartTime;
                logger.info("✅ Batch prediction completed for {} transactions in {}ms ({}s, avg: {}ms per transaction)", 
                    batchPredictions.size(), batchTime, batchTime / 1000.0, 
                    batchTime / (double) batchPredictions.size());
            } catch (Exception ex) {
                progressThread.interrupt(); // Stop progress logging
                long batchTime = System.currentTimeMillis() - batchStartTime;
                logger.error("❌ Batch prediction failed after {}ms ({}s), falling back to error predictions: {}", 
                    batchTime, batchTime / 1000.0, ex.getMessage(), ex);
                batchPredictions = new ArrayList<>();
                for (int i = 0; i < narrations.size(); i++) {
                    batchPredictions.add(new LocalModelInferenceService.PredictionResult("Uncategorized", null, null, null, 0.0, "error", false));
                }
            }
            
            // Second pass: Create transactions with predictions
            logger.info("Creating and saving {} transactions...", transactionDataList.size());
            int savedCount = 0;
            int errorCount = 0;
            long saveStartTime = System.currentTimeMillis();
            
            // Process manual keyword matching in parallel with transaction creation
            java.util.concurrent.ExecutorService keywordExecutor = java.util.concurrent.Executors.newFixedThreadPool(
                Math.min(4, Runtime.getRuntime().availableProcessors()));
            List<java.util.concurrent.Future<String>> keywordFutures = new ArrayList<>();
            
            // Submit all keyword matching tasks in parallel
            for (TransactionData td : transactionDataList) {
                final String narration = td.narration;
                keywordFutures.add(keywordExecutor.submit(() -> {
                    return categoryKeywords.stream()
                        .filter(keyword -> narration.matches("(?i).*\\b" + Pattern.quote(keyword.getKeyword()) + "\\b.*"))
                        .map(CategoryKeyword::getCategoryName)
                        .findFirst()
                        .orElse(null);
                }));
            }
            
            for (int i = 0; i < transactionDataList.size(); i++) {
                TransactionData td = transactionDataList.get(i);
                LocalModelInferenceService.PredictionResult prediction = 
                    (i < batchPredictions.size()) ? batchPredictions.get(i) : 
                    new LocalModelInferenceService.PredictionResult("Uncategorized", null, null, null, 0.0, "error", false);
                
                try {
                    // Get manual keyword matching result (from parallel processing)
                    String categoryName = null;
                    try {
                        categoryName = keywordFutures.get(i).get();
                    } catch (Exception e) {
                        logger.warn("Error getting keyword match for transaction {}: {}", i, e.getMessage());
                    }

                    // Create and save transaction
                    Transaction transaction = new Transaction();
                    transaction.setDate(td.date);
                    transaction.setNarration(td.narration);
                    transaction.setChequeRefNo(td.chequeRefNo);
                    transaction.setWithdrawalAmt(td.withdrawalAmt);
                    transaction.setDepositAmt(td.depositAmt);
                    transaction.setClosingBalance(td.closingAmt);
                    transaction.setCategoryName(categoryName);
                    transaction.setPredictedCategory(prediction.getPredictedCategory());
                    transaction.setPredictedSubcategory(prediction.getPredictedSubcategory());
                    transaction.setPredictedTransactionType(prediction.getTransactionType());
                    transaction.setPredictedIntent(prediction.getIntent());
                    transaction.setPredictionConfidence(prediction.getConfidence());
                    transaction.setPredictionReason(prediction.getReason());
                    transaction.setUser(user);
                    transaction.setAmount(td.amount);

                    transactionRepository.save(transaction);
                    savedCount++;
                    
                    // Log progress every 100 transactions
                    if ((i + 1) % 100 == 0) {
                        logger.info("Progress: {}/{} transactions saved...", i + 1, transactionDataList.size());
                    }

                } catch (Exception e) {
                    errorCount++;
                    logger.error("Error processing transaction data at index {} (row {}): {}", 
                        i, td.rowNumber, e.getMessage(), e);
                    // Continue processing instead of throwing - we want to save as many as possible
                    if (errorCount <= 10) {
                        // Log first 10 errors in detail
                        logger.error("Transaction error at row {}: {}", td.rowNumber, e.getMessage());
                    }
                }
            }
            
            keywordExecutor.shutdown();
            
            long saveTime = System.currentTimeMillis() - saveStartTime;
            logger.info("✅ Transaction save completed: {} saved, {} errors, in {}ms ({}s)", 
                savedCount, errorCount, saveTime, saveTime / 1000.0);
            
            if (errorCount > 0) {
                logger.warn("⚠️ {} transactions failed to save. Check logs above for details.", errorCount);
            }
            
            if (savedCount == 0 && errorCount > 0) {
                throw new IllegalArgumentException("Failed to save any transactions. Check logs for details.");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Error reading the file", e);
        }
    }
    
    // Helper class to hold transaction data before prediction
    private static class TransactionData {
        LocalDate date;
        String narration;
        String chequeRefNo;
        Double withdrawalAmt;
        Double depositAmt;
        Double closingAmt;
        Double amount;
        int rowNumber;
        
        TransactionData(LocalDate date, String narration, String chequeRefNo, 
                        Double withdrawalAmt, Double depositAmt, Double closingAmt, 
                        Double amount, int rowNumber) {
            this.date = date;
            this.narration = narration;
            this.chequeRefNo = chequeRefNo;
            this.withdrawalAmt = withdrawalAmt;
            this.depositAmt = depositAmt;
            this.closingAmt = closingAmt;
            this.amount = amount;
            this.rowNumber = rowNumber;
        }
    }

    // Helper method to safely get cell value as string (handles both STRING and NUMERIC cells)
    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return null;
        }
        
        try {
            switch (cell.getCellType()) {
                case STRING:
                    return cell.getStringCellValue();
                case NUMERIC:
                    // If numeric and date-formatted, return as formatted string
                    if (DateUtil.isCellDateFormatted(cell)) {
                        return cell.getDateCellValue().toString();
                    }
                    // Otherwise, return numeric value as string
                    double numValue = cell.getNumericCellValue();
                    if (numValue == (long) numValue) {
                        return String.valueOf((long) numValue);
                    }
                    return String.valueOf(numValue);
                case BOOLEAN:
                    return String.valueOf(cell.getBooleanCellValue());
                case FORMULA:
                    // Handle formula cells
                    try {
                        return cell.getStringCellValue();
                    } catch (IllegalStateException e) {
                        // If formula returns numeric, get numeric value
                        return String.valueOf(cell.getNumericCellValue());
                    }
                default:
                    return null;
            }
        } catch (Exception e) {
            logger.debug("Error reading cell value: {}", e.getMessage());
            return null;
        }
    }
    
    // Helper method to handle numeric cell values and nulls
    private Double getNumericCellValue(Cell cell) {
        if (cell == null) {
            return null;
        }

        // Check if the cell contains a numeric value
        if (cell.getCellType() == CellType.NUMERIC) {
            return cell.getNumericCellValue();
        }

        // Check if the cell contains a string that can be parsed into a numeric value
        if (cell.getCellType() == CellType.STRING) {
            try {
                // Try parsing the string as a double
                return Double.parseDouble(cell.getStringCellValue());
            } catch (NumberFormatException e) {
                // If the string cannot be parsed as a number, return null
                return null;
            }
        }
        
        // Handle formula cells that return numeric values
        if (cell.getCellType() == CellType.FORMULA) {
            try {
                return cell.getNumericCellValue();
            } catch (IllegalStateException e) {
                // Formula returns non-numeric, try string parsing
                try {
                    return Double.parseDouble(cell.getStringCellValue());
                } catch (Exception ignored) {
                    return null;
                }
            }
        }

        // If the cell contains anything other than a numeric or string value, return null
        return null;
    }



    // Delete duplicate transactions
    public int deleteDuplicateTransactions() {
        // Find duplicate transactions based on necessary criteria (e.g., same narration, same date, etc.)
        List<Transaction> duplicateTransactions = transactionRepository.findDuplicateTransactions();
        if (!duplicateTransactions.isEmpty()) {
            transactionRepository.deleteAll(duplicateTransactions);
            return duplicateTransactions.size();
        }
        return 0;
    }

    // Delete transactions based on month, year, and user
    public int deleteTransactionsByMonthYearAndUser(Month month, int year, Long userId) {
        // Call the repository to delete the transactions based on filter
        int monthValue = month.getValue();

        return transactionRepository.deleteTransactionsByMonthYearAndUser(monthValue, year, userId);
    }

    public int deleteSelectedTransactions(List<Long> transactionIds) {
        // Delete transactions by IDs
        List<Transaction> transactionsToDelete = transactionRepository.findAllById(transactionIds);
        transactionRepository.deleteAll(transactionsToDelete);
        return transactionsToDelete.size();
    }


    public List<Transaction> getUncategorizedTransactions() {
        return transactionRepository.findByCategoryNameIsNull();
    }

    public Map<String, List<Transaction>> getTransactionsByCategory() {
        List<Transaction> transactions = transactionRepository.findAll();
        Map<String, List<Transaction>> transactionsByCategory = new HashMap<>();

        // Group transactions by category
        for (Transaction transaction : transactions) {
            String category = transaction.getCategoryName();
            if (!transactionsByCategory.containsKey(category)) {
                transactionsByCategory.put(category, new ArrayList<>());
            }
            transactionsByCategory.get(category).add(transaction);
        }

        return transactionsByCategory;
    }

    public List<Transaction> filterTransactions(int month, int year, Long userId, String categoryName, 
                                               Double amountValue, String amountOperator, String narration) {
        return filterTransactions(month, year, userId, categoryName, null, null, amountValue, amountOperator, narration);
    }
    
    public List<Transaction> filterTransactions(int month, int year, Long userId, String categoryName,
                                               String predictedCategory, String predictedSubcategory,
                                               Double amountValue, String amountOperator, String narration) {
        return transactionRepository.filterTransactions(month, year, userId, categoryName, 
                                                        predictedCategory, predictedSubcategory,
                                                        amountValue, amountOperator, narration);
    }
    
    // Overloaded method for backward compatibility
    public List<Transaction> filterTransactions(int month, int year, Long userId, String categoryName) {
        return filterTransactions(month, year, userId, categoryName, null, null, null, null, null);
    }
    
    // Get distinct AI predicted categories
    public List<String> getDistinctPredictedCategories() {
        return transactionRepository.findDistinctPredictedCategories();
    }
    
    // Get distinct AI predicted subcategories
    public List<String> getDistinctPredictedSubcategories() {
        return transactionRepository.findDistinctPredictedSubcategories();
    }

    public void updateCategory(Long transactionId, String categoryName) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid transaction ID"));
        transaction.setCategoryName(categoryName);
        transactionRepository.save(transaction);
    }
    
    /**
     * Add a user correction for a transaction narration.
     * Saves the correction to user_corrections.json in Efficient Mode-ready format.
     * Only essential data (narration and category) is stored.
     * 
     * @param narration Transaction narration text
     * @param category Corrected category
     * @param userId User ID for Efficient Mode cloud sync (optional, can be null)
     * @param transactionId Transaction ID for Efficient Mode cloud sync (optional, can be null)
     * @return true if correction was added successfully
     */
    public boolean addCorrection(String narration, String category, Long userId, Long transactionId) {
        if (narration == null || narration.trim().isEmpty() || category == null || category.trim().isEmpty()) {
            logger.warn("Cannot add correction: narration or category is empty");
            return false;
        }
        
        try {
            // Call Python script to add correction
            String projectRoot = System.getProperty("user.dir");
            String scriptPath = "mybudget-ai/add_correction.py";
            if (!new java.io.File(scriptPath).isAbsolute()) {
                scriptPath = new java.io.File(projectRoot, scriptPath).getAbsolutePath();
            }
            
            // Escape narration and category for shell (handle quotes and special chars)
            String escapedNarration = narration.replace("\"", "\\\"").replace("$", "\\$").replace("`", "\\`");
            String escapedCategory = category.replace("\"", "\\\"").replace("$", "\\$").replace("`", "\\`");
            
            // Build command with essential data and Efficient Mode metadata (for future cloud sync)
            // Python script will handle userId and transactionId if provided
            String[] command = {
                "python3",
                "-u",
                scriptPath,
                escapedNarration,
                escapedCategory,
                userId != null ? userId.toString() : "",
                transactionId != null ? transactionId.toString() : ""
            };
            
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(new java.io.File(projectRoot));
            Process process = pb.start();
            
            // Read stdout and stderr in separate threads to prevent blocking
            StringBuilder output = new StringBuilder();
            StringBuilder errorOutput = new StringBuilder();
            
            // Read stdout
            Thread stdoutReader = new Thread(() -> {
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                } catch (Exception e) {
                    logger.debug("Error reading Python stdout: {}", e.getMessage());
                }
            });
            stdoutReader.setDaemon(true);
            stdoutReader.start();
            
            // Read stderr (where Python logs errors)
            Thread stderrReader = new Thread(() -> {
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        errorOutput.append(line).append("\n");
                        // Log important messages
                        if (line.contains("✅") || line.contains("❌") || line.contains("Error") || 
                            line.contains("Failed") || line.contains("Exception") || line.contains("Traceback")) {
                            logger.info("Python: {}", line);
                        }
                    }
                } catch (Exception e) {
                    logger.debug("Error reading Python stderr: {}", e.getMessage());
                }
            });
            stderrReader.setDaemon(true);
            stderrReader.start();
            
            // Wait for process
            int exitCode = process.waitFor();
            
            // Wait for readers to finish
            try {
                stdoutReader.join(1000);
                stderrReader.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            if (exitCode == 0) {
                logger.info("✅ Correction added successfully (Efficient Mode-ready): '{}' -> '{}'", 
                    narration.substring(0, Math.min(50, narration.length())), category);
                return true;
            } else {
                logger.error("❌ Failed to add correction. Exit code: {}, Stdout: {}, Stderr: {}", 
                    exitCode, output.toString(), errorOutput.toString());
                return false;
            }
        } catch (Exception e) {
            logger.error("❌ Error adding correction: {}", e.getMessage(), e);
            return false;
        }
    }

}
