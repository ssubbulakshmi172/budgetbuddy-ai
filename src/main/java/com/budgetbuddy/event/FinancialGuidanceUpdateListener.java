package com.budgetbuddy.event;

import com.budgetbuddy.model.User;
import com.budgetbuddy.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listens to transaction changes and automatically updates financial guidance tables
 * Implements debouncing to prevent redundant updates
 */
@Component
public class FinancialGuidanceUpdateListener {

    private static final Logger logger = LoggerFactory.getLogger(FinancialGuidanceUpdateListener.class);
    
    // Debounce: Only update once per user within 30 seconds
    private static final long UPDATE_DEBOUNCE_MS = 30000; // 30 seconds
    private final Map<Long, Long> lastUpdateTime = new ConcurrentHashMap<>();

    @Autowired
    private CategoryOverspendingService categoryOverspendingService;

    @Autowired
    private MoneyLeakService moneyLeakService;

    @Autowired
    private SavingsProjectionService savingsProjectionService;

    @Autowired
    private WeekendOverspendingService weekendOverspendingService;

    @Autowired
    private FinancialAnalyticsService financialAnalyticsService;

    /**
     * Automatically update all financial guidance tables when transaction changes
     * Runs asynchronously to avoid blocking transaction save operations
     * Implements debouncing to prevent redundant updates
     */
    @EventListener
    @Async
    public void handleTransactionChanged(TransactionChangedEvent event) {
        User user = event.getUser();
        if (user == null) {
            logger.warn("TransactionChangedEvent received with null user, skipping update");
            return;
        }

        Long userId = user.getId();
        long currentTime = System.currentTimeMillis();
        Long lastUpdate = lastUpdateTime.get(userId);
        
        // Check if we should skip this update (debounce)
        if (lastUpdate != null && (currentTime - lastUpdate) < UPDATE_DEBOUNCE_MS) {
            logger.debug("⏭️ Skipping financial guidance update for user {} (debounced, last update {}ms ago)", 
                userId, currentTime - lastUpdate);
            return;
        }

        logger.info("🔄 Auto-updating financial guidance for user {} after transaction {}", 
            userId, event.getChangeType());

        // Update timestamp before processing
        lastUpdateTime.put(userId, currentTime);
        
        // Call internal update method directly
        updateFinancialGuidanceInternal(user);
    }

    /**
     * Public method to manually trigger financial guidance updates
     * Can be called directly or via the reload endpoint
     * Bypasses debounce for manual updates
     */
    public void updateFinancialGuidance(User user) {
        if (user == null) {
            logger.warn("Cannot update financial guidance: user is null");
            return;
        }

        Long userId = user.getId();
        long currentTime = System.currentTimeMillis();
        
        // For manual updates, check debounce but allow if enough time has passed
        Long lastUpdate = lastUpdateTime.get(userId);
        if (lastUpdate != null && (currentTime - lastUpdate) < UPDATE_DEBOUNCE_MS) {
            logger.debug("⏭️ Skipping manual financial guidance update for user {} (debounced, last update {}ms ago). Use /guidance/reload to force update.", 
                userId, currentTime - lastUpdate);
            return;
        }

        // Update timestamp
        lastUpdateTime.put(userId, currentTime);
        
        // Call internal update method
        updateFinancialGuidanceInternal(user);
    }

    /**
     * Force update financial guidance (bypasses debounce)
     * Used for manual reload requests
     */
    public void forceUpdateFinancialGuidance(User user) {
        if (user == null) {
            logger.warn("Cannot force update financial guidance: user is null");
            return;
        }

        Long userId = user.getId();
        long currentTime = System.currentTimeMillis();
        
        // Update timestamp to reset debounce
        lastUpdateTime.put(userId, currentTime);
        
        logger.info("🔄 Force updating financial guidance for user {} (bypassing debounce)", userId);
        
        // Call the update method (it will check debounce but we just reset it)
        updateFinancialGuidanceInternal(user);
    }

    /**
     * Internal update method that performs the actual update
     */
    private void updateFinancialGuidanceInternal(User user) {
        if (user == null) {
            logger.warn("Cannot update financial guidance: user is null");
            return;
        }

        try {
            // 1. Update Category Overspending Alerts
            try {
                categoryOverspendingService.detectOverspending(user);
                logger.debug("✅ Updated category overspending alerts");
            } catch (Exception e) {
                logger.error("❌ Error updating category overspending: {}", e.getMessage());
            }

            // 2. Update Weekend Overspending
            try {
                weekendOverspendingService.detectWeekendOverspending(user);
                logger.debug("✅ Updated weekend overspending");
            } catch (Exception e) {
                logger.error("❌ Error updating weekend overspending: {}", e.getMessage());
            }

            // 3. Update Savings Projection
            try {
                savingsProjectionService.calculateYearEndSavings(user);
                logger.debug("✅ Updated savings projection");
            } catch (Exception e) {
                logger.error("❌ Error updating savings projection: {}", e.getMessage());
            }

            logger.info("✅ Financial guidance update completed for user {}", user.getId());

        } catch (Exception e) {
            logger.error("❌ Error in financial guidance update: {}", e.getMessage(), e);
        }
    }
}

