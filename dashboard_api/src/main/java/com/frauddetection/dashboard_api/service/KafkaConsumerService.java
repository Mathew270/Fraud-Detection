package com.frauddetection.dashboard_api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.frauddetection.dashboard_api.model.AlertEvent;
import com.frauddetection.dashboard_api.model.TransactionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * Kafka-to-SSE bridge service.
 *
 * Listens to the {@code transactions} and {@code fraud-alerts} Kafka topics
 * and multicasts received events into Project Reactor {@link Sinks}. The
 * {@link com.frauddetection.dashboard_api.controller.SseController} exposes
 * these sinks as SSE endpoints, enabling real-time browser streaming.
 *
 * <p><strong>Sink configuration:</strong>
 * <ul>
 *   <li>{@code multicast()} — only active subscribers receive events (no replay)</li>
 *   <li>{@code directBestEffort()} — drops events for slow subscribers
 *       rather than buffering indefinitely, preventing out-of-memory errors</li>
 * </ul>
 *
 * <p><strong>Deserialization strategy:</strong>
 * Kafka messages arrive as raw JSON strings (see {@code application.yml}).
 * This service deserializes them manually using Jackson's {@link ObjectMapper}
 * because it consumes two topics with different schemas ({@link TransactionEvent}
 * and {@link AlertEvent}). A single Kafka {@code JsonDeserializer} cannot
 * handle multiple target types on different topics.
 *
 * @see com.frauddetection.dashboard_api.config.KafkaConfig
 * @see com.frauddetection.dashboard_api.controller.SseController
 */
@Service
public class KafkaConsumerService {

    private static final Logger logger = LoggerFactory.getLogger(KafkaConsumerService.class);

    /** Jackson ObjectMapper for JSON string → Java POJO conversion. */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** Broadcast sink for transaction events. */
    private final Sinks.Many<TransactionEvent> transactionSink =
            Sinks.many().multicast().directBestEffort();

    /** Broadcast sink for fraud alert events. */
    private final Sinks.Many<AlertEvent> alertSink =
            Sinks.many().multicast().directBestEffort();

    // =========================================================================
    // Kafka Listeners
    // =========================================================================

    /**
     * Consumes raw JSON messages from the {@code transactions} topic.
     *
     * <p>Each message is deserialized into a {@link TransactionEvent} and
     * pushed into the transaction sink for broadcast to SSE subscribers.
     * Malformed messages are logged and skipped.
     *
     * @param message raw JSON string from Kafka
     */
    @KafkaListener(topics = "transactions", groupId = "dashboard-tx-group")
    public void listenTransactions(String message) {
        try {
            TransactionEvent event = OBJECT_MAPPER.readValue(message, TransactionEvent.class);
            logger.debug("Received transaction: {}", event.getTransactionId());
            transactionSink.tryEmitNext(event);
        } catch (Exception e) {
            logger.error("Failed to deserialize transaction: {}", e.getMessage());
        }
    }

    /**
     * Consumes raw JSON messages from the {@code fraud-alerts} topic.
     *
     * @param message raw JSON string from Kafka
     */
    @KafkaListener(topics = "fraud-alerts", groupId = "dashboard-alert-group")
    public void listenAlerts(String message) {
        try {
            AlertEvent event = OBJECT_MAPPER.readValue(message, AlertEvent.class);
            logger.info("Fraud alert received: alertId={}, severity={}", event.getAlertId(), event.getSeverity());
            alertSink.tryEmitNext(event);
        } catch (Exception e) {
            logger.error("Failed to deserialize alert: {}", e.getMessage());
        }
    }

    // =========================================================================
    // Stream accessors (used by SseController)
    // =========================================================================

    /**
     * Returns a {@link Flux} of all incoming transactions.
     * Each SSE subscriber receives its own independent subscription.
     */
    public Flux<TransactionEvent> getTransactionStream() {
        return transactionSink.asFlux();
    }

    /**
     * Returns a {@link Flux} of all fraud alerts.
     */
    public Flux<AlertEvent> getAlertStream() {
        return alertSink.asFlux();
    }
}
