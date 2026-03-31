package com.fraudpipeline.dashboard.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Data Transfer Object (DTO) for Fraud Alerts.
 * This represents a suspicious event flagged by the Python Fraud Detector.
 * 
 * Note: No Lombok used; utilizing manual getters, setters, and constructors.
 */
public class AlertEvent {

    @JsonProperty("alert_id")
    private String alertId;

    @JsonProperty("created_at")
    private String createdAt;

    // Use the TransactionEvent model we created as a nested object
    private TransactionEvent transaction;

    @JsonProperty("fraud_reasons")
    private List<String> fraudReasons;

    @JsonProperty("detector_context")
    private Map<String, Object> detectorContext;

    private String severity;

    // Default No-Args Constructor (Required for Jackson JSON Deserialization)
    public AlertEvent() {}

    // Full-Args Constructor
    public AlertEvent(String alertId, String createdAt, TransactionEvent transaction, 
                      List<String> fraudReasons, Map<String, Object> detectorContext, String severity) {
        this.alertId = alertId;
        this.createdAt = createdAt;
        this.transaction = transaction;
        this.fraudReasons = fraudReasons;
        this.detectorContext = detectorContext;
        this.severity = severity;
    }

    // --- MANUAL GETTERS & SETTERS ---

    public String getAlertId() { return alertId; }
    public void setAlertId(String alertId) { this.alertId = alertId; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public TransactionEvent getTransaction() { return transaction; }
    public void setTransaction(TransactionEvent transaction) { this.transaction = transaction; }

    public List<String> getFraudReasons() { return fraudReasons; }
    public void setFraudReasons(List<String> fraudReasons) { this.fraudReasons = fraudReasons; }

    public Map<String, Object> getDetectorContext() { return detectorContext; }
    public void setDetectorContext(Map<String, Object> detectorContext) { this.detectorContext = detectorContext; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    @Override
    public String toString() {
        return "AlertEvent{" +
                "alertId='" + alertId + '\'' +
                ", severity='" + severity + '\'' +
                ", reasons=" + fraudReasons +
                '}';
    }
}
