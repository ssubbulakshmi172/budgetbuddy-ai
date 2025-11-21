package com.budgetbuddy.event;

import com.budgetbuddy.model.Transaction;
import com.budgetbuddy.model.User;

/**
 * Event published when a transaction is created, updated, or deleted
 */
public class TransactionChangedEvent {
    private final Transaction transaction;
    private final User user;
    private final ChangeType changeType;

    public enum ChangeType {
        CREATED, UPDATED, DELETED
    }

    public TransactionChangedEvent(Transaction transaction, User user, ChangeType changeType) {
        this.transaction = transaction;
        this.user = user;
        this.changeType = changeType;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public User getUser() {
        return user;
    }

    public ChangeType getChangeType() {
        return changeType;
    }
}

