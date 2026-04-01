package com.frauddetection.dashboard_api.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Data Transfer Object (DTO) representing a single financial transaction.
 *
 * This Java class maps 1:1 to the JSON produced by the Python
 * transaction_generator.py. Jackson uses the @JsonProperty annotations
 * to translate between Python's snake_case and Java's camelCase.
 *
 * Example JSON from the Python producer:
 * {
 *   "transaction_id": "uuid-here",
 *   "timestamp": "2026-04-01T12:00:00Z",
 *   "user_id": "u-1001",
 *   "amount": 250.50,
 *   "currency": "USD",
 *   "merchant_category": "grocery",
 *   "country": "SG",
 *   "city": "Singapore",
 *   "latitude": 1.3521,
 *   "longitude": 103.8198
 * }
 *
 * NOTE: No Lombok — all getters, setters, and constructors are manual.
 */
@JsonIgnoreProperties(ignoreUnknown = true) // Safely ignore extra fields we don't need
public class TransactionEvent {

    // --- Fields matching the Python producer's JSON output ---

    @JsonProperty("transaction_id")
    private String transactionId;

    private String timestamp;

    @JsonProperty("event_epoch_ms")
    private long eventEpochMs;

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("account_id")
    private String accountId;

    private double amount;

    private String currency;

    @JsonProperty("merchant_id")
    private String merchantId;

    @JsonProperty("merchant_category")
    private String merchantCategory;

    @JsonProperty("payment_method")
    private String paymentMethod;

    private String channel;

    @JsonProperty("card_present")
    private boolean cardPresent;

    @JsonProperty("device_id")
    private String deviceId;

    @JsonProperty("device_type")
    private String deviceType;

    @JsonProperty("ip_address")
    private String ipAddress;

    private String country;

    private String city;

    private double latitude;

    private double longitude;

    // --- Default No-Args Constructor ---
    // Required by Jackson for JSON deserialization (it creates an empty
    // object first, then populates fields via setters or reflection).
    public TransactionEvent() {}

    // --- Full Constructor (for testing and manual instantiation) ---
    public TransactionEvent(String transactionId, String timestamp, long eventEpochMs,
                            String userId, String accountId, double amount, String currency,
                            String merchantId, String merchantCategory, String paymentMethod,
                            String channel, boolean cardPresent, String deviceId,
                            String deviceType, String ipAddress, String country, String city,
                            double latitude, double longitude) {
        this.transactionId = transactionId;
        this.timestamp = timestamp;
        this.eventEpochMs = eventEpochMs;
        this.userId = userId;
        this.accountId = accountId;
        this.amount = amount;
        this.currency = currency;
        this.merchantId = merchantId;
        this.merchantCategory = merchantCategory;
        this.paymentMethod = paymentMethod;
        this.channel = channel;
        this.cardPresent = cardPresent;
        this.deviceId = deviceId;
        this.deviceType = deviceType;
        this.ipAddress = ipAddress;
        this.country = country;
        this.city = city;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    // --- Getters and Setters ---

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public long getEventEpochMs() { return eventEpochMs; }
    public void setEventEpochMs(long eventEpochMs) { this.eventEpochMs = eventEpochMs; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getMerchantId() { return merchantId; }
    public void setMerchantId(String merchantId) { this.merchantId = merchantId; }

    public String getMerchantCategory() { return merchantCategory; }
    public void setMerchantCategory(String merchantCategory) { this.merchantCategory = merchantCategory; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public boolean isCardPresent() { return cardPresent; }
    public void setCardPresent(boolean cardPresent) { this.cardPresent = cardPresent; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getDeviceType() { return deviceType; }
    public void setDeviceType(String deviceType) { this.deviceType = deviceType; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    @Override
    public String toString() {
        return "TransactionEvent{" +
                "txnId='" + transactionId + '\'' +
                ", userId='" + userId + '\'' +
                ", amount=" + amount +
                ", country='" + country + '\'' +
                '}';
    }
}
