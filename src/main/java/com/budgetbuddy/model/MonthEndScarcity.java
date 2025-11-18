package com.budgetbuddy.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.YearMonth;

@Entity
@Table(name = "month_end_scarcity")
public class MonthEndScarcity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "month", nullable = false)
    private YearMonth month;

    @Column(name = "month_end_spending", nullable = false)
    private Double monthEndSpending;

    @Column(name = "rest_of_month_avg", nullable = false)
    private Double restOfMonthAvg;

    @Column(name = "ratio", nullable = false)
    private Double ratio; // month_end / rest_of_month

    @Column(name = "average_reduction_pct")
    private Double averageReductionPct;

    @Column(name = "behavior_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private BehaviorType behaviorType;

    @Column(name = "reduced_spending")
    private Boolean reducedSpending = false;

    @Column(name = "credit_spike")
    private Boolean creditSpike = false;

    @Column(name = "savings_withdrawal")
    private Boolean savingsWithdrawal = false;

    @Column(name = "borrowing_increase")
    private Boolean borrowingIncrease = false;

    @Column(name = "pattern_strength")
    private Double patternStrength; // 0.0 to 1.0

    @Column(name = "months_detected")
    private Integer monthsDetected;

    @Column(name = "created_at")
    private LocalDate createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDate.now();
    }

    public enum BehaviorType {
        SCARCITY,      // Spending drops significantly at month-end
        NORMAL,        // Consistent spending throughout month
        OVERSPEND      // Spending increases at month-end
    }

    // Constructors
    public MonthEndScarcity() {}

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public YearMonth getMonth() { return month; }
    public void setMonth(YearMonth month) { this.month = month; }

    public Double getMonthEndSpending() { return monthEndSpending; }
    public void setMonthEndSpending(Double monthEndSpending) { this.monthEndSpending = monthEndSpending; }

    public Double getRestOfMonthAvg() { return restOfMonthAvg; }
    public void setRestOfMonthAvg(Double restOfMonthAvg) { this.restOfMonthAvg = restOfMonthAvg; }

    public Double getRatio() { return ratio; }
    public void setRatio(Double ratio) { this.ratio = ratio; }

    public Double getAverageReductionPct() { return averageReductionPct; }
    public void setAverageReductionPct(Double averageReductionPct) { this.averageReductionPct = averageReductionPct; }

    public BehaviorType getBehaviorType() { return behaviorType; }
    public void setBehaviorType(BehaviorType behaviorType) { this.behaviorType = behaviorType; }

    public Boolean getReducedSpending() { return reducedSpending; }
    public void setReducedSpending(Boolean reducedSpending) { this.reducedSpending = reducedSpending; }

    public Boolean getCreditSpike() { return creditSpike; }
    public void setCreditSpike(Boolean creditSpike) { this.creditSpike = creditSpike; }

    public Boolean getSavingsWithdrawal() { return savingsWithdrawal; }
    public void setSavingsWithdrawal(Boolean savingsWithdrawal) { this.savingsWithdrawal = savingsWithdrawal; }

    public Boolean getBorrowingIncrease() { return borrowingIncrease; }
    public void setBorrowingIncrease(Boolean borrowingIncrease) { this.borrowingIncrease = borrowingIncrease; }

    public Double getPatternStrength() { return patternStrength; }
    public void setPatternStrength(Double patternStrength) { this.patternStrength = patternStrength; }

    public Integer getMonthsDetected() { return monthsDetected; }
    public void setMonthsDetected(Integer monthsDetected) { this.monthsDetected = monthsDetected; }

    public LocalDate getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDate createdAt) { this.createdAt = createdAt; }
}

