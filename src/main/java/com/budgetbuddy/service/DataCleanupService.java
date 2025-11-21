package com.budgetbuddy.service;

import com.budgetbuddy.model.User;
import com.budgetbuddy.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class DataCleanupService {

    private static final Logger logger = LoggerFactory.getLogger(DataCleanupService.class);

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private CategoryOverspendingAlertRepository categoryOverspendingAlertRepository;

    @Autowired
    private MoneyLeakRepository moneyLeakRepository;

    @Autowired
    private SavingsProjectionRepository savingsProjectionRepository;

    @Autowired
    private WeekendOverspendingRepository weekendOverspendingRepository;

    @Autowired
    private SpendingPatternRepository spendingPatternRepository;

    /**
     * Clear all transaction data and financial guidance data for a user
     * WARNING: This will permanently delete all transactions and all financial analysis data
     */
    @Transactional
    public void clearAllTransactionAndGuidanceData(User user) {
        logger.warn("⚠️ Starting data cleanup for user: {} (ID: {})", user.getName(), user.getId());
        
        try {
            Long userId = user.getId();
            
            // 1. Clear all financial guidance data first (to avoid foreign key issues)
            clearFinancialGuidanceData(user);
            
            // 2. Delete all transactions for this user
            List<com.budgetbuddy.model.Transaction> userTransactions = transactionRepository.findAll()
                .stream()
                .filter(t -> t.getUser().getId().equals(userId))
                .collect(Collectors.toList());
            
            if (!userTransactions.isEmpty()) {
                transactionRepository.deleteAll(userTransactions);
                logger.info("✅ Deleted {} transactions for user {}", userTransactions.size(), userId);
            } else {
                logger.info("ℹ️ No transactions found for user {}", userId);
            }
            
            logger.info("✅ Successfully cleared all transaction and financial guidance data for user: {} (ID: {})", 
                user.getName(), userId);
        } catch (Exception e) {
            logger.error("❌ Error clearing transaction and guidance data for user {}: {}", 
                user.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to clear data: " + e.getMessage(), e);
        }
    }

    /**
     * Clear all financial guidance data for a user
     */
    @Transactional
    private void clearFinancialGuidanceData(User user) {
        Long userId = user.getId();
        logger.info("Clearing financial guidance data for user: {} (ID: {})", user.getName(), userId);
        
        try {
            // Delete Category Overspending Alerts
            List<com.budgetbuddy.model.CategoryOverspendingAlert> categoryAlerts = 
                categoryOverspendingAlertRepository.findByUserAndIsActiveTrue(user);
            if (!categoryAlerts.isEmpty()) {
                categoryOverspendingAlertRepository.deleteAll(categoryAlerts);
                logger.info("✅ Deleted {} category overspending alerts", categoryAlerts.size());
            }
            
            // Delete Money Leaks
            List<com.budgetbuddy.model.MoneyLeak> moneyLeaks = 
                moneyLeakRepository.findByUserAndIsActiveTrueOrderByAnnualAmountDesc(user);
            if (!moneyLeaks.isEmpty()) {
                moneyLeakRepository.deleteAll(moneyLeaks);
                logger.info("✅ Deleted {} money leaks", moneyLeaks.size());
            }
            
            // Delete Savings Projections
            List<com.budgetbuddy.model.SavingsProjection> savingsProjections = 
                savingsProjectionRepository.findByUserAndYearOrderByProjectionDateDesc(user, null);
            // Get all savings projections for the user
            List<com.budgetbuddy.model.SavingsProjection> allSavingsProjections = 
                savingsProjectionRepository.findAll()
                    .stream()
                    .filter(sp -> sp.getUser().getId().equals(userId))
                    .collect(Collectors.toList());
            if (!allSavingsProjections.isEmpty()) {
                savingsProjectionRepository.deleteAll(allSavingsProjections);
                logger.info("✅ Deleted {} savings projections", allSavingsProjections.size());
            }
            
            // Delete Weekend Overspending
            List<com.budgetbuddy.model.WeekendOverspending> weekendOverspending = 
                weekendOverspendingRepository.findByUserAndIsActiveTrue(user);
            if (!weekendOverspending.isEmpty()) {
                weekendOverspendingRepository.deleteAll(weekendOverspending);
                logger.info("✅ Deleted {} weekend overspending records", weekendOverspending.size());
            }
            
            // Delete Spending Patterns
            List<com.budgetbuddy.model.SpendingPattern> spendingPatterns = 
                spendingPatternRepository.findByUserAndIsActiveTrue(user);
            if (!spendingPatterns.isEmpty()) {
                spendingPatternRepository.deleteAll(spendingPatterns);
                logger.info("✅ Deleted {} spending patterns", spendingPatterns.size());
            }
            
            logger.info("✅ Successfully cleared all financial guidance data for user: {} (ID: {})", 
                user.getName(), userId);
        } catch (Exception e) {
            logger.error("❌ Error clearing financial guidance data for user {}: {}", 
                userId, e.getMessage(), e);
            throw e;
        }
    }
}

