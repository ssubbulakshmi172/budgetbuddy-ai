package com.budgetbuddy.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "money_leak")
public class MoneyLeak {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "leak_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private LeakType leakType;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "merchant_pattern")
    private String merchantPattern;

    @Column(name = "monthly_amount", nullable = false)
    private Double monthlyAmount;

    @Column(name = "annual_amount", nullable = false)
    private Double annualAmount;

    @Column(name = "transaction_count")
    private Integer transactionCount;

    @Column(name = "average_transaction_amount")
    private Double averageTransactionAmount;

    @Column(name = "suggestion", length = 500)
    private String suggestion;

    @Column(name = "`rank`")
    private Integer rank; // 1, 2, 3 for top 3

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "detected_at")
    private LocalDate detectedAt;

    @PrePersist
    protected void onCreate() {
        detectedAt = LocalDate.now();
    }

    public enum LeakType {
        REPEATING_SUBSCRIPTION,    // Recurring subscriptions
        COFFEE_EFFECT,             // Small frequent purchases
        ATM_WITHDRAWAL_SPIKE,      // Unusually high ATM withdrawals
        UNUSED_SERVICE,            // Paid but unused services
        AUTO_DEBIT_MISALIGNED      // Auto-debits not aligned with goals
    }

    // Constructors
    public MoneyLeak() {}

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public LeakType getLeakType() { return leakType; }
    public void setLeakType(LeakType leakType) { this.leakType = leakType; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getMerchantPattern() { return merchantPattern; }
    public void setMerchantPattern(String merchantPattern) { this.merchantPattern = merchantPattern; }

    public Double getMonthlyAmount() { return monthlyAmount; }
    public void setMonthlyAmount(Double monthlyAmount) { this.monthlyAmount = monthlyAmount; }

    public Double getAnnualAmount() { return annualAmount; }
    public void setAnnualAmount(Double annualAmount) { this.annualAmount = annualAmount; }

    public Integer getTransactionCount() { return transactionCount; }
    public void setTransactionCount(Integer transactionCount) { this.transactionCount = transactionCount; }

    public Double getAverageTransactionAmount() { return averageTransactionAmount; }
    public void setAverageTransactionAmount(Double averageTransactionAmount) { this.averageTransactionAmount = averageTransactionAmount; }

    public String getSuggestion() { return suggestion; }
    public void setSuggestion(String suggestion) { this.suggestion = suggestion; }

    public Integer getRank() { return rank; }
    public void setRank(Integer rank) { this.rank = rank; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public LocalDate getDetectedAt() { return detectedAt; }
    public void setDetectedAt(LocalDate detectedAt) { this.detectedAt = detectedAt; }
}

