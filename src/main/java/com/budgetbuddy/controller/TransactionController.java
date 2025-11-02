package com.budgetbuddy.controller;

import com.budgetbuddy.model.CategoryKeyword;
import com.budgetbuddy.model.Transaction;
import com.budgetbuddy.model.User;
import com.budgetbuddy.repository.UserRepository;
import com.budgetbuddy.service.CategoryKeywordService;
import com.budgetbuddy.service.TransactionCategorizationService;
import com.budgetbuddy.service.LocalModelInferenceService;
import com.budgetbuddy.service.TransactionService;
import com.budgetbuddy.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import jakarta.annotation.PostConstruct;

@Controller
@RequestMapping("/transactions")
public class TransactionController {

    private static final Logger logger = LoggerFactory.getLogger(TransactionController.class);

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private CategoryKeywordService categoryKeywordService;

    @Autowired
    private TransactionCategorizationService categorizationService;
    
    /**
     * Log service injection status
     */
    @PostConstruct
    public void init() {
        if (categorizationService != null) {
            logger.info("✅ TransactionController: TransactionCategorizationService injected successfully");
        } else {
            logger.error("❌ TransactionController: TransactionCategorizationService is NULL!");
        }
    }


    @GetMapping
    public String listTransactions(@RequestParam(required = false) String sort, Model model) {
        logger.info("Entering listTransactions method with sort parameter: {}", sort);
        try {
            List<Transaction> transactions = transactionService.getAllTransactions(sort);
            model.addAttribute("transactions", transactions);
            model.addAttribute("currentSort", sort);
            logger.debug("Fetched {} transactions", transactions.size());
        } catch (Exception e) {
            logger.error("Error occurred while fetching transactions: {}", e.getMessage(), e);
        }
        logger.info("Exiting listTransactions method");
        return "transaction/transaction-list";
    }

    @GetMapping("/new")
    public String newTransactionForm(Model model) {
        logger.info("Entering newTransactionForm method");
        try {
            Transaction transaction = new Transaction();
            transaction.setDate(LocalDate.now());
            model.addAttribute("transaction", transaction);
            model.addAttribute("users", userService.getAllUsers());
            logger.debug("Created new transaction form");
        } catch (Exception e) {
            logger.error("Error occurred while creating new transaction form: {}", e.getMessage(), e);
        }
        logger.info("Exiting newTransactionForm method");
        return "transaction/transaction-form";
    }

    @PostMapping
    public String saveTransaction(@ModelAttribute Transaction transaction,
                                  @RequestParam(value = "user", required = false) Long userId,
                                  Model model) {
        logger.info("Entering saveTransaction method with transaction: {}", transaction);
        logger.info("Received userId parameter: {}", userId);
        logger.info("🔍 DEBUG: Transaction narration value: '{}' (null={}, empty={})", 
            transaction.getNarration(), 
            transaction.getNarration() == null,
            transaction.getNarration() != null && transaction.getNarration().trim().isEmpty());
        logger.info("🔍 DEBUG: categorizationService is null: {}", categorizationService == null);
        long startTime = System.currentTimeMillis();
        try {
            // Handle user binding - convert userId to User object
            if (userId != null) {
                User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));
                transaction.setUser(user);
                logger.info("Set user for transaction: {}", user);
            } else if (transaction.getUser() != null && transaction.getUser().getId() != null) {
                // If user object has ID but wasn't properly bound, fetch it
                User user = userRepository.findById(transaction.getUser().getId())
                    .orElseThrow(() -> new RuntimeException("User not found with ID: " + transaction.getUser().getId()));
                transaction.setUser(user);
            } else {
                logger.warn("No user specified for transaction");
            }
            
            // Calculate amount if not set (deposit - withdrawal)
            if (transaction.getAmount() == null) {
                double deposit = transaction.getDepositAmt() != null ? transaction.getDepositAmt() : 0.0;
                double withdrawal = transaction.getWithdrawalAmt() != null ? transaction.getWithdrawalAmt() : 0.0;
                transaction.setAmount(deposit - withdrawal);
            }
            List<CategoryKeyword> categoryKeywords = categoryKeywordService.getAllCategories();
            
