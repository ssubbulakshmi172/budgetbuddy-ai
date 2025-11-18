package com.budgetbuddy.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.YearMonth;

@Entity
@Table(name = "category_overspending_alert")
public class CategoryOverspendingAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "alert_level", nullable = false)
    @Enumerated(EnumType.STRING)
    private AlertLevel alertLevel;

    @Column(name = "current_amount", nullable = false)
    private Double currentAmount;

    @Column(name = "historical_avg", nullable = false)
    private Double historicalAvg;

    @Column(name = "standard_deviation")
    private Double standardDeviation;

    @Column(name = "percentage_increase")
    private Double percentageIncrease;

    @Column(name = "projected_monthly")
    private Double projectedMonthly;

    @Column(name = "month")
    private YearMonth month;

    @Column(name = "days_elapsed")
    private Integer daysElapsed;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "created_at")
    private LocalDate createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDate.now();
        if (month == null) {
            month = YearMonth.now();
        }
    }

    public enum AlertLevel {
        LOW,        // < 10% increase
        MEDIUM,     // 10-25% increase
        HIGH,       // 25-50% increase
        CRITICAL    // > 50% increase OR exceeds 2× std_dev
    }

    // Constructors
    public CategoryOverspendingAlert() {}

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public AlertLevel getAlertLevel() { return alertLevel; }
    public void setAlertLevel(AlertLevel alertLevel) { this.alertLevel = alertLevel; }

    public Double getCurrentAmount() { return currentAmount; }
    public void setCurrentAmount(Double currentAmount) { this.currentAmount = currentAmount; }

    public Double getHistoricalAvg() { return historicalAvg; }
    public void setHistoricalAvg(Double historicalAvg) { this.historicalAvg = historicalAvg; }

    public Double getStandardDeviation() { return standardDeviation; }
    public void setStandardDeviation(Double standardDeviation) { this.standardDeviation = standardDeviation; }

    public Double getPercentageIncrease() { return percentageIncrease; }
    public void setPercentageIncrease(Double percentageIncrease) { this.percentageIncrease = percentageIncrease; }

    public Double getProjectedMonthly() { return projectedMonthly; }
    public void setProjectedMonthly(Double projectedMonthly) { this.projectedMonthly = projectedMonthly; }

    public YearMonth getMonth() { return month; }
    public void setMonth(YearMonth month) { this.month = month; }

    public Integer getDaysElapsed() { return daysElapsed; }
    public void setDaysElapsed(Integer daysElapsed) { this.daysElapsed = daysElapsed; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public LocalDate getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDate createdAt) { this.createdAt = createdAt; }
}

