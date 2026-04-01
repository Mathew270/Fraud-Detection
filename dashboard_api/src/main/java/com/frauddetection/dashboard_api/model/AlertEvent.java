package com.frauddetection.dashboard_api.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Data Transfer Object (DTO) representing a fraud alert.
 *
 * This maps to the alert JSON published by the Python fraud_detector.py
 * to the 'fraud-alerts' Kafka topic. Each alert wraps the offending
 * transaction along with the reasons it was flagged and contextual data.
 *
 * Example JSON from the Python fraud detector:
 * {
 *   "alert_id": "alert-uuid-here",
 *   "created_at": "2026-04-01T12:00:01Z",
 *   "transaction": { ... full TransactionEvent ... },
 *   "fraud_reasons": ["huge_amount", "location_anomaly"],
 *   "detector_context": {
 *     "recent_transaction_count_in_window": 5,
 *     "distance_from_last_km": 9800.0
 *   },
 *   "severity": "high"
 * }
 *
 * NOTE: No Lombok — all getters, setters, and constructors are manual.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AlertEvent {

    @JsonProperty("alert_id")
    private String alertId;

    @JsonProperty("created_at")
    private String createdAt;

    // Nested transaction object — reuses our TransactionEvent DTO.
    // Jackson automatically deserializes the nested JSON into this field.
    private TransactionEvent transaction;

    @JsonProperty("fraud_reasons")
    private List<String> fraudReasons;

    // A flexible map for extra context from the detector (distance, window count, etc.).
    // Using Map<String, Object> because the Python side can add arbitrary keys.
    @JsonProperty("detector_context")
    private Map<String, Object> detectorContext;

    // "high" for huge_amount alerts, "medium" for other types
    private String severity;

    // --- Default No-Args Constructor (required by Jackson) ---
    public AlertEvent() {}

    // --- Full Constructor ---
    public AlertEvent(String alertId, String createdAt, TransactionEvent transaction,
                      List<String> fraudReasons, Map<String, Object> detectorContext,
                      String severity) {
        this.alertId = alertId;
        this.createdAt = createdAt;
        this.transaction = transaction;
        this.fraudReasons = fraudReasons;
        this.detectorContext = detectorContext;
        this.severity = severity;
    }

    // --- Getters and Setters ---

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
                ", userId='" + (transaction != null ? transaction.getUserId() : "null") + '\'' +
                '}';
    }
}
