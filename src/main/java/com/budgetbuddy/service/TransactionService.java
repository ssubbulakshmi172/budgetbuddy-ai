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
        logger.info("Fetched transaction: {}", transaction);
        return transaction;
    }

    public Transaction saveTransaction(Transaction transaction) {
        logger.info("Saving transaction: {}", transaction);
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
                if (row.getCell(0) == null || row.getCell(0).getStringCellValue().trim().isEmpty()) {
                    break;
                }
                try {
                    String dateString = row.getCell(0).getStringCellValue();
                    LocalDate date = LocalDate.parse(dateString, dateFormatter);
                    String narration = row.getCell(1).getStringCellValue();
                    String chequeRefNo = row.getCell(2).getStringCellValue();
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
                }
            }
            
            // Find user once
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty()) {
                throw new IllegalArgumentException("User not found: " + userId);
            }
            User user = userOpt.get();
            
            // BATCH PREDICT: Get all narrations and predict in one call (MUCH FASTER!)
            List<String> narrations = transactionDataList.stream()
                .map(td -> td.narration)
                .collect(Collectors.toList());
            
            logger.info("Batch predicting {} transactions (model loads once)...", narrations.size());
            List<LocalModelInferenceService.PredictionResult> batchPredictions;
            try {
                batchPredictions = categorizationService.getBatchFullPredictions(narrations);
                logger.info("✅ Batch prediction completed for {} transactions", batchPredictions.size());
            } catch (Exception ex) {
                logger.error("Batch prediction failed, falling back to individual predictions: {}", ex.getMessage());
                batchPredictions = new ArrayList<>();
                for (int i = 0; i < narrations.size(); i++) {
                    batchPredictions.add(new LocalModelInferenceService.PredictionResult("Uncategorized", null, null, 0.0));
                }
            }
            
            // Second pass: Create transactions with predictions
            for (int i = 0; i < transactionDataList.size(); i++) {
                TransactionData td = transactionDataList.get(i);
                LocalModelInferenceService.PredictionResult prediction = 
                    (i < batchPredictions.size()) ? batchPredictions.get(i) : 
                    new LocalModelInferenceService.PredictionResult("Uncategorized", null, null, 0.0);
                
                try {
                    // Match category keywords
                    String categoryName = categoryKeywords.stream()
                        .filter(keyword -> td.narration.matches("(?i).*\\b" + Pattern.quote(keyword.getKeyword()) + "\\b.*"))
                        .map(CategoryKeyword::getCategoryName)
                        .findFirst()
                        .orElse(null);

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
                    transaction.setPredictedTransactionType(prediction.getTransactionType());
                    transaction.setPredictedIntent(prediction.getIntent());
                    transaction.setPredictionConfidence(prediction.getConfidence());
                    transaction.setUser(user);
                    transaction.setAmount(td.amount);

                    transactionRepository.save(transaction);

                } catch (Exception e) {
                    logger.error("Error processing transaction data at index {}: {}", i, e.getMessage());
                    throw new IllegalArgumentException("Error processing row " + td.rowNumber + ": " + e.getMessage());
                }
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

    public List<Transaction> filterTransactions(int month, int year, Long userId, String categoryName) {
        return transactionRepository.filterTransactions(month, year, userId, categoryName);
    }

    public void updateCategory(Long transactionId, String categoryName) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid transaction ID"));
        transaction.setCategoryName(categoryName);
        transactionRepository.save(transaction);
    }



}