            // Set full prediction (category, transaction_type, intent, confidence) if narration exists
            long inferenceStartTime = System.currentTimeMillis();
            if (transaction.getNarration() != null && !transaction.getNarration().trim().isEmpty()) {
                logger.info("Calling categorization for narration: '{}'", transaction.getNarration());
                try {
                    LocalModelInferenceService.PredictionResult prediction = 
                        categorizationService.getFullPrediction(transaction.getNarration());

                    String categoryName = categoryKeywords.stream()
                            .filter(keyword -> transaction.getNarration().matches("(?i).*\\b" + Pattern.quote(keyword.getKeyword()) + "\\b.*"))
                            .map(CategoryKeyword::getCategoryName)
                            .findFirst()
                            .orElse(null);
                    transaction.setCategoryName(categoryName);

                    transaction.setPredictedCategory(prediction.getPredictedCategory());
                    transaction.setPredictedTransactionType(prediction.getTransactionType());
                    transaction.setPredictedIntent(prediction.getIntent());
                    transaction.setPredictionConfidence(prediction.getConfidence());
                    long inferenceTime = System.currentTimeMillis() - inferenceStartTime;
                    logger.info("✅ Prediction result: category={}, type={}, intent={}, confidence={}, time={}ms", 
                        prediction.getPredictedCategory(), prediction.getTransactionType(), 
                        prediction.getIntent(), prediction.getConfidence(), inferenceTime);
                } catch (Exception e) {
                    logger.error("❌ Failed to predict category for transaction '{}': {}", 
                        transaction.getNarration(), e.getMessage(), e);
                    transaction.setPredictedCategory("Uncategorized");
                }
            } else {
                logger.warn("⚠️ Narration is null or empty, skipping categorization");
            }
            
            transactionService.saveTransaction(transaction);
            long totalTime = System.currentTimeMillis() - startTime;
            logger.info("Transaction saved successfully with ID: {} in {}ms", transaction.getId(), totalTime);
            
            // Add saved transaction and timing to model for display
            model.addAttribute("savedTransaction", transaction);
            model.addAttribute("saveTime", totalTime);
            model.addAttribute("users", userService.getAllUsers());
            // Load distinct categories for dropdown
            List<String> categories = categoryKeywordService.getDistinctCategories();
            model.addAttribute("categories", categories);
            
            // Create a new transaction object for the form
            Transaction newTransaction = new Transaction();
            newTransaction.setDate(LocalDate.now());
            model.addAttribute("transaction", newTransaction);
            model.addAttribute("success", "Transaction saved successfully!");
            
