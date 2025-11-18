package com.budgetbuddy.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate date;
    private String narration;
    private String chequeRefNo;

    @Column(name = "withdrawal_amount")
    private Double withdrawalAmt;

    @Column(name = "deposit_amount")
    private Double depositAmt;

    @Column(name = "closing_balance")
    private Double closingBalance;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;




    @Column(name = "predicted_category")
    private String predictedCategory;

    @Column(name = "predicted_subcategory")
    private String predictedSubcategory;

    @Column(name = "predicted_transaction_type")
    private String predictedTransactionType; // P2C, P2P, P2Business

    @Column(name = "predicted_intent")
    private String predictedIntent; // purchase, transfer, refund, subscription, bill_payment, other

    @Column(name = "prediction_confidence")
    private Double predictionConfidence; // 0.0 to 1.0

    @Column(name = "prediction_reason", nullable = true)
    private String predictionReason; // keyword_match, ml_prediction, etc.

    @Column(nullable = true)
    private String categoryName; // New column for the matched category

    @Column(nullable = false)
    private Double amount;

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amt) {
        this.amount = amt;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }
    // Default Constructor
    public Transaction() {}

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public String getNarration() {
        return narration;
    }

    public void setNarration(String narration) {
        this.narration = narration;
    }

    public String getChequeRefNo() {
        return chequeRefNo;
    }

    public void setChequeRefNo(String chequeRefNo) {
        this.chequeRefNo = chequeRefNo;
    }

    public Double getWithdrawalAmt() {
        return withdrawalAmt;
    }

    public void setWithdrawalAmt(Double withdrawalAmt) {
        this.withdrawalAmt = withdrawalAmt;
    }

    public Double getDepositAmt() {
        return depositAmt;
    }

    public void setDepositAmt(Double depositAmt) {
        this.depositAmt = depositAmt;
    }

    public Double getClosingBalance() {
        return closingBalance;
    }

    public void setClosingBalance(Double closingBalance) {
        this.closingBalance = closingBalance;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getPredictedCategory() {
        return predictedCategory;
    }

    public void setPredictedCategory(String predictedCategory) {
        this.predictedCategory = predictedCategory;
    }

    public String getPredictedSubcategory() {
        return predictedSubcategory;
    }

    public void setPredictedSubcategory(String predictedSubcategory) {
        this.predictedSubcategory = predictedSubcategory;
    }

    public String getPredictedTransactionType() {
        return predictedTransactionType;
    }

    public void setPredictedTransactionType(String predictedTransactionType) {
        this.predictedTransactionType = predictedTransactionType;
    }

    public String getPredictedIntent() {
        return predictedIntent;
    }

    public void setPredictedIntent(String predictedIntent) {
        this.predictedIntent = predictedIntent;
    }

    public Double getPredictionConfidence() {
        return predictionConfidence;
    }

    public void setPredictionConfidence(Double predictionConfidence) {
        this.predictionConfidence = predictionConfidence;
    }

    public String getPredictionReason() {
        return predictionReason;
    }

    public void setPredictionReason(String predictionReason) {
        this.predictionReason = predictionReason;
    }

    // Override toString method for logging or debugging
    @Override
    public String toString() {
        return "Transaction{" +
                "id=" + id +
                ", date=" + date +
                ", narration='" + narration + '\'' +
                ", chequeRefNo='" + chequeRefNo + '\'' +
                ", withdrawalAmt=" + withdrawalAmt +
                ", depositAmt=" + depositAmt +
                ", closingBalance=" + closingBalance +
                ", user=" + user.getId() + " - " + user.getName() +
                ", categoryName="+categoryName+
                '}';
    }
}
