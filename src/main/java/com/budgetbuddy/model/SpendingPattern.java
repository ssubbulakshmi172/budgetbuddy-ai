package com.budgetbuddy.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.DayOfWeek;

@Entity
@Table(name = "spending_patterns")
public class SpendingPattern {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "pattern_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private PatternType patternType; // DAILY, WEEKLY, MONTHLY, SEASONAL

    @Column(name = "category")
    private String category;

    @Column(name = "subcategory")
    private String subcategory;

    @Column(name = "merchant_pattern")
    private String merchantPattern; // Pattern in narration (e.g., "STARBUCKS", "UTILITY BILL")

    @Column(name = "day_of_week")
    @Enumerated(EnumType.STRING)
    private DayOfWeek dayOfWeek; // For weekly patterns

    @Column(name = "day_of_month")
    private Integer dayOfMonth; // For monthly patterns (e.g., 1st, 15th)

    @Column(name = "time_of_day")
    private String timeOfDay; // "MORNING", "AFTERNOON", "EVENING", "NIGHT"

    @Column(name = "average_amount", nullable = false)
    private Double averageAmount;

    @Column(name = "frequency")
    private Integer frequency; // How many times per period

    @Column(name = "confidence_score")
    private Double confidenceScore; // 0.0 to 1.0

    @Column(name = "first_observed")
    private LocalDate firstObserved;

    @Column(name = "last_observed")
    private LocalDate lastObserved;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "created_at")
    private LocalDate createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDate.now();
    }

    public enum PatternType {
        DAILY, WEEKLY, MONTHLY, SEASONAL
    }

    // Constructors
    public SpendingPattern() {}

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

    public PatternType getPatternType() {
        return patternType;
    }

    public void setPatternType(PatternType patternType) {
        this.patternType = patternType;
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

    public String getMerchantPattern() {
        return merchantPattern;
    }

    public void setMerchantPattern(String merchantPattern) {
        this.merchantPattern = merchantPattern;
    }

    public DayOfWeek getDayOfWeek() {
        return dayOfWeek;
    }

    public void setDayOfWeek(DayOfWeek dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public Integer getDayOfMonth() {
        return dayOfMonth;
    }

    public void setDayOfMonth(Integer dayOfMonth) {
        this.dayOfMonth = dayOfMonth;
    }

    public String getTimeOfDay() {
        return timeOfDay;
    }

    public void setTimeOfDay(String timeOfDay) {
        this.timeOfDay = timeOfDay;
    }

    public Double getAverageAmount() {
        return averageAmount;
    }

    public void setAverageAmount(Double averageAmount) {
        this.averageAmount = averageAmount;
    }

    public Integer getFrequency() {
        return frequency;
    }

    public void setFrequency(Integer frequency) {
        this.frequency = frequency;
    }

    public Double getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(Double confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public LocalDate getFirstObserved() {
        return firstObserved;
    }

    public void setFirstObserved(LocalDate firstObserved) {
        this.firstObserved = firstObserved;
    }

    public LocalDate getLastObserved() {
        return lastObserved;
    }

    public void setLastObserved(LocalDate lastObserved) {
        this.lastObserved = lastObserved;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public LocalDate getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDate createdAt) {
        this.createdAt = createdAt;
    }
}

