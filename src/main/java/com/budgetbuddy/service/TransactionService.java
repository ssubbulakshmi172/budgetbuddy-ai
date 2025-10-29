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
        logger.info("Fetching all transactions");
        List<Transaction> transactions = transactionRepository.findAll();
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


            // Process each row in the sheet
            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                if (row.getCell(0) == null || row.getCell(0).getStringCellValue().trim().isEmpty()) {
                    // Break the loop if the first column is empty or contains only whitespace
                    break;
                }
                try {
                    // Read data from Excel row
                    //String userName = row.getCell(0).getStringCellValue();
                    String dateString = row.getCell(0).getStringCellValue();
                    LocalDate date = LocalDate.parse(dateString, dateFormatter); // Use custom date formatter);
                    String narration = row.getCell(1).getStringCellValue();
                    String chequeRefNo = row.getCell(2).getStringCellValue();
                    Double withdrawalAmt = getNumericCellValue(row.getCell(4));
                    Double depositAmt = getNumericCellValue(row.getCell(5));
                    Double closingAmt=getNumericCellValue(row.getCell(6));

                    withdrawalAmt = (withdrawalAmt == null) ? 0.0 : withdrawalAmt;

                    depositAmt = (depositAmt == null) ? 0.0 : depositAmt;

                    closingAmt = (closingAmt == null) ? 0.0 : closingAmt;

                    Double amount = withdrawalAmt <= 0.0 ? depositAmt : (-1 * withdrawalAmt);

                    // Find user by name
                    Optional<User> userOpt = userRepository.findById(userId);
                    if (userOpt.isEmpty()) {
                        throw new IllegalArgumentException("User not found: "+userId );
                    }

                    User user = userOpt.get();



                    String categoryName = categoryKeywords.stream()
                            .filter(keyword -> narration.matches("(?i).*\\b" + Pattern.quote(keyword.getKeyword()) + "\\b.*"))
                            .map(CategoryKeyword::getCategoryName)
                            .findFirst()
                            .orElse(null);

                    String aiPredictedCategory;
                    try {
                        aiPredictedCategory = categorizationService.getPredictedCategory(narration);
                    } catch (Exception ex) {
                        aiPredictedCategory = "Uncategorized";
                        logger.error("AI prediction failed for narration: {} -> {}", narration, ex.getMessage());
                    }

                    // Create and save transaction
                    Transaction transaction = new Transaction();
                    transaction.setDate(date);
                    transaction.setNarration(narration);
                    transaction.setChequeRefNo(chequeRefNo);
                    transaction.setWithdrawalAmt(withdrawalAmt);
                    transaction.setDepositAmt(depositAmt);
                    transaction.setClosingBalance(closingAmt);
                    transaction.setCategoryName(categoryName);
                    transaction.setPredictedCategory(aiPredictedCategory);
                    transaction.setUser(user);
                    transaction.setAmount(amount);

                    transactionRepository.save(transaction);

                } catch (DateTimeParseException e) {
                    e.printStackTrace();
                    throw new IllegalArgumentException("Invalid date format in row " + row.getRowNum()+"====>"+  row.getCell(0).getStringCellValue()+ e.getMessage());
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new IllegalArgumentException("Error processing row " + row.getRowNum()+"====>"+ row.getCell(0).getStringCellValue()+e.getMessage());
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Error reading the file", e);
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
