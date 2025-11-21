package com.budgetbuddy.service;

import com.budgetbuddy.model.MoneyLeak;
import com.budgetbuddy.model.Transaction;
import com.budgetbuddy.model.User;
import com.budgetbuddy.repository.MoneyLeakRepository;
import com.budgetbuddy.repository.TransactionRepository;
import com.budgetbuddy.util.NarrationPreprocessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.json.JSONArray;
import org.json.JSONObject;

@Service
public class MoneyLeakService {

    private static final Logger logger = LoggerFactory.getLogger(MoneyLeakService.class);

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private MoneyLeakRepository moneyLeakRepository;

    @Value("${python.command:mybudget-ai/venv/bin/python3}")
    private String pythonCommand;

    @Value("${python.inference.timeout:30}")
    private int timeoutSeconds;

    private static final double SMALL_TRANSACTION_THRESHOLD = 200.0; // ₹200
    private static final int MIN_SUBSCRIPTION_OCCURRENCES = 3;
    private static final int MIN_COFFEE_EFFECT_TRANSACTIONS = 10;

    /**
     * Check if a transaction category is an investment (should be excluded from expense calculations).
     * 
     * Investments are not considered expenses because they represent asset allocation,
     * not consumption. Based on categories.yml, the "Investments" category includes:
     * - Stocks & Bonds
     * - Investments & Savings (SIP, FD, Mutual Funds, etc.)
     * - Loans & EMI (loan repayments are debt reduction, not expenses)
     * - Banking & Payments (transaction fees, but these are minimal)
     * 
     * @param category Category name (can be "Investments" or "Investments / Subcategory")
     * @return true if category is an investment, false otherwise
     */
    private boolean isInvestmentCategory(String category) {
        if (category == null || category.trim().isEmpty()) {
            return false;
        }
        
        // Normalize: lowercase and trim whitespace
        String normalized = category.toLowerCase().trim();
        
        // Check if category is exactly "Investments" or starts with "Investments /"
        // This covers all subcategories: "Investments / Stocks & Bonds", etc.
        return normalized.equals("investments") || normalized.startsWith("investments /");
    }
    
    /**
     * Check if a transaction category is salary/income (should be excluded from money leak detection).
     * 
     * Salary/Income transactions are not expenses - they represent money coming in.
     * Based on categories.yml, the "Salary" category includes:
     * - Salary & Income (wages, pay, paycheck, income, compensation, etc.)
     * 
     * @param category Category name (can be "Salary" or "Salary / Subcategory")
     * @return true if category is salary/income, false otherwise
     */
    private boolean isSalaryCategory(String category) {
        if (category == null || category.trim().isEmpty()) {
            return false;
        }
        
        // Normalize: lowercase and trim whitespace
        String normalized = category.toLowerCase().trim();
        
        // Check if category is exactly "Salary" or starts with "Salary /"
        // This covers subcategories: "Salary / Salary & Income", etc.
        return normalized.equals("salary") || normalized.startsWith("salary /");
    }
    
    /**
     * Check if a transaction should be excluded from expense calculations.
     * A transaction is excluded if either its categoryName or predictedCategory is an investment.
     * 
     * @param transaction Transaction to check
     * @return true if transaction should be excluded (is an investment), false otherwise
     */
    private boolean isInvestmentTransaction(Transaction transaction) {
        return isInvestmentCategory(transaction.getCategoryName()) || 
               isInvestmentCategory(transaction.getPredictedCategory());
    }
    