            return "transaction/transaction-form";
        } catch (Exception e) {
            logger.error("Error occurred while saving transaction: {}", e.getMessage(), e);
            model.addAttribute("error", "Failed to save transaction: " + e.getMessage());
            model.addAttribute("transaction", transaction);
            model.addAttribute("users", userService.getAllUsers());
            // Load distinct categories for dropdown
            List<String> categories = categoryKeywordService.getDistinctCategories();
            model.addAttribute("categories", categories);
            return "transaction/transaction-form";
        }
    }

    @GetMapping("/edit/{id}")
    public String editTransactionForm(@PathVariable Long id, Model model) {
        logger.info("Entering editTransactionForm method for transaction ID: {}", id);
        try {
            Transaction transaction = transactionService.getTransactionById(id);
            model.addAttribute("transaction", transaction);
            model.addAttribute("users", userService.getAllUsers());
            String formattedDate = transaction.getDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
            model.addAttribute("formattedTransactionDate", formattedDate);
            logger.debug("Fetched transaction with ID: {}", id);
        } catch (Exception e) {
            logger.error("Error occurred while fetching transaction with ID {}: {}", id, e.getMessage(), e);
        }
        logger.info("Exiting editTransactionForm method for transaction ID: {}", id);
        return "transaction/transaction-form";
    }

    @PostMapping("/edit/{id}")
    public String saveTransaction(@PathVariable Long id, @ModelAttribute Transaction transaction) {
        logger.info("Entering saveTransaction method for updating transaction ID: {}", id);
        try {
            transactionService.saveTransaction(transaction);
            logger.info("Transaction updated successfully with ID: {}", id);
        } catch (Exception e) {
            logger.error("Error occurred while updating transaction with ID {}: {}", id, e.getMessage(), e);
        }
        return "redirect:/transactions/list";
    }

    @GetMapping("/delete/{id}")
    public String deleteTransaction(@PathVariable Long id) {
        logger.info("Entering deleteTransaction method for transaction ID: {}", id);
        try {
            transactionService.deleteTransaction(id);
            logger.info("Transaction with ID: {} deleted successfully", id);
        } catch (Exception e) {
            logger.error("Error occurred while deleting transaction with ID {}: {}", id, e.getMessage(), e);
        }
        return "redirect:/transactions";
    }

    @GetMapping("/transaction-upload")
    public String showUploadForm(Model model) {
        logger.info("Entering showUploadForm method");
        model.addAttribute("users", userService.getAllUsers());
        logger.info("Exiting showUploadForm method");
        return "transaction/transaction-upload";
    }

    @PostMapping("/transaction-upload")
    public String handleFileUpload(@RequestParam("files") MultipartFile[] files,
                                   @RequestParam("user") Long userId, Model model) {
        logger.info("Entering handleFileUpload method with userId: {}", userId);
        List<String> successFiles = new ArrayList<>();
        List<String> errorFiles = new ArrayList<>();

        for (MultipartFile file : files) {
            if (!file.isEmpty()) {
                try {
                    transactionService.importTransactions(file, userId);
                    successFiles.add(file.getOriginalFilename());
                    logger.info("File '{}' uploaded successfully for user ID: {}", file.getOriginalFilename(), userId);
                } catch (Exception e) {
                    errorFiles.add(file.getOriginalFilename());
                    logger.error("Error processing file '{}' for user ID {}: {}", file.getOriginalFilename(), userId, e.getMessage(), e);
                }
            }
        }

        // Build success message
        StringBuilder messageBuilder = new StringBuilder();
        if (!successFiles.isEmpty()) {
            messageBuilder.append("Files uploaded successfully: ").append(String.join(", ", successFiles));
            if (!errorFiles.isEmpty()) {
                messageBuilder.append(". ");
            }
        }
        
        // Only add error message if there are actual errors
        if (!errorFiles.isEmpty()) {
            messageBuilder.append("Errors in files: ").append(String.join(", ", errorFiles));
        }
        
        String message = messageBuilder.toString();
        if (!message.isEmpty()) {
            model.addAttribute("message", message);
            model.addAttribute("hasErrors", !errorFiles.isEmpty());
            model.addAttribute("successFiles", successFiles);
            model.addAttribute("errorFiles", errorFiles);
        }
        
        logger.info("Exiting handleFileUpload method");
        return "transaction/transaction-upload";
    }

    @GetMapping("/recategorize-all")
    public String recategorizeAllTransactions() {
        logger.info("Entering recategorizeAllTransactions method");
        try {

            List<Transaction> uncategorizedTransactions = transactionService.getUncategorizedTransactions();
            List<CategoryKeyword> categoryKeywords = categoryKeywordService.getAllCategories();

            uncategorizedTransactions.forEach(transaction -> {
                String categoryName = categoryKeywords.stream()
                        .filter(keyword -> transaction.getNarration().matches("(?i).*\\b" + Pattern.quote(keyword.getKeyword()) + "\\b.*"))
                        .map(CategoryKeyword::getCategoryName)
                        .findFirst()
                        .orElse(null);
                transaction.setCategoryName(categoryName);
                transactionService.saveTransaction(transaction);
            });
            logger.info("Recategorized all transactions");
        } catch (Exception e) {
            logger.error("Error occurred while recategorizing transactions: {}", e.getMessage(), e);
        }
        return "redirect:/transactions";
    }

    @GetMapping("/transactions/uncategorized")
    public String getUncategorizedTransactions(Model model) {
        logger.info("Entering getUncategorizedTransactions method");
        try {
            List<Transaction> uncategorizedTransactions = transactionService.getUncategorizedTransactions();
            model.addAttribute("uncategorizedTransactions", uncategorizedTransactions);
            logger.debug("Fetched {} uncategorized transactions", uncategorizedTransactions.size());
        } catch (Exception e) {
            logger.error("Error occurred while fetching uncategorized transactions: {}", e.getMessage(), e);
        }
        logger.info("Exiting getUncategorizedTransactions method");
        return "transaction/transaction-list";
    }

    @GetMapping("/filter-form")
    public String showFilterTransactionsForm(Model model) {
        logger.info("Entering showFilterTransactionsForm method");
        try {
            List<User> users = userRepository.findAll();
            model.addAttribute("users", users);
            model.addAttribute("categoriesKeywords", categoryKeywordService.getDistinctCategories());

            model.addAttribute("transactions", new ArrayList<Transaction>());
        } catch (Exception e) {
            logger.error("Error occurred while fetching users for filter form: {}", e.getMessage(), e);
        }
        logger.info("Exiting showFilterTransactionsForm method");
        return "transaction/transaction-filter";
    }

    @GetMapping("/filter")
    public String filterTransactions(@RequestParam(required = false) Month month,
                                     @RequestParam(required = false) Integer year,
                                     @RequestParam(required = false) Long userId,
                                     @RequestParam(required = false) String category, // Optional category filter
                                     @RequestParam(required = false) Double amountValue,
                                     @RequestParam(required = false) String amountOperator,
                                     @RequestParam(required = false) String narration,
                                     Model model,
                                     RedirectAttributes redirectAttributes) {

        logger.info("Entering filterTransactions method with month: {}, year: {}, userId: {}, category: {}, amount: {} {}, narration: {}",
                month, year, userId, category, amountOperator, amountValue, narration);
        List<User> users = userRepository.findAll();
        model.addAttribute("users", users);

        try {
            // Handle optional parameters and set default values where necessary
            int monthValue = (month != null) ? month.getValue() : -1;  // Default to -1 for unspecified month
            int yearValue = (year != null) ? year : -1;
            String categoryValue = (category != null && !category.isEmpty()) ? category : null;
            
            // Validate and sanitize amount operator
            String validAmountOperator = null;
            if (amountOperator != null && !amountOperator.isEmpty()) {
                if (amountOperator.equals("gt") || amountOperator.equals("lt") || 
                    amountOperator.equals("gte") || amountOperator.equals("lte") || 
                    amountOperator.equals("eq")) {
                    validAmountOperator = amountOperator;
                }
            }
            
            // Sanitize narration (trim whitespace)
            String narrationValue = (narration != null && !narration.trim().isEmpty()) ? narration.trim() : null;

            // Log the adjusted values
            logger.debug("Processed month: {}, year: {}, amount: {} {}, narration: {}",
                    monthValue, yearValue, validAmountOperator, amountValue, narrationValue);
            
            // Call the service to filter transactions
            List<Transaction> transactions = transactionService.filterTransactions(
                    monthValue, yearValue, userId, categoryValue, 
                    amountValue, validAmountOperator, narrationValue);

            // Add results to the model for view rendering
            model.addAttribute("transactions", transactions);
            model.addAttribute("month", month);
            model.addAttribute("year", year);
            model.addAttribute("userId", userId);
            model.addAttribute("categoryKeyword", category);
            model.addAttribute("amountValue", amountValue);
            model.addAttribute("amountOperator", amountOperator);
            model.addAttribute("narration", narration);
            model.addAttribute("categoriesKeywords", categoryKeywordService.getDistinctCategories());
            long transactionCount = transactions.size();
            double totalAmount = transactions.stream().mapToDouble(Transaction::getAmount).sum();
            model.addAttribute("transactionCount", transactionCount);
            model.addAttribute("totalAmount", totalAmount);


            // Log the number of transactions filtered and any relevant details
            if (transactions.isEmpty()) {
                logger.info("No transactions found for the given filters.");
            } else {
                logger.debug("Filtered {} transactions for month: {}, year: {}, userId: {}, category: {}, amount: {} {}, narration: {}",
                        transactions.size(), month, year, userId, category, amountOperator, amountValue, narration);
            }

        } catch (Exception e) {
            // Log the error with exception details for debugging
            logger.error("Error occurred while filtering transactions: month={}, year={}, userId={}, category={}, amount={} {}, narration={}. Exception: {}",
                    month, year, userId, category, amountOperator, amountValue, narration, e.getMessage(), e);
        }

        logger.info("Exiting filterTransactions method");


        // Redirect with parameters
        if (month != null) {
            redirectAttributes.addAttribute("month", month);
        }
        if (year != null) {
            redirectAttributes.addAttribute("year", year);
        }
        if (category != null && !category.isEmpty()) {
            redirectAttributes.addAttribute("category", category);
        } else {
            redirectAttributes.addAttribute("category", "EMPTY");
        }
        if (userId != null) {
            redirectAttributes.addAttribute("userId", userId);
        }

        return "transaction/transaction-list";
    }

    @PostMapping("/delete")
    public String deleteTransactions(@RequestParam(required = false) List<Long> transactionIds,
                                     @RequestParam(required = false) boolean deleteDuplicates,
                                     @RequestParam(required = false) boolean deleteSelected,
                                     Model model) {
        logger.info("Entering deleteTransactions method");
        try {
            int deletedCount = 0;
            if (deleteDuplicates) {
                deletedCount = transactionService.deleteDuplicateTransactions();
                model.addAttribute("message", deletedCount + " duplicate transactions deleted successfully.");
                logger.info("Deleted {} duplicate transactions", deletedCount);
            } else if (deleteSelected && transactionIds != null) {
                deletedCount = transactionService.deleteSelectedTransactions(transactionIds);
                model.addAttribute("message", deletedCount + " selected transactions deleted successfully.");
                logger.info("Deleted {} selected transactions", deletedCount);
            }

            List<Transaction> transactions = transactionService.getAllTransactions();
            model.addAttribute("transactions", transactions);
        } catch (Exception e) {
            logger.error("Error occurred while deleting transactions: {}", e.getMessage(), e);
        }
        logger.info("Exiting deleteTransactions method");
        return "transaction/transaction-list";
    }

    @GetMapping("/dashboard")
    public String getDashboard(

            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Long user,
            Model model
    ) {
        // Log incoming parameters
        logger.info("Fetching dashboard with parameters: year = {}, userID = {}", year, user);

        // Get all transactions
        List<Transaction> transactions;

        // Filter transactions by year if provided
        // Assuming 'user' is a User object or a valid user identifier, and other filter parameters are available
        if (user != null) {
            // Log and fetch transactions filtered by the user
            logger.info("Fetching all transactions for user: " + user);

            // Here, you need to pass the filter parameters like month, year, category, etc.
            transactions = transactionService.filterTransactions(-1, -1, user, "");
        } else {
            // Log and fetch all transactions without any filters
            logger.info("Fetching all transactions");
            transactions = transactionService.getAllTransactions();
        }


        // Log number of transactions fetched
        logger.info("Fetched {} transactions", transactions.size());

        // Initialize a map for category-wise month-level data
        Map<String, Map<String, Double>> categoryMonthSums = new HashMap<>();

        if (!transactions.isEmpty()) {
            categoryMonthSums = transactions.stream()
                    .collect(Collectors.groupingBy(
                            transaction -> {
                                String categoryName = transaction.getCategoryName();
                                return (categoryName == null || categoryName.isEmpty())
                                        ? "Uncategorized"
                                        : categoryName;
                            },
                            Collectors.groupingBy(
                                    transaction -> {
                                        // Convert Month enum to String (e.g., "OCTOBER")
                                        return transaction.getDate().getMonth().toString();
                                    },
                                    Collectors.summingDouble(Transaction::getAmount)
                            )
                    ));
        }


        // Log the computed data
        logger.info("Category-wise month sums: {}", categoryMonthSums);

        // Add attributes to the model for Thymeleaf view
        ObjectMapper objectMapper = new ObjectMapper();
        String jsonCategoryMonthSums = "";

        try {
            jsonCategoryMonthSums = objectMapper.writeValueAsString(categoryMonthSums);
        } catch (Exception e) {
            System.err.println("Error serializing categoryMonthSums: " + e.getMessage());
        }

// Add the serialized JSON to the model
        model.addAttribute("categoryMonthSumsJson", jsonCategoryMonthSums);
        model.addAttribute("categoryMonthSums", categoryMonthSums);

        logger.info("Category-wise month categoryMonthSumsJson: {}", jsonCategoryMonthSums);

        model.addAttribute("transactions", transactions);
        model.addAttribute("year", year);    // Add selected year to the model
        model.addAttribute("users", userService.getAllUsers());

        return "transaction/dashboard";  // Thymeleaf view
    }

    @PostMapping("/editCategory")
    public String editCategory(@RequestParam Long transactionId,
                               @RequestParam String categoryName,
                               @RequestParam(required = false) Integer month,
                               @RequestParam(required = false) Integer year,
                               @RequestParam(required = false) Long userId,
                               @RequestParam(required = false) String categoryKeyword) {
        logger.info("Entering update method with month: {}, year: {}, userId: {}, category: {}",
                month, year, userId, categoryKeyword);
        transactionService.updateCategory(transactionId, categoryName);

        String redirectUrl = "redirect:/transactions/filter?month=" +
                (month != null ? month : "") +
                "&year=" +
                (year != null ? year : "") +
                "&category=" +
                (categoryKeyword != null && !categoryKeyword.isEmpty() ? categoryKeyword : "EMPTY") +
                "&userId=" +
                (userId != null ? userId : "");

        logger.debug("Redirect URL: {}", redirectUrl);
        return redirectUrl;
    }


    // 🔁 Refresh all predictions
    @PostMapping("/refresh-categories")
    public ResponseEntity<Map<String, String>> refreshAllCategories() {
        categorizationService.refreshPredictionsForAll();
        return ResponseEntity.ok(Map.of("message", "✅ All transaction categories refreshed successfully!"));
    }

    @PostMapping("/refresh-category/{id}")
    @ResponseBody
    public String refreshPredictionForTransaction(@PathVariable Long id) {
        try {
            categorizationService.refreshPredictionForTransaction(id);
            return "✅ Refreshed prediction for transaction ID: " + id;
        } catch (Exception e) {
            logger.error("Error refreshing prediction for transaction ID {}: {}", id, e.getMessage(), e);
            return "⚠️ Failed to refresh prediction for transaction ID " + id + ": " + e.getMessage();
        }
    }


}
