package com.budgetbuddy.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.YearMonth;

@Entity
@Table(name = "savings_projection")
public class SavingsProjection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "projection_date", nullable = false)
    private LocalDate projectionDate;

    @Column(name = "current_month")
    private Integer currentMonth; // 1-12

    @Column(name = "current_savings", nullable = false)
    private Double currentSavings;

    @Column(name = "monthly_income_avg", nullable = false)
    private Double monthlyIncomeAvg;

    @Column(name = "monthly_expense_avg", nullable = false)
    private Double monthlyExpenseAvg;

    @Column(name = "monthly_savings_rate", nullable = false)
    private Double monthlySavingsRate;

    @Column(name = "remaining_months")
    private Integer remainingMonths;

    @Column(name = "projected_additional_savings")
    private Double projectedAdditionalSavings;

    @Column(name = "projected_year_end", nullable = false)
    private Double projectedYearEnd;

    @Column(name = "confidence_score")
    private Double confidenceScore; // 0.0 to 1.0

    @Column(name = "trend_adjustment_factor")
    private Double trendAdjustmentFactor;

    @Column(name = "year")
    private Integer year;

    @Column(name = "created_at")
    private LocalDate createdAt;

    @PrePersist
    protected void onCreate() {
        projectionDate = LocalDate.now();
        createdAt = LocalDate.now();
        if (year == null) {
            year = LocalDate.now().getYear();
        }
        if (currentMonth == null) {
            currentMonth = LocalDate.now().getMonthValue();
        }
    }

    // Constructors
    public SavingsProjection() {}

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public LocalDate getProjectionDate() { return projectionDate; }
    public void setProjectionDate(LocalDate projectionDate) { this.projectionDate = projectionDate; }

    public Integer getCurrentMonth() { return currentMonth; }
    public void setCurrentMonth(Integer currentMonth) { this.currentMonth = currentMonth; }

    public Double getCurrentSavings() { return currentSavings; }
    public void setCurrentSavings(Double currentSavings) { this.currentSavings = currentSavings; }

    public Double getMonthlyIncomeAvg() { return monthlyIncomeAvg; }
    public void setMonthlyIncomeAvg(Double monthlyIncomeAvg) { this.monthlyIncomeAvg = monthlyIncomeAvg; }

    public Double getMonthlyExpenseAvg() { return monthlyExpenseAvg; }
    public void setMonthlyExpenseAvg(Double monthlyExpenseAvg) { this.monthlyExpenseAvg = monthlyExpenseAvg; }

    public Double getMonthlySavingsRate() { return monthlySavingsRate; }
    public void setMonthlySavingsRate(Double monthlySavingsRate) { this.monthlySavingsRate = monthlySavingsRate; }

    public Integer getRemainingMonths() { return remainingMonths; }
    public void setRemainingMonths(Integer remainingMonths) { this.remainingMonths = remainingMonths; }

    public Double getProjectedAdditionalSavings() { return projectedAdditionalSavings; }
    public void setProjectedAdditionalSavings(Double projectedAdditionalSavings) { this.projectedAdditionalSavings = projectedAdditionalSavings; }

    public Double getProjectedYearEnd() { return projectedYearEnd; }
    public void setProjectedYearEnd(Double projectedYearEnd) { this.projectedYearEnd = projectedYearEnd; }

    public Double getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(Double confidenceScore) { this.confidenceScore = confidenceScore; }

    public Double getTrendAdjustmentFactor() { return trendAdjustmentFactor; }
    public void setTrendAdjustmentFactor(Double trendAdjustmentFactor) { this.trendAdjustmentFactor = trendAdjustmentFactor; }

    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }

    public LocalDate getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDate createdAt) { this.createdAt = createdAt; }
}