    /**
     * Check if a transaction is income/salary (should be excluded from money leak detection).
     * Income transactions have positive amounts or contain salary/credit keywords in narration.
     * 
     * @param transaction Transaction to check
     * @return true if transaction is income/salary, false otherwise
     */
    private boolean isIncomeTransaction(Transaction transaction) {
        // Positive amounts are deposits/income (following amount convention)
        if (transaction.getAmount() != null && transaction.getAmount() > 0) {
            return true;
        }
        
        // Check narration for income keywords (case-insensitive)
        // This handles cases where salary might be incorrectly stored as negative
        String narration = transaction.getNarration();
        if (narration != null && !narration.trim().isEmpty()) {
            String narrationUpper = narration.toUpperCase().trim();
            
            // Common income/salary patterns
            // Match "SALARY CREDIT", "SALARY CREDIT SALARY", "SALARY DEPOSIT", etc.
            if (narrationUpper.contains("SALARY") && 
                (narrationUpper.contains("CREDIT") || 
                 narrationUpper.contains("DEPOSIT") ||
                 narrationUpper.contains("SALARY CREDIT") ||
                 narrationUpper.matches(".*SALARY.*CREDIT.*"))) {
                return true;
            }
            
            // Match standalone "SALARY" if it's a credit/deposit pattern
            if (narrationUpper.equals("SALARY") || 
                narrationUpper.startsWith("SALARY ") ||
                narrationUpper.contains(" SALARY")) {
                // Additional check: if it contains credit/deposit keywords
                if (narrationUpper.contains("CREDIT") || 
                    narrationUpper.contains("DEPOSIT") ||
                    narrationUpper.contains("INCOME")) {
                    return true;
                }
            }
            
            // Match income-related keywords
            if (narrationUpper.contains("INCOME") ||
                (narrationUpper.contains("DEPOSIT") && narrationUpper.contains("SAL"))) {
                return true;
            }
        }
        
        // Check category for salary/income (similar to investment category check)
        String category = transaction.getCategoryName() != null ? transaction.getCategoryName() : 
                         (transaction.getPredictedCategory() != null ? transaction.getPredictedCategory() : "");
        if (isSalaryCategory(category)) {
            return true;
        }
        
        // Also check for income-related keywords in category (fallback)
        if (category != null && !category.trim().isEmpty()) {
            String categoryUpper = category.toUpperCase().trim();
            if (categoryUpper.contains("INCOME") && !categoryUpper.contains("EXPENSE")) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Detect top 3 money leaks for a user (excluding investments)
     * Rule-Based Aggregation: Sum all Money Leak transactions and rank by total amount
     */
    public List<MoneyLeak> detectMoneyLeaks(User user) {
        logger.info("Detecting top 3 money leaks for user: {} (excluding investments)", user.getId());

        List<MoneyLeak> leaks = new ArrayList<>();

        // 1. Detect repeating subscriptions (non-investment, exclude income)
        leaks.addAll(detectRepeatingSubscriptions(user));

        // 2. Detect coffee-effect (small frequent purchases)
        leaks.addAll(detectCoffeeEffect(user));

        // 3. Detect ATM withdrawal spikes
        leaks.addAll(detectATMWithdrawalSpikes(user));

        // 4. Detect friend-covering / one-sided sharing leaks
        leaks.addAll(detectFriendCoveringLeaks(user));

        // 5. Detect high-impact one-time payments
        leaks.addAll(detectHighImpactOneTime(user));

        // 6. Detect emotional / late-night spending
        leaks.addAll(detectEmotionalLateNightSpending(user));

        // Rule-Based Aggregation: Sum all leaks and rank by total amount
        // Group by leak type and sum amounts
        Map<String, MoneyLeak> aggregatedLeaks = new HashMap<>();
        
        for (MoneyLeak leak : leaks) {
            String key = leak.getLeakType().name() + "|" + 
                        (leak.getMerchantPattern() != null ? leak.getMerchantPattern() : "UNKNOWN");
            
            if (aggregatedLeaks.containsKey(key)) {
                MoneyLeak existing = aggregatedLeaks.get(key);
                // Aggregate amounts
                existing.setMonthlyAmount(existing.getMonthlyAmount() + leak.getMonthlyAmount());
                existing.setAnnualAmount(existing.getAnnualAmount() + leak.getAnnualAmount());
                existing.setTransactionCount(existing.getTransactionCount() + (leak.getTransactionCount() != null ? leak.getTransactionCount() : 0));
            } else {
                aggregatedLeaks.put(key, leak);
            }
        }

        // Convert to list and sort by total annual amount (descending)
        List<MoneyLeak> rankedLeaks = new ArrayList<>(aggregatedLeaks.values());
        rankedLeaks.sort((a, b) -> Double.compare(b.getAnnualAmount(), a.getAnnualAmount()));
        
        // Deactivate old leaks
        List<MoneyLeak> oldLeaks = moneyLeakRepository.findByUserAndIsActiveTrueOrderByAnnualAmountDesc(user);
        for (MoneyLeak oldLeak : oldLeaks) {
            oldLeak.setIsActive(false);
            moneyLeakRepository.save(oldLeak);
        }

        // Assign ranks and save top 3
        int rank = 1;
        for (MoneyLeak leak : rankedLeaks) {
            if (rank <= 3) {
                leak.setRank(rank);
                leak.setIsActive(true);
                moneyLeakRepository.save(leak);
                rank++;
            }
        }

        logger.info("Detected {} aggregated money leaks, top 3 ranked for user {}", rankedLeaks.size(), user.getId());
        return rankedLeaks.stream()
            .filter(l -> l.getRank() != null && l.getRank() <= 3)
            .collect(Collectors.toList());
    }

    /**
     * Detect regular monthly spending patterns (including investments).
     * This shows all recurring monthly expenses and investments separately.
     * 
     * @param user User to analyze
     * @return List of regular monthly spending patterns (both expenses and investments)
     */
    public List<MoneyLeak> detectRegularMonthlySpending(User user) {
        logger.info("Detecting regular monthly spending for user: {} (including investments)", user.getId());

        List<MoneyLeak> regularSpending = new ArrayList<>();
        LocalDate sixMonthsAgo = LocalDate.now().minusMonths(6);

        // Get all transactions (including investments, but exclude income)
        List<Transaction> allTransactions = transactionRepository.findAll()
            .stream()
            .filter(t -> t.getUser().getId().equals(user.getId()))
            .filter(t -> t.getAmount() != null && t.getAmount() < 0)
            .filter(t -> t.getDate().isAfter(sixMonthsAgo))
            .filter(t -> !isIncomeTransaction(t))  // Exclude income/salary transactions
            .collect(Collectors.toList());

        // Group by merchant pattern and amount (for recurring payments)
        Map<String, List<Transaction>> grouped = allTransactions.stream()
            .collect(Collectors.groupingBy(t -> {
                String merchant = extractMerchantPattern(t.getNarration());
                double amount = Math.abs(t.getAmount());
                String category = t.getCategoryName() != null ? t.getCategoryName() : 
                                 (t.getPredictedCategory() != null ? t.getPredictedCategory() : "Unknown");
                return category + "|" + merchant + "|" + String.format("%.2f", amount);
            }));

        for (Map.Entry<String, List<Transaction>> entry : grouped.entrySet()) {
            List<Transaction> txs = entry.getValue();
            
            if (txs.size() < MIN_SUBSCRIPTION_OCCURRENCES) {
                continue;
            }

            // Check if recurring monthly (within ±3 days)
            if (isRecurringMonthly(txs)) {
                String[] parts = entry.getKey().split("\\|");
                String category = parts[0];
                String merchant = parts.length > 1 ? parts[1] : "Unknown";
                double amount = parts.length > 2 ? Double.parseDouble(parts[2]) : 
                               txs.stream().mapToDouble(t -> Math.abs(t.getAmount())).average().orElse(0.0);

                double monthlyAmount = amount;
                double annualAmount = monthlyAmount * 12;

                MoneyLeak regularSpend = new MoneyLeak();
                regularSpend.setUser(user);
                regularSpend.setLeakType(isInvestmentCategory(category) ? 
                    MoneyLeak.LeakType.REPEATING_SUBSCRIPTION : MoneyLeak.LeakType.REPEATING_SUBSCRIPTION);
                regularSpend.setTitle(isInvestmentCategory(category) ? 
                    "Monthly Investment: " + merchant : "Monthly Expense: " + merchant);
                regularSpend.setDescription(String.format(
                    "You spend ₹%.0f monthly on %s (%s). This adds up to ₹%.0f per year.",
                    monthlyAmount, merchant, category, annualAmount
                ));
                regularSpend.setMerchantPattern(merchant);
                regularSpend.setMonthlyAmount(monthlyAmount);
                regularSpend.setAnnualAmount(annualAmount);
                regularSpend.setTransactionCount(txs.size());
                regularSpend.setAverageTransactionAmount(amount);
                regularSpend.setSuggestion(isInvestmentCategory(category) ? 
                    "This is a regular investment. Consider reviewing if it aligns with your financial goals." :
                    "This is a recurring expense. Review if this subscription/service is still needed.");
                regularSpend.setIsActive(true);
                regularSpend.setRank(null); // No ranking for regular spending list

                regularSpending.add(regularSpend);
            }
        }

        // Sort by monthly amount (descending)
        regularSpending.sort((a, b) -> Double.compare(b.getMonthlyAmount(), a.getMonthlyAmount()));

        logger.info("Detected {} regular monthly spending patterns for user {}", regularSpending.size(), user.getId());
        return regularSpending;
    }

    /**
     * Detect repeating subscriptions (same merchant/amount recurring monthly)
     */
    private List<MoneyLeak> detectRepeatingSubscriptions(User user) {
        List<MoneyLeak> leaks = new ArrayList<>();

        LocalDate sixMonthsAgo = LocalDate.now().minusMonths(6);
        List<Transaction> transactions = transactionRepository.findAll()
            .stream()
            .filter(t -> t.getUser().getId().equals(user.getId()))
            .filter(t -> t.getAmount() != null && t.getAmount() < 0)
            .filter(t -> t.getDate().isAfter(sixMonthsAgo))
            .filter(t -> !isInvestmentTransaction(t))
            .filter(t -> !isIncomeTransaction(t))  // Exclude income/salary transactions
            .collect(Collectors.toList());

        // Group by merchant pattern and amount
        Map<String, List<Transaction>> grouped = transactions.stream()
            .collect(Collectors.groupingBy(t -> {
                String merchant = extractMerchantPattern(t.getNarration());
                double amount = Math.abs(t.getAmount());
                return merchant + "|" + String.format("%.2f", amount);
            }));

        for (Map.Entry<String, List<Transaction>> entry : grouped.entrySet()) {
            List<Transaction> txs = entry.getValue();
            
            if (txs.size() < MIN_SUBSCRIPTION_OCCURRENCES) {
                continue;
            }

            // Check if recurring monthly (within ±3 days)
            if (isRecurringMonthly(txs)) {
                String[] parts = entry.getKey().split("\\|");
                String merchant = parts[0];
                double amount = Double.parseDouble(parts[1]);

                double monthlyAmount = amount;
                double annualAmount = monthlyAmount * 12;

                MoneyLeak leak = new MoneyLeak();
                leak.setUser(user);
                leak.setLeakType(MoneyLeak.LeakType.REPEATING_SUBSCRIPTION);
                leak.setTitle("Recurring Subscription: " + merchant);
                leak.setDescription(String.format(
                    "You pay ₹%.0f monthly to %s. This adds up to ₹%.0f per year.",
                    monthlyAmount, merchant, annualAmount
                ));
                leak.setMerchantPattern(merchant);
                leak.setMonthlyAmount(monthlyAmount);
                leak.setAnnualAmount(annualAmount);
                leak.setTransactionCount(txs.size());
                leak.setAverageTransactionAmount(amount);
                leak.setSuggestion("Review if this subscription is still needed. Consider canceling unused services.");

                leaks.add(leak);
            }
        }

        return leaks;
    }

    /**
     * Detect coffee-effect: small frequent purchases (< ₹200) at same merchant cluster
     */
    private List<MoneyLeak> detectCoffeeEffect(User user) {
        List<MoneyLeak> leaks = new ArrayList<>();

        LocalDate oneMonthAgo = LocalDate.now().minusMonths(1);
        List<Transaction> transactions = transactionRepository.findAll()
            .stream()
            .filter(t -> t.getUser().getId().equals(user.getId()))
            .filter(t -> t.getAmount() != null && t.getAmount() < 0)
            .filter(t -> Math.abs(t.getAmount()) < SMALL_TRANSACTION_THRESHOLD)
            .filter(t -> t.getDate().isAfter(oneMonthAgo))
            .filter(t -> !isInvestmentTransaction(t))
            .filter(t -> !isIncomeTransaction(t))  // Exclude income/salary transactions
            .collect(Collectors.toList());

        // Group by merchant pattern (similar merchant names)
        Map<String, List<Transaction>> grouped = transactions.stream()
            .collect(Collectors.groupingBy(t -> extractMerchantPattern(t.getNarration())));

        for (Map.Entry<String, List<Transaction>> entry : grouped.entrySet()) {
            String merchantPattern = entry.getKey();
            List<Transaction> txs = entry.getValue();

            if (txs.size() < MIN_COFFEE_EFFECT_TRANSACTIONS) {
                continue;
            }

            double monthlyTotal = txs.stream()
                .mapToDouble(t -> Math.abs(t.getAmount()))
                .sum();

            double annualAmount = monthlyTotal * 12;
            double avgAmount = monthlyTotal / txs.size();

            MoneyLeak leak = new MoneyLeak();
            leak.setUser(user);
            leak.setLeakType(MoneyLeak.LeakType.COFFEE_EFFECT);
            leak.setTitle("Small Frequent Purchases: " + merchantPattern);
            leak.setDescription(String.format(
                "You make %d small purchases (avg ₹%.0f) at %s per month. " +
                "This adds up to ₹%.0f per month (₹%.0f per year).",
                txs.size(), avgAmount, merchantPattern, monthlyTotal, annualAmount
            ));
            leak.setMerchantPattern(merchantPattern);
            leak.setMonthlyAmount(monthlyTotal);
            leak.setAnnualAmount(annualAmount);
            leak.setTransactionCount(txs.size());
            leak.setAverageTransactionAmount(avgAmount);
            leak.setSuggestion("Consider reducing frequency or finding cheaper alternatives. Small amounts add up quickly!");

            leaks.add(leak);
        }

        return leaks;
    }

    /**
     * Detect unusually high ATM withdrawals
     */
    private List<MoneyLeak> detectATMWithdrawalSpikes(User user) {
        List<MoneyLeak> leaks = new ArrayList<>();

        LocalDate sixMonthsAgo = LocalDate.now().minusMonths(6);
        List<Transaction> transactions = transactionRepository.findAll()
            .stream()
            .filter(t -> t.getUser().getId().equals(user.getId()))
            .filter(t -> t.getNarration() != null)
            .filter(t -> t.getNarration().toUpperCase().contains("ATM") || 
                        t.getNarration().toUpperCase().contains("CASH") ||
                        t.getWithdrawalAmt() != null)
            .filter(t -> t.getDate().isAfter(sixMonthsAgo))
            .filter(t -> !isInvestmentTransaction(t))
            .filter(t -> !isIncomeTransaction(t))  // Exclude income/salary transactions
            .collect(Collectors.toList());

        if (transactions.isEmpty()) {
            return leaks;
        }

        // Group by month
        Map<YearMonth, List<Transaction>> byMonth = transactions.stream()
            .collect(Collectors.groupingBy(t -> YearMonth.from(t.getDate())));

        // Calculate historical average
        List<Double> monthlyTotals = byMonth.values().stream()
            .map(txs -> txs.stream()
                .mapToDouble(t -> Math.abs(t.getWithdrawalAmt() != null ? t.getWithdrawalAmt() : Math.abs(t.getAmount())))
                .sum())
            .collect(Collectors.toList());

        if (monthlyTotals.size() < 3) {
            return leaks; // Need at least 3 months
        }

        double historicalAvg = monthlyTotals.stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.0);

        // Check current month
        YearMonth currentMonth = YearMonth.now();
        List<Transaction> currentMonthTxs = byMonth.getOrDefault(currentMonth, Collections.emptyList());
        double currentMonthTotal = currentMonthTxs.stream()
            .mapToDouble(t -> Math.abs(t.getWithdrawalAmt() != null ? t.getWithdrawalAmt() : Math.abs(t.getAmount())))
            .sum();

        // Check frequency
        int currentMonthCount = currentMonthTxs.size();
        double avgFrequency = byMonth.values().stream()
            .mapToInt(List::size)
            .average()
            .orElse(0.0);

        // Flag if spike detected
        if (currentMonthTotal > historicalAvg * 1.5 || currentMonthCount > avgFrequency * 2) {
            double extraWithdrawal = currentMonthTotal - historicalAvg;
            double annualAmount = currentMonthTotal * 12;

            MoneyLeak leak = new MoneyLeak();
            leak.setUser(user);
            leak.setLeakType(MoneyLeak.LeakType.ATM_WITHDRAWAL_SPIKE);
            leak.setTitle("Unusually High ATM Withdrawals");
            leak.setDescription(String.format(
                "You withdrew ₹%.0f this month (avg: ₹%.0f). " +
                "That's ₹%.0f extra. Annual projection: ₹%.0f",
                currentMonthTotal, historicalAvg, extraWithdrawal, annualAmount
            ));
            leak.setMerchantPattern("ATM");
            leak.setMonthlyAmount(currentMonthTotal);
            leak.setAnnualAmount(annualAmount);
            leak.setTransactionCount(currentMonthCount);
            leak.setAverageTransactionAmount(currentMonthTotal / Math.max(currentMonthCount, 1));
            leak.setSuggestion("Track where cash is being spent. Consider using digital payments for better tracking.");

            leaks.add(leak);
        }

        return leaks;
    }

    /**
     * Check if transactions recur monthly (within ±3 days)
     */
    private boolean isRecurringMonthly(List<Transaction> transactions) {
        if (transactions.size() < MIN_SUBSCRIPTION_OCCURRENCES) {
            return false;
        }

        // Sort by date
        transactions.sort(Comparator.comparing(Transaction::getDate));

        // Check intervals between transactions
        for (int i = 1; i < transactions.size(); i++) {
            long daysBetween = ChronoUnit.DAYS.between(
                transactions.get(i - 1).getDate(),
                transactions.get(i).getDate()
            );

            // Should be approximately 30 days (±3 days)
            if (daysBetween < 27 || daysBetween > 33) {
                return false;
            }
        }

        return true;
    }

    /**
     * Detect friend-covering / one-sided sharing leaks
     * Rule: Sum all Friend-Sharing transactions where user covers >50% of the group cost
     */
    private List<MoneyLeak> detectFriendCoveringLeaks(User user) {
        List<MoneyLeak> leaks = new ArrayList<>();
        
        LocalDate sixMonthsAgo = LocalDate.now().minusMonths(6);
        List<Transaction> transactions = transactionRepository.findAll()
            .stream()
            .filter(t -> t.getUser().getId().equals(user.getId()))
            .filter(t -> t.getAmount() != null && t.getAmount() < 0)
            .filter(t -> t.getDate().isAfter(sixMonthsAgo))
            .filter(t -> !isInvestmentTransaction(t))
            .filter(t -> !isIncomeTransaction(t))  // Exclude income/salary transactions
            .collect(Collectors.toList());

        // Detect P2P transactions that look like group expenses
        List<Transaction> friendSharingTxs = transactions.stream()
            .filter(t -> {
                String narration = t.getNarration() != null ? t.getNarration().toLowerCase() : "";
                return narration.contains("friend") || narration.contains("group") || 
                       narration.contains("shared") || narration.contains("split") ||
                       narration.contains("dinner") || narration.contains("lunch") ||
                       narration.contains("outing") || narration.contains("hangout") ||
                       (t.getPredictedTransactionType() != null && 
                        t.getPredictedTransactionType().equals("P2P"));
            })
            .collect(Collectors.toList());

        if (friendSharingTxs.isEmpty()) {
            return leaks;
        }

        // Group by date (same day = likely same event)
        Map<LocalDate, List<Transaction>> byDate = friendSharingTxs.stream()
            .collect(Collectors.groupingBy(Transaction::getDate));

        double totalOneSided = 0.0;
        int incidentCount = 0;
        List<Transaction> oneSidedTxs = new ArrayList<>();

        for (Map.Entry<LocalDate, List<Transaction>> entry : byDate.entrySet()) {
            List<Transaction> dayTxs = entry.getValue();
            double dayTotal = dayTxs.stream().mapToDouble(t -> Math.abs(t.getAmount())).sum();
            
            // Heuristic: If amount > ₹500 on a single day with friend-related narration,
            // likely covering group expense (restaurant bill, cab, etc.)
            if (dayTotal > 500.0 && dayTxs.size() >= 1) {
                oneSidedTxs.addAll(dayTxs);
                totalOneSided += dayTotal;
                incidentCount++;
            }
        }

        if (incidentCount > 0) {
            double avgPerIncident = totalOneSided / incidentCount;
            double monthlyAmount = totalOneSided / 6.0; // Average over 6 months
            double annualAmount = monthlyAmount * 12;

            MoneyLeak leak = new MoneyLeak();
            leak.setUser(user);
            leak.setLeakType(MoneyLeak.LeakType.REPEATING_SUBSCRIPTION); // Reuse type
            leak.setTitle("Friend-Covering / One-Sided Sharing");
            leak.setDescription(String.format(
                "You covered ₹%.0f in group expenses (%d incidents, avg ₹%.0f per incident). " +
                "This suggests you're paying more than your share.",
                totalOneSided, incidentCount, avgPerIncident
            ));
            // Use a category-like pattern that can be filtered by predictedCategory
            // The actual category might be "Social / Friends & Social Expenses" or similar
            leak.setMerchantPattern("Social / Friends & Social Expenses");
            leak.setMonthlyAmount(monthlyAmount);
            leak.setAnnualAmount(annualAmount);
            leak.setTransactionCount(oneSidedTxs.size());
            leak.setAverageTransactionAmount(avgPerIncident);
            leak.setSuggestion("Consider using split-bill apps or asking friends to pay their share upfront.");
            leak.setIsActive(true);
            leak.setRank(null);

            leaks.add(leak);
        }

        return leaks;
    }

    /**
     * Detect high-impact one-time payments affecting cash flow
     * Rule: Identify large transactions (>₹5000) that are not recurring
     */
    private List<MoneyLeak> detectHighImpactOneTime(User user) {
        List<MoneyLeak> leaks = new ArrayList<>();
        
        LocalDate threeMonthsAgo = LocalDate.now().minusMonths(3);
        List<Transaction> transactions = transactionRepository.findAll()
            .stream()
            .filter(t -> t.getUser().getId().equals(user.getId()))
            .filter(t -> t.getAmount() != null && t.getAmount() < 0)
            .filter(t -> t.getDate().isAfter(threeMonthsAgo))
            .filter(t -> !isInvestmentTransaction(t))
            .filter(t -> !isIncomeTransaction(t))  // Exclude income/salary transactions
            .filter(t -> Math.abs(t.getAmount()) > 5000.0) // Large payments
            .collect(Collectors.toList());

        // Filter out recurring payments (same merchant/amount appearing multiple times)
        Map<String, List<Transaction>> byMerchantAmount = transactions.stream()
            .collect(Collectors.groupingBy(t -> {
                String merchant = extractMerchantPattern(t.getNarration());
                double amount = Math.abs(t.getAmount());
                return merchant + "|" + String.format("%.0f", amount);
            }));

        for (Map.Entry<String, List<Transaction>> entry : byMerchantAmount.entrySet()) {
            List<Transaction> txs = entry.getValue();
            
            // If appears only once or twice, it's likely a one-time payment
            if (txs.size() <= 2) {
                double totalAmount = txs.stream().mapToDouble(t -> Math.abs(t.getAmount())).sum();
                String merchant = extractMerchantPattern(txs.get(0).getNarration());

                MoneyLeak leak = new MoneyLeak();
                leak.setUser(user);
                leak.setLeakType(MoneyLeak.LeakType.REPEATING_SUBSCRIPTION); // Reuse type
                leak.setTitle("High-Impact One-Time: " + merchant);
                leak.setDescription(String.format(
                    "One-time payment of ₹%.0f to %s. Large payments can affect cash flow.",
                    totalAmount, merchant
                ));
                leak.setMerchantPattern(merchant);
                leak.setMonthlyAmount(totalAmount);
                leak.setAnnualAmount(totalAmount);
                leak.setTransactionCount(txs.size());
                leak.setAverageTransactionAmount(totalAmount);
                leak.setSuggestion("Plan for large one-time expenses in advance. Consider breaking into installments if possible.");
                leak.setIsActive(true);
                leak.setRank(null);

                leaks.add(leak);
            }
        }

        return leaks;
    }

    /**
     * Detect emotional / late-night spending
     * Rule: Filter transactions with impulse indicators (late-night, multiple purchases in short time)
     * Note: We don't have exact timestamps, so we use heuristics based on narration and patterns
     */
    private List<MoneyLeak> detectEmotionalLateNightSpending(User user) {
        List<MoneyLeak> leaks = new ArrayList<>();
        
        LocalDate oneMonthAgo = LocalDate.now().minusMonths(1);
        List<Transaction> transactions = transactionRepository.findAll()
            .stream()
            .filter(t -> t.getUser().getId().equals(user.getId()))
            .filter(t -> t.getAmount() != null && t.getAmount() < 0)
            .filter(t -> t.getDate().isAfter(oneMonthAgo))
            .filter(t -> !isInvestmentTransaction(t))
            .filter(t -> !isIncomeTransaction(t))  // Exclude income/salary transactions
            .collect(Collectors.toList());

        // Detect impulse buy patterns:
        // 1. Multiple small purchases on same day (likely impulse)
        // 2. Food/dining transactions (common late-night/emotional spending)
        Map<LocalDate, List<Transaction>> byDate = transactions.stream()
            .filter(t -> {
                String category = t.getCategoryName() != null ? t.getCategoryName() : 
                                (t.getPredictedCategory() != null ? t.getPredictedCategory() : "");
                String narration = t.getNarration() != null ? t.getNarration().toLowerCase() : "";
                return category.toLowerCase().contains("dining") || 
                       category.toLowerCase().contains("food") ||
                       narration.contains("food") || narration.contains("restaurant") ||
                       narration.contains("cafe") || narration.contains("coffee");
            })
            .collect(Collectors.groupingBy(Transaction::getDate));

        double totalImpulse = 0.0;
        int impulseDays = 0;
        List<Transaction> impulseTxs = new ArrayList<>();

        for (Map.Entry<LocalDate, List<Transaction>> entry : byDate.entrySet()) {
            List<Transaction> dayTxs = entry.getValue();
            
            // If 3+ food/dining transactions on same day, likely impulse/emotional
            if (dayTxs.size() >= 3) {
                double dayTotal = dayTxs.stream().mapToDouble(t -> Math.abs(t.getAmount())).sum();
                impulseTxs.addAll(dayTxs);
                totalImpulse += dayTotal;
                impulseDays++;
            }
        }

        if (impulseDays > 0) {
            double monthlyAmount = totalImpulse;
            double annualAmount = monthlyAmount * 12;

            MoneyLeak leak = new MoneyLeak();
            leak.setUser(user);
            leak.setLeakType(MoneyLeak.LeakType.COFFEE_EFFECT); // Reuse type
            leak.setTitle("Emotional / Impulse Spending");
            leak.setDescription(String.format(
                "Detected %d days with 3+ food/dining transactions (likely impulse/emotional spending). " +
                "Total: ₹%.0f in last month.",
                impulseDays, totalImpulse
            ));
            // Use actual category name that exists in database (without parenthetical parts)
            leak.setMerchantPattern("Dining & Food");
            leak.setMonthlyAmount(monthlyAmount);
            leak.setAnnualAmount(annualAmount);
            leak.setTransactionCount(impulseTxs.size());
            leak.setAverageTransactionAmount(impulseTxs.size() > 0 ? totalImpulse / impulseTxs.size() : 0.0);
            leak.setSuggestion("Try meal planning and grocery shopping to reduce impulse food purchases. Set a daily food budget.");
            leak.setIsActive(true);
            leak.setRank(null);

            leaks.add(leak);
        }

        return leaks;
    }

    /**
     * Extract merchant pattern from narration
     */
    public String extractMerchantPattern(String narration) {
        // Use the utility class for consistent narration preprocessing
        return NarrationPreprocessor.extractMerchantPattern(narration);
    }

    /**
     * Get top 3 money leaks for a user
     */
    public List<MoneyLeak> getTopMoneyLeaks(User user) {
        return moneyLeakRepository.findByUserAndRankIsNotNullOrderByRankAsc(user);
    }

    /**
     * Detect anomalies using Isolation Forest (ML-based detection)
     * Calls Python script for ML-powered anomaly detection
     * 
     * @param user User to analyze
     * @return List of anomaly results (transaction_id, amount, date, category, narration, anomaly_score)
     */
    public List<Map<String, Object>> detectAnomalies(User user) {
        logger.info("Detecting anomalies using Isolation Forest for user: {}", user.getId());

        try {
            String projectRoot = System.getProperty("user.dir");
            String scriptPath = "mybudget-ai/anomaly_detection.py";
            if (!Files.exists(Paths.get(projectRoot, scriptPath))) {
                logger.warn("Anomaly detection script not found: {}", scriptPath);
                return new ArrayList<>();
            }

            String scriptAbsolutePath = Paths.get(projectRoot, scriptPath).toAbsolutePath().toString();

            // Build command
            String[] command = {
                pythonCommand,
                "-u",  // Unbuffered mode
                scriptAbsolutePath,
                String.valueOf(user.getId())
            };

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(false);
            pb.directory(new java.io.File(projectRoot));

            Process process = pb.start();
            logger.info("Started anomaly detection Python process for user: {}", user.getId());

            // Read stdout (JSON output)
            StringBuilder output = new StringBuilder();
            Thread stdoutReader = new Thread(() -> {
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
            stdoutReader.setDaemon(true);
            stdoutReader.start();

            // Read stderr (warnings/errors)
            StringBuilder errorOutput = new StringBuilder();
            Thread errorReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        errorOutput.append(line).append("\n");
                    }
                } catch (Exception e) {
                    logger.debug("Error reading Python stderr: {}", e.getMessage());
                }
            });
            errorReader.setDaemon(true);
            errorReader.start();

            // Wait for process with timeout
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                logger.warn("Anomaly detection timeout after {} seconds", timeoutSeconds);
                return new ArrayList<>();
            }

