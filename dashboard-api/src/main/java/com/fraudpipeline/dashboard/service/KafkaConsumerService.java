package com.fraudpipeline.dashboard.service;

import com.fraudpipeline.dashboard.model.AlertEvent;
import com.fraudpipeline.dashboard.model.TransactionEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * The "Bridge" Service.
 * Listens to Kafka topics and pushes data into Project Reactor Sinks.
 * These Sinks act as the multi-cast source for our SSE Controller.
 */
@Service
public class KafkaConsumerService {

    private static final Logger logger = LoggerFactory.getLogger(KafkaConsumerService.class);
    
    // ObjectMapper for JSON deserialization
    private final ObjectMapper objectMapper;

    // Sinks for multi-casting Kafka events to multiple SSE subscribers
    // .replay().limit(1) ensures new UI clients get the very last event immediately
    private final Sinks.Many<TransactionEvent> transactionSink = Sinks.many().multicast().directBestEffort();
    private final Sinks.Many<AlertEvent> alertSink = Sinks.many().multicast().directBestEffort();

    public KafkaConsumerService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Listener for the 'transactions' Kafka topic.
     * Converts raw JSON strings into TransactionEvent objects and pushes to the Sink.
     */
    @KafkaListener(topics = "${spring.kafka.template.default-topic:transactions}", groupId = "dashboard-tx-group")
    public void listenTransactions(String message) {
        try {
            TransactionEvent event = objectMapper.readValue(message, TransactionEvent.class);
            logger.debug("Received Transaction: {}", event.getUserId());
            
            // Push the event into the reactive sink
            transactionSink.tryEmitNext(event);
        } catch (Exception e) {
            logger.error("Error deserializing transaction: {}", e.getMessage());
        }
    }

    /**
     * Listener for the 'fraud-alerts' Kafka topic.
     */
    @KafkaListener(topics = "fraud-alerts", groupId = "dashboard-alert-group")
    public void listenAlerts(String message) {
        try {
            AlertEvent event = objectMapper.readValue(message, AlertEvent.class);
            logger.info("🚨 FRAUD ALERT RECEIVED: {}", event.getAlertId());
            
            // Push the alert into the reactive sink
            alertSink.tryEmitNext(event);
        } catch (Exception e) {
            logger.error("Error deserializing alert: {}", e.getMessage());
        }
    }

    // --- Public methods to expose the streams to the Controller ---

    public Flux<TransactionEvent> getTransactionStream() {
        return transactionSink.asFlux();
    }

    public Flux<AlertEvent> getAlertStream() {
        return alertSink.asFlux();
    }
}
