package com.budgetbuddy.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.YearMonth;

@Entity
@Table(name = "weekend_overspending")
public class WeekendOverspending {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "category")
    private String category;

    @Column(name = "weekend_avg", nullable = false)
    private Double weekendAvg;

    @Column(name = "weekday_avg", nullable = false)
    private Double weekdayAvg;

    @Column(name = "ratio", nullable = false)
    private Double ratio; // weekend_avg / weekday_avg

    @Column(name = "percentage_increase")
    private Double percentageIncrease;

    @Column(name = "month")
    private YearMonth month;

    @Column(name = "trend")
    @Enumerated(EnumType.STRING)
    private Trend trend;

    @Column(name = "alert_level")
    @Enumerated(EnumType.STRING)
    private AlertLevel alertLevel;

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

    public enum Trend {
        INCREASING, DECREASING, STABLE
    }

    public enum AlertLevel {
        LOW,    // Ratio 1.1-1.3
        MEDIUM, // Ratio 1.3-1.5
        HIGH    // Ratio > 1.5
    }

    // Constructors
    public WeekendOverspending() {}

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Double getWeekendAvg() { return weekendAvg; }
    public void setWeekendAvg(Double weekendAvg) { this.weekendAvg = weekendAvg; }

    public Double getWeekdayAvg() { return weekdayAvg; }
    public void setWeekdayAvg(Double weekdayAvg) { this.weekdayAvg = weekdayAvg; }

    public Double getRatio() { return ratio; }
    public void setRatio(Double ratio) { this.ratio = ratio; }

    public Double getPercentageIncrease() { return percentageIncrease; }
    public void setPercentageIncrease(Double percentageIncrease) { this.percentageIncrease = percentageIncrease; }

    public YearMonth getMonth() { return month; }
    public void setMonth(YearMonth month) { this.month = month; }

    public Trend getTrend() { return trend; }
    public void setTrend(Trend trend) { this.trend = trend; }

    public AlertLevel getAlertLevel() { return alertLevel; }
    public void setAlertLevel(AlertLevel alertLevel) { this.alertLevel = alertLevel; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public LocalDate getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDate createdAt) { this.createdAt = createdAt; }
}

