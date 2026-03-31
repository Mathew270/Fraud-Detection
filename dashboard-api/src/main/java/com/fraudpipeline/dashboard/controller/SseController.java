package com.fraudpipeline.dashboard.controller;

import com.fraudpipeline.dashboard.model.AlertEvent;
import com.fraudpipeline.dashboard.model.TransactionEvent;
import com.fraudpipeline.dashboard.service.KafkaConsumerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.time.Duration;

/**
 * Controller for Server-Sent Events (SSE).
 * This acts as the "Observability Hub" where the React Dashboard connects.
 */
@RestController
@RequestMapping("/api/stream")
public class SseController {

    private static final Logger logger = LoggerFactory.getLogger(SseController.class);
    
    private final KafkaConsumerService kafkaConsumerService;

    public SseController(KafkaConsumerService kafkaConsumerService) {
        this.kafkaConsumerService = kafkaConsumerService;
    }

    /**
     * Stream of all incoming transactions.
     * 
     * Content-Type: text/event-stream
     * This keeps a persistent connection open with the browser.
     */
    @GetMapping(value = "/transactions", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<TransactionEvent> streamTransactions() {
        logger.info("📡 New UI subscriber connected to /transactions stream");
        
        // Directly pipe the Flux from our Kafka service to the HTTP response
        return kafkaConsumerService.getTransactionStream()
                .onBackpressureDrop(dropped -> logger.warn("⚠️ Backpressure: Dropped Transaction Event for user {}", dropped.getUserId()));
    }

    /**
     * Dedicated stream for high-priority fraud alerts.
     */
    @GetMapping(value = "/alerts", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<AlertEvent> streamAlerts() {
        logger.info("📡 New UI subscriber connected to /alerts stream");
        
        return kafkaConsumerService.getAlertStream();
    }
}
