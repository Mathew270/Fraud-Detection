package com.frauddetection.dashboard_api.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * DTO representing a fraud alert.
 *
 * Maps to the alert JSON published by {@code fraud_detector.py} to the
 * {@code fraud-alerts} Kafka topic. Each alert wraps the offending
 * {@link TransactionEvent} along with the fraud reasons and detector context.
 *
 * <p>Example JSON:
 * <pre>{@code
 * {
 *   "alert_id": "alert-uuid-here",
 *   "created_at": "2026-04-01T12:00:01Z",
 *   "transaction": { ... },
 *   "fraud_reasons": ["huge_amount", "location_anomaly"],
 *   "detector_context": {
 *     "recent_transaction_count_in_window": 5,
 *     "distance_from_last_km": 9800.0
 *   },
 *   "severity": "high"
 * }
 * }</pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AlertEvent {

    @JsonProperty("alert_id")
    private String alertId;

    @JsonProperty("created_at")
    private String createdAt;

    /**
     * The transaction that triggered this alert.
     * Jackson automatically deserializes the nested JSON object.
     */
    private TransactionEvent transaction;

    @JsonProperty("fraud_reasons")
    private List<String> fraudReasons;

    /**
     * Flexible context from the fraud detector (e.g. distance, window count).
     * Uses {@code Map<String, Object>} because the Python side may add
     * arbitrary keys depending on which fraud rules triggered.
     */
    @JsonProperty("detector_context")
    private Map<String, Object> detectorContext;

    /** Severity level: "high" for huge_amount alerts, "medium" otherwise. */
    private String severity;

    /** No-args constructor required by Jackson for deserialization. */
    public AlertEvent() {}

    /** Full constructor for programmatic instantiation and testing. */
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
