package com.fraudpipeline.dashboard.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Data Transfer Object (DTO) for Incoming Kafka Transactions.
 * Represents the "Data Plane" message in the Java Control Plane.
 * 
 * Note: No Lombok used; utilizing manual getters, setters, and constructors.
 */
public class TransactionEvent {

    @JsonProperty("user_id")
    private String userId;

    private double amount;

    private String currency;

    private String merchant;

    private String category;

    private String timestamp;

    @JsonProperty("is_fraud")
    private boolean isFraud;

    // Default No-Args Constructor (Required for Jackson JSON Deserialization)
    public TransactionEvent() {}

    // Full-Args Constructor for internal testing/instantiation
    public TransactionEvent(String userId, double amount, String currency, String merchant, 
                            String category, String timestamp, boolean isFraud) {
        this.userId = userId;
        this.amount = amount;
        this.currency = currency;
        this.merchant = merchant;
        this.category = category;
        this.timestamp = timestamp;
        this.isFraud = isFraud;
    }

    // --- MANUAL GETTERS & SETTERS ---

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getMerchant() { return merchant; }
    public void setMerchant(String merchant) { this.merchant = merchant; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public boolean isFraud() { return isFraud; }
    public void setFraud(boolean fraud) { isFraud = fraud; }

    @Override
    public String toString() {
        return "TransactionEvent{" +
                "userId='" + userId + '\'' +
                ", amount=" + amount +
                ", merchant='" + merchant + '\'' +
                ", isFraud=" + isFraud +
                '}';
    }
}
