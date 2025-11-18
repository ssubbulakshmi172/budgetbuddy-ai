package com.budgetbuddy.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.YearMonth;

@Entity
@Table(name = "salary_week_analysis")
public class SalaryWeekAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "salary_date")
    private LocalDate salaryDate;

    @Column(name = "salary_amount")
    private Double salaryAmount;

    @Column(name = "salary_week_start")
    private LocalDate salaryWeekStart;

    @Column(name = "salary_week_end")
    private LocalDate salaryWeekEnd;

    @Column(name = "salary_week_spending", nullable = false)
    private Double salaryWeekSpending;

    @Column(name = "non_salary_week_avg", nullable = false)
    private Double nonSalaryWeekAvg;

    @Column(name = "ratio", nullable = false)
    private Double ratio; // salary_week / non_salary_week

    @Column(name = "extra_spending")
    private Double extraSpending;

    @Column(name = "month")
    private YearMonth month;

    @Column(name = "is_anomaly")
    private Boolean isAnomaly = false;

    @Column(name = "confidence_score")
    private Double confidenceScore; // Confidence in salary detection

    @Column(name = "created_at")
    private LocalDate createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDate.now();
        if (month == null) {
            month = YearMonth.now();
        }
    }

    // Constructors
    public SalaryWeekAnalysis() {}

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public LocalDate getSalaryDate() { return salaryDate; }
    public void setSalaryDate(LocalDate salaryDate) { this.salaryDate = salaryDate; }

    public Double getSalaryAmount() { return salaryAmount; }
    public void setSalaryAmount(Double salaryAmount) { this.salaryAmount = salaryAmount; }

    public LocalDate getSalaryWeekStart() { return salaryWeekStart; }
    public void setSalaryWeekStart(LocalDate salaryWeekStart) { this.salaryWeekStart = salaryWeekStart; }

    public LocalDate getSalaryWeekEnd() { return salaryWeekEnd; }
    public void setSalaryWeekEnd(LocalDate salaryWeekEnd) { this.salaryWeekEnd = salaryWeekEnd; }

    public Double getSalaryWeekSpending() { return salaryWeekSpending; }
    public void setSalaryWeekSpending(Double salaryWeekSpending) { this.salaryWeekSpending = salaryWeekSpending; }

    public Double getNonSalaryWeekAvg() { return nonSalaryWeekAvg; }
    public void setNonSalaryWeekAvg(Double nonSalaryWeekAvg) { this.nonSalaryWeekAvg = nonSalaryWeekAvg; }

    public Double getRatio() { return ratio; }
    public void setRatio(Double ratio) { this.ratio = ratio; }

    public Double getExtraSpending() { return extraSpending; }
    public void setExtraSpending(Double extraSpending) { this.extraSpending = extraSpending; }

    public YearMonth getMonth() { return month; }
    public void setMonth(YearMonth month) { this.month = month; }

    public Boolean getIsAnomaly() { return isAnomaly; }
    public void setIsAnomaly(Boolean isAnomaly) { this.isAnomaly = isAnomaly; }

    public Double getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(Double confidenceScore) { this.confidenceScore = confidenceScore; }

    public LocalDate getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDate createdAt) { this.createdAt = createdAt; }
}

