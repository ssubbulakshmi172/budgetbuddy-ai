package com.budgetbuddy.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "spending_predictions")
public class SpendingPrediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "prediction_date", nullable = false)
    private LocalDate predictionDate; // When this prediction was made

    @Column(name = "forecast_start_date", nullable = false)
    private LocalDate forecastStartDate; // Start of forecast period

    @Column(name = "forecast_end_date", nullable = false)
    private LocalDate forecastEndDate; // End of forecast period

    @Column(name = "category")
    private String category;

    @Column(name = "subcategory")
    private String subcategory;

    @Column(name = "predicted_amount", nullable = false)
    private Double predictedAmount;

    @Column(name = "confidence_score")
    private Double confidenceScore; // 0.0 to 1.0

    @Column(name = "prediction_method")
    private String predictionMethod; // "PATTERN_BASED", "TREND_BASED", "HISTORICAL_AVERAGE"

    @Column(name = "risk_level")
    @Enumerated(EnumType.STRING)
    private RiskLevel riskLevel; // LOW, MEDIUM, HIGH

    @Column(name = "is_overspending_risk")
    private Boolean isOverspendingRisk = false;

    @Column(name = "created_at")
    private LocalDate createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDate.now();
    }

    public enum RiskLevel {
        LOW, MEDIUM, HIGH
    }

    // Constructors
    public SpendingPrediction() {}

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

    public LocalDate getPredictionDate() {
        return predictionDate;
    }

    public void setPredictionDate(LocalDate predictionDate) {
        this.predictionDate = predictionDate;
    }

    public LocalDate getForecastStartDate() {
        return forecastStartDate;
    }

    public void setForecastStartDate(LocalDate forecastStartDate) {
        this.forecastStartDate = forecastStartDate;
    }

    public LocalDate getForecastEndDate() {
        return forecastEndDate;
    }

    public void setForecastEndDate(LocalDate forecastEndDate) {
        this.forecastEndDate = forecastEndDate;
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

    public Double getPredictedAmount() {
        return predictedAmount;
    }

    public void setPredictedAmount(Double predictedAmount) {
        this.predictedAmount = predictedAmount;
    }

    public Double getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(Double confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public String getPredictionMethod() {
        return predictionMethod;
    }

    public void setPredictionMethod(String predictionMethod) {
        this.predictionMethod = predictionMethod;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(RiskLevel riskLevel) {
        this.riskLevel = riskLevel;
    }

    public Boolean getIsOverspendingRisk() {
        return isOverspendingRisk;
    }

    public void setIsOverspendingRisk(Boolean isOverspendingRisk) {
        this.isOverspendingRisk = isOverspendingRisk;
    }

    public LocalDate getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDate createdAt) {
        this.createdAt = createdAt;
    }
}

