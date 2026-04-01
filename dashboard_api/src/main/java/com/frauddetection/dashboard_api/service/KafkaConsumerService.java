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
 * The "Secure Bridge" — the heart of the Kafka-to-SSE proxy architecture.
 *
 * This service has TWO responsibilities:
 *   1. LISTEN to Kafka topics ('transactions' and 'fraud-alerts')
 *   2. MULTICAST events into Project Reactor Sinks
 *
 * The Sinks act as in-memory "broadcast channels". When the SseController
 * subscribes to getSomethingStream(), it gets a Flux that emits every event
 * the Kafka listener receives. Multiple browser clients can subscribe
 * simultaneously — the Sink handles fan-out automatically.
 *
 * Why Sinks.Many.multicast().directBestEffort()?
 *   - multicast():       only active subscribers receive events (no replay)
 *   - directBestEffort(): if a subscriber can't keep up, drop the event
 *                         rather than buffering indefinitely (prevents OOM)
 */
@Service
public class KafkaConsumerService {

    private static final Logger logger = LoggerFactory.getLogger(KafkaConsumerService.class);

    // Jackson ObjectMapper for JSON string -> Java POJO conversion.
    // Spring auto-configures one; we receive it via constructor injection.
    private final ObjectMapper objectMapper;

    // Reactive broadcast channels. Events pushed here are fanned out
    // to every active SSE subscriber connected via the SseController.
    private final Sinks.Many<TransactionEvent> transactionSink =
            Sinks.many().multicast().directBestEffort();

    private final Sinks.Many<AlertEvent> alertSink =
            Sinks.many().multicast().directBestEffort();

    /**
     * Constructor injection — Spring automatically provides the ObjectMapper.
     * No @Autowired needed; Spring infers it from the single constructor.
     */
    public KafkaConsumerService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // =========================================================================
    // Kafka Listeners — these methods are called automatically by Spring Kafka
    // whenever a new message arrives on the subscribed topic.
    // =========================================================================

    /**
     * Consumes raw JSON strings from the 'transactions' topic.
     *
     * Each message is deserialized into a TransactionEvent and pushed
     * into the transaction Sink for broadcast to SSE subscribers.
     *
     * The groupId must be unique to this service so it doesn't compete
     * with the Python fraud-detector-group for the same messages.
     */
    @KafkaListener(topics = "transactions", groupId = "dashboard-tx-group")
    public void listenTransactions(String message) {
        try {
            // Deserialize the raw Kafka JSON string into our Java DTO
            TransactionEvent event = objectMapper.readValue(message, TransactionEvent.class);
            logger.debug("Received transaction: {}", event.getTransactionId());

            // Push into the reactive sink — all SSE subscribers will receive this
            transactionSink.tryEmitNext(event);
        } catch (Exception e) {
            // Log and continue — a single malformed message shouldn't crash the service
            logger.error("Failed to deserialize transaction: {}", e.getMessage());
        }
    }

    /**
     * Consumes raw JSON strings from the 'fraud-alerts' topic.
     * These are high-priority events that the dashboard should highlight.
     */
    @KafkaListener(topics = "fraud-alerts", groupId = "dashboard-alert-group")
    public void listenAlerts(String message) {
        try {
            AlertEvent event = objectMapper.readValue(message, AlertEvent.class);
            logger.info("FRAUD ALERT received: {} | severity={}", event.getAlertId(), event.getSeverity());

            alertSink.tryEmitNext(event);
        } catch (Exception e) {
            logger.error("Failed to deserialize alert: {}", e.getMessage());
        }
    }

    // =========================================================================
    // Public stream accessors — used by SseController to create SSE endpoints
    // =========================================================================

    /**
     * Returns a Flux of all incoming transactions.
     * Each SSE subscriber gets their own subscription to this Flux.
     */
    public Flux<TransactionEvent> getTransactionStream() {
        return transactionSink.asFlux();
    }

    /**
     * Returns a Flux of all fraud alerts.
     */
    public Flux<AlertEvent> getAlertStream() {
        return alertSink.asFlux();
    }
}