            stdoutReader.join(1000);
            errorReader.join(1000);

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                logger.warn("Anomaly detection script exited with code: {}", exitCode);
                if (errorOutput.length() > 0) {
                    logger.warn("Python error: {}", errorOutput.toString());
                }
                return new ArrayList<>();
            }

            // Parse JSON output
            String jsonOutput = output.toString().trim();
            List<Map<String, Object>> anomalies = new ArrayList<>();

            if (!jsonOutput.isEmpty() && jsonOutput.startsWith("[")) {
                JSONArray resultArray = new JSONArray(jsonOutput);
                for (int i = 0; i < resultArray.length(); i++) {
                    JSONObject jsonResult = resultArray.getJSONObject(i);
                    Map<String, Object> anomaly = new HashMap<>();
                    anomaly.put("transaction_id", jsonResult.optLong("transaction_id", 0));
                    anomaly.put("amount", jsonResult.optDouble("amount", 0.0));
                    anomaly.put("date", jsonResult.optString("date", ""));
                    anomaly.put("category", jsonResult.optString("category", "Unknown"));
                    anomaly.put("intent", jsonResult.optString("intent", "unknown"));
                    anomaly.put("narration", jsonResult.optString("narration", ""));
                    anomaly.put("anomaly_score", jsonResult.optDouble("anomaly_score", 0.0));
                    anomalies.add(anomaly);
                }
            }

            return anomalies;

        } catch (Exception e) {
            logger.error("Error detecting anomalies: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }
}

