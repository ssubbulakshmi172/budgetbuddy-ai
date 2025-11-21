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
import com.budgetbuddy.service.DataCleanupService;
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

    @Autowired
    private DataCleanupService dataCleanupService;
    
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
        logger.info("Entering saveTransaction method");
        logger.debug("Received userId parameter: {}", userId);
        logger.debug("Transaction has narration field");
        long startTime = System.currentTimeMillis();
        try {
            // Handle user binding - convert userId to User object
            if (userId != null) {
                User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));
                transaction.setUser(user);
                logger.debug("Set user for transaction");
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
                logger.debug("Calling categorization for transaction");
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
                    transaction.setPredictedSubcategory(prediction.getPredictedSubcategory());
                    transaction.setPredictedTransactionType(prediction.getTransactionType());
                    transaction.setPredictedIntent(prediction.getIntent());
                    transaction.setPredictionConfidence(prediction.getConfidence());
                    transaction.setPredictionReason(prediction.getReason());
                    long inferenceTime = System.currentTimeMillis() - inferenceStartTime;
                    logger.debug("✅ Prediction completed: category={}, confidence={}, time={}ms", 
                        prediction.getPredictedCategory(), prediction.getConfidence(), inferenceTime);
                } catch (Exception e) {
                    logger.error("❌ Failed to predict category: {}", e.getMessage(), e);
                    transaction.setPredictedCategory("Uncategorized");
                    transaction.setPredictedSubcategory(null);
                }
            } else {
                logger.warn("⚠️ Narration field is empty, skipping categorization");
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
            // Use predicted categories everywhere
            List<String> predictedCategories = transactionService.getDistinctPredictedCategories();
            List<String> predictedSubcategories = transactionService.getDistinctPredictedSubcategories();
            logger.info("Found {} predicted categories and {} predicted subcategories", 
                       predictedCategories != null ? predictedCategories.size() : 0,
                       predictedSubcategories != null ? predictedSubcategories.size() : 0);
            model.addAttribute("predictedCategories", predictedCategories != null ? predictedCategories : new ArrayList<>());
            model.addAttribute("predictedSubcategories", predictedSubcategories != null ? predictedSubcategories : new ArrayList<>());

            model.addAttribute("transactions", new ArrayList<Transaction>());
        } catch (Exception e) {
            logger.error("Error occurred while fetching users for filter form: {}", e.getMessage(), e);
        }
        logger.info("Exiting showFilterTransactionsForm method");
        return "transaction/transaction-filter";
    }

    @GetMapping("/filter")
    public String filterTransactions(@RequestParam(required = false) String month,
                                     @RequestParam(required = false) Integer year,
                                     @RequestParam(required = false) Long userId,
                                     @RequestParam(required = false) String category, // Optional category filter
                                     @RequestParam(required = false) String predictedCategory, // AI predicted category
                                     @RequestParam(required = false) String predictedSubcategory, // AI predicted subcategory
                                     @RequestParam(required = false) Double amountValue,
                                     @RequestParam(required = false) String amountOperator,
                                     @RequestParam(required = false) String narration,
                                     Model model,
                                     RedirectAttributes redirectAttributes) {

        logger.info("Entering filterTransactions method with month: {}, year: {}, userId: {}, category: {}, predictedCategory: {}, predictedSubcategory: {}, amount: {} {}",
                month, year, userId, category, predictedCategory, predictedSubcategory, amountOperator, amountValue);
        List<User> users = userRepository.findAll();
        model.addAttribute("users", users);
        model.addAttribute("predictedCategories", transactionService.getDistinctPredictedCategories());
        model.addAttribute("predictedSubcategories", transactionService.getDistinctPredictedSubcategories());

        try {
            // Handle optional parameters and set default values where necessary
            // Convert month parameter: can be numeric (1-12) or Month enum name (JANUARY, etc.)
            int monthValue = -1;  // Default to -1 for unspecified month
            Month monthEnum = null;
            if (month != null && !month.trim().isEmpty()) {
                try {
                    // Try parsing as integer (1-12)
                    int monthInt = Integer.parseInt(month.trim());
                    if (monthInt >= 1 && monthInt <= 12) {
                        monthValue = monthInt;
                        monthEnum = Month.of(monthInt);
                    }
                } catch (NumberFormatException e) {
                    // Try parsing as Month enum name
                    try {
                        monthEnum = Month.valueOf(month.trim().toUpperCase());
                        monthValue = monthEnum.getValue();
                    } catch (IllegalArgumentException ex) {
                        logger.warn("Invalid month parameter: {}", month);
                    }
                }
            }
            int yearValue = (year != null) ? year : -1;
            // Deprecated: category parameter kept for backward compatibility, but we use predictedCategory everywhere
            String categoryValue = null; // Not used anymore, we use predictedCategory
            // URL decode predictedCategory if needed (Spring should auto-decode, but ensure it's trimmed)
            String predictedCategoryValue = null;
            if (predictedCategory != null && !predictedCategory.trim().isEmpty() && !predictedCategory.trim().equals("ALL")) {
                // Spring auto-decodes URL parameters, but handle any remaining encoding issues
                // Replace + with space (URL encoding for space)
                String decoded = predictedCategory.trim().replace("+", " ");
                // Additional URL decoding if needed (Spring should handle this, but just in case)
                try {
                    decoded = java.net.URLDecoder.decode(decoded, "UTF-8");
                } catch (Exception e) {
                    // If decoding fails, use the original
                    logger.debug("URL decoding failed, using original: {}", e.getMessage());
                }
                // Use exact value from URL - query will handle parentheses removal for matching
                predictedCategoryValue = decoded.trim();
                logger.info("Filtering by predictedCategory: '{}' (original: '{}', URL decoded)", predictedCategoryValue, predictedCategory);
                
                // Debug: Check what categories exist in database
                List<String> allCategories = transactionService.getDistinctPredictedCategories();
                logger.info("Available predicted categories in database (total: {}): {}", allCategories.size(), allCategories);
                
                // Make final reference for lambda
                final String finalPredictedCategory = predictedCategoryValue;
                boolean categoryExists = allCategories.stream()
                    .anyMatch(cat -> cat != null && cat.trim().equalsIgnoreCase(finalPredictedCategory));
                
                if (categoryExists) {
                    // Find the exact match to show what it matched
                    String matchedCategory = allCategories.stream()
                        .filter(cat -> cat != null && cat.trim().equalsIgnoreCase(finalPredictedCategory))
                        .findFirst()
                        .orElse(null);
                    logger.info("✅ Category '{}' MATCHED with database category '{}' (case-insensitive)", predictedCategoryValue, matchedCategory);
                } else {
                    logger.warn("❌ Category '{}' NOT FOUND in database. Available categories: {}", predictedCategoryValue, allCategories);
                    // Try to find similar categories
                    List<String> similarCategories = allCategories.stream()
                        .filter(cat -> cat != null && 
                                (cat.toLowerCase().contains(finalPredictedCategory.toLowerCase()) || 
                                 finalPredictedCategory.toLowerCase().contains(cat.toLowerCase())))
                        .collect(java.util.stream.Collectors.toList());
                    if (!similarCategories.isEmpty()) {
                        logger.info("💡 Similar categories found: {}", similarCategories);
                    }
                }
            }
            String predictedSubcategoryValue = (predictedSubcategory != null && !predictedSubcategory.trim().isEmpty() && !predictedSubcategory.trim().equals("ALL")) ? predictedSubcategory.trim() : null;
            
            // Validate and sanitize amount operator
            String validAmountOperator = null;
            if (amountOperator != null && !amountOperator.isEmpty()) {
                if (amountOperator.equals("gt") || amountOperator.equals("lt") || 
                    amountOperator.equals("gte") || amountOperator.equals("lte") || 
                    amountOperator.equals("eq")) {
                    validAmountOperator = amountOperator;
                }
            }
            
            // Sanitize narration (trim whitespace and URL decode)
            String narrationValue = null;
            if (narration != null && !narration.trim().isEmpty()) {
                // Spring auto-decodes URL parameters, but handle any remaining encoding issues
                String decoded = narration.trim().replace("+", " ");
                try {
                    decoded = java.net.URLDecoder.decode(decoded, "UTF-8");
                } catch (Exception e) {
                    logger.debug("URL decoding failed for narration, using original: {}", e.getMessage());
                }
                narrationValue = decoded.trim();
                logger.info("Filtering by narration: '{}' (original: '{}', URL decoded)", narrationValue, narration);
            }

            // Log the adjusted values
            logger.debug("Processed month: {}, year: {}, amount: {} {}, narration: {}",
                    monthValue, yearValue, validAmountOperator, amountValue, narrationValue != null ? "[FILTERED]" : "null");
            
            // Call the service to filter transactions
            List<Transaction> transactions = transactionService.filterTransactions(
                    monthValue, yearValue, userId, categoryValue,
                    predictedCategoryValue, predictedSubcategoryValue,
                    amountValue, validAmountOperator, narrationValue);

            // Add results to the model for view rendering
            model.addAttribute("transactions", transactions);
            model.addAttribute("month", monthEnum);
            model.addAttribute("year", year);
            model.addAttribute("userId", userId);
            // Using predictedCategory everywhere, categoryKeyword is deprecated
            model.addAttribute("predictedCategory", predictedCategory);
            model.addAttribute("predictedSubcategory", predictedSubcategory);
            model.addAttribute("amountValue", amountValue);
            model.addAttribute("amountOperator", amountOperator);
            model.addAttribute("narration", narration);
            // Add predictedCategories for dropdown in results table
            model.addAttribute("predictedCategories", transactionService.getDistinctPredictedCategories());
            long transactionCount = transactions.size();
            double totalAmount = transactions.stream().mapToDouble(Transaction::getAmount).sum();
            model.addAttribute("transactionCount", transactionCount);
            model.addAttribute("totalAmount", totalAmount);


            // Log the number of transactions filtered and any relevant details
            if (transactions.isEmpty()) {
                logger.info("No transactions found for the given filters.");
            } else {
                logger.debug("Filtered {} transactions for month: {}, year: {}, userId: {}, category: {}, amount: {} {}",
                        transactions.size(), month, year, userId, category, amountOperator, amountValue);
            }

        } catch (Exception e) {
            // Log the error with exception details for debugging
            logger.error("Error occurred while filtering transactions: month={}, year={}, userId={}, category={}, amount={} {}. Exception: {}",
                    month, year, userId, category, amountOperator, amountValue, e.getMessage(), e);
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
    
    /**
     * Get all categories from categories.yml for dropdown
     * Uses Python script to parse YAML properly
     */
    @GetMapping("/categories-list")
    @ResponseBody
    public ResponseEntity<List<String>> getCategoriesList() {
        try {
            List<String> categories = new ArrayList<>();
            String projectRoot = System.getProperty("user.dir");
            String scriptPath = "mybudget-ai/get_categories.py";
            if (!new java.io.File(scriptPath).isAbsolute()) {
                scriptPath = new java.io.File(projectRoot, scriptPath).getAbsolutePath();
            }
            
            // Call Python script to parse YAML
            String[] command = {"python3", "-u", scriptPath};
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(new java.io.File(projectRoot));
            Process process = pb.start();
            
            // Read output (JSON array of categories)
            StringBuilder output = new StringBuilder();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode == 0 && output.length() > 0) {
                // Parse JSON array
                ObjectMapper mapper = new ObjectMapper();
                categories = mapper.readValue(output.toString(), 
                    mapper.getTypeFactory().constructCollectionType(List.class, String.class));
                java.util.Collections.sort(categories);
                logger.info("Loaded {} categories from YAML", categories.size());
                return ResponseEntity.ok(categories);
            } else {
                logger.warn("Failed to load categories from YAML. Exit code: {}", exitCode);
                return ResponseEntity.ok(categories);
            }
        } catch (Exception e) {
            logger.error("Error getting categories list: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(new ArrayList<>());
        }
    }
    
    /**
     * Add a correction for a transaction's predicted category.
     * Saves the correction to user_corrections.json for offline mode.
     */
    @PostMapping("/add-correction")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> addCorrection(
            @RequestParam Long transactionId,
            @RequestParam String category) {
        try {
            logger.info("Adding correction for transaction ID: {} with category: {}", transactionId, category);
            
            Transaction transaction = transactionService.getTransactionById(transactionId);
            if (transaction == null) {
                return ResponseEntity.status(404).body(Map.of("success", false, "message", "Transaction not found"));
            }
            
            String narration = transaction.getNarration();
            
            if (narration == null || narration.trim().isEmpty()) {
                return ResponseEntity.status(400).body(Map.of("success", false, "message", "Transaction narration is empty"));
            }
            
            // Get user ID and transaction ID for Efficient Mode cloud sync
            Long userId = transaction.getUser() != null ? transaction.getUser().getId() : null;
            Long txnId = transaction.getId();
            
            // Add correction to user_corrections.json file (for model retraining)
            // This writes to mybudget-ai/user_corrections.json
            logger.info("📝 Adding correction to user_corrections.json: '{}' -> '{}'", narration.substring(0, Math.min(50, narration.length())), category);
            boolean success = transactionService.addCorrection(narration, category, userId, txnId);
            
            if (success) {
                // Update predicted category (UI will show this, categoryName is hidden)
                transaction.setPredictedCategory(category);
                transaction.setCategoryName(null);  // Clear categoryName - UI shows predictedCategory instead
                transactionService.saveTransaction(transaction);
                
                logger.info("✅ Correction saved to user_corrections.json and transaction updated in database");
                logger.info("   JSON file: mybudget-ai/user_corrections.json");
                logger.info("   Transaction ID: {}, Category: {}", txnId, category);
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "✅ Correction saved! The transaction category has been updated and saved to user_corrections.json for model retraining.",
                    "updatedCategory", category,
                    "transactionId", txnId
                ));
            } else {
                return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "❌ Failed to save correction. Please try again."
                ));
            }
        } catch (Exception e) {
            logger.error("❌ Error adding correction: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "❌ Error: " + e.getMessage()
            ));
        }
    }


    // 🔁 Refresh all predictions
    @PostMapping("/refresh-categories")
    public ResponseEntity<Map<String, String>> refreshAllCategories() {
        try {
            logger.info("🔄 Refresh categories endpoint called");
            categorizationService.refreshPredictionsForAll();
            logger.info("✅ Refresh categories completed successfully");
            return ResponseEntity.ok(Map.of("message", "✅ All transaction categories refreshed successfully!"));
        } catch (Exception e) {
            logger.error("❌ Error refreshing categories: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("message", "❌ Error refreshing categories: " + e.getMessage()));
        }
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

    /**
     * Clear all transaction data and financial guidance data
     * WARNING: This will permanently delete all data
     */
    @PostMapping("/clear-all-data")
    @ResponseBody
    public ResponseEntity<Map<String, String>> clearAllData() {
        try {
            User currentUser = userService.getCurrentUser();
            if (currentUser == null) {
                return ResponseEntity.status(401).body(Map.of("error", "User not authenticated"));
            }
            
            logger.warn("⚠️ User {} requested to clear all transaction and guidance data", currentUser.getName());
            dataCleanupService.clearAllTransactionAndGuidanceData(currentUser);
            
            return ResponseEntity.ok(Map.of(
                "message", "✅ Successfully cleared all transaction and financial guidance data",
                "status", "success"
            ));
        } catch (Exception e) {
            logger.error("❌ Error clearing data: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to clear data: " + e.getMessage(),
                "status", "error"
            ));
        }
    }

}
