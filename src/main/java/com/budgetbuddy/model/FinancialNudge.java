package com.budgetbuddy.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "financial_nudges")
public class FinancialNudge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "nudge_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private NudgeType nudgeType; // SPENDING_ALERT, PATTERN_DETECTED, OVERSpending_RISK, TREND_WARNING

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "message", nullable = false, length = 1000)
    private String message;

    @Column(name = "suggestion", length = 500)
    private String suggestion; // Actionable recommendation

    @Column(name = "category")
    private String category;

    @Column(name = "subcategory")
    private String subcategory;

    @Column(name = "related_amount")
    private Double relatedAmount;

    @Column(name = "priority")
    @Enumerated(EnumType.STRING)
    private Priority priority; // LOW, MEDIUM, HIGH

    @Column(name = "is_read")
    private Boolean isRead = false;

    @Column(name = "is_dismissed")
    private Boolean isDismissed = false;

    @Column(name = "created_at")
    private LocalDate createdAt;

    @Column(name = "expires_at")
    private LocalDate expiresAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDate.now();
        if (expiresAt == null) {
            expiresAt = LocalDate.now().plusDays(7); // Default 7 days expiry
        }
    }

    public enum NudgeType {
        SPENDING_ALERT,        // Early warning about overspending
        PATTERN_DETECTED,       // New spending pattern identified
        OVERSPENDING_RISK,      // High risk of overspending
        TREND_WARNING,          // Unusual trend detected
        BUDGET_MILESTONE,       // Approaching budget limit
        SAVINGS_OPPORTUNITY,    // Opportunity to save
        CATEGORY_OVERSPENDING,  // Category-level overspending alert
        MONEY_LEAK_DETECTED,    // Money leak detected (subscription, coffee-effect, etc.)
        WEEKEND_OVERSPENDING,   // Weekend overspending pattern
        SALARY_WEEK_SPIKE,      // Spending spike after salary
        MONTH_END_SCARCITY,     // Month-end scarcity behavior
        YEAR_END_SAVINGS_PROJECTION  // Year-end savings projection
    }

    public enum Priority {
        LOW, MEDIUM, HIGH
    }

    // Constructors
    public FinancialNudge() {}

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public NudgeType getNudgeType() {
        return nudgeType;
    }

    public void setNudgeType(NudgeType nudgeType) {
        this.nudgeType = nudgeType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public void setSuggestion(String suggestion) {
        this.suggestion = suggestion;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getSubcategory() {
        return subcategory;
    }

    public void setSubcategory(String subcategory) {
        this.subcategory = subcategory;
    }

    public Double getRelatedAmount() {
        return relatedAmount;
    }

    public void setRelatedAmount(Double relatedAmount) {
        this.relatedAmount = relatedAmount;
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    public Boolean getIsRead() {
        return isRead;
    }

    public void setIsRead(Boolean isRead) {
        this.isRead = isRead;
    }

    public Boolean getIsDismissed() {
        return isDismissed;
    }

    public void setIsDismissed(Boolean isDismissed) {
        this.isDismissed = isDismissed;
    }

    public LocalDate getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDate createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDate getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDate expiresAt) {
        this.expiresAt = expiresAt;
    }
}

