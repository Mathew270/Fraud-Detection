package com.frauddetection.dashboard_api.controller;

import com.frauddetection.dashboard_api.model.AlertEvent;
import com.frauddetection.dashboard_api.model.TransactionEvent;
import com.frauddetection.dashboard_api.service.KafkaConsumerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * SSE Controller — the "Observability Hub" of the Dashboard.
 *
 * This controller exposes Server-Sent Events (SSE) endpoints that the
 * React frontend subscribes to via the browser's EventSource API.
 * Each endpoint returns a Flux that never completes, keeping the HTTP
 * connection open and streaming events as they arrive from Kafka.
 *
 * Architecture flow:
 *   Python Producer → Kafka → KafkaConsumerService → Reactor Sink → THIS → Browser
 *
 * @CrossOrigin is required because the React dev server (localhost:5173)
 * runs on a different port than this API (localhost:8085). Without it,
 * the browser would block the SSE connection due to CORS policy.
 */
@RestController
@RequestMapping("/api/stream")
@CrossOrigin(origins = "*") // Allow all origins during development; lock down in production
public class SseController {

    private static final Logger logger = LoggerFactory.getLogger(SseController.class);

    // Injected by Spring — provides the reactive Flux streams from Kafka
    private final KafkaConsumerService kafkaConsumerService;

    public SseController(KafkaConsumerService kafkaConsumerService) {
        this.kafkaConsumerService = kafkaConsumerService;
    }

    /**
     * GET /api/stream/transactions
     *
     * Returns an SSE stream of all incoming financial transactions.
     * The browser connects once and receives a continuous flow of events.
     *
     * MediaType.TEXT_EVENT_STREAM_VALUE tells Spring to format the response
     * as SSE (Content-Type: text/event-stream), which the browser's
     * EventSource API knows how to parse.
     *
     * Backpressure handling: if a slow client can't keep up, we drop
     * events rather than buffering them (preventing memory leaks).
     */
    @GetMapping(value = "/transactions", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<TransactionEvent> streamTransactions() {
        logger.info("New SSE subscriber connected to /api/stream/transactions");

        return kafkaConsumerService.getTransactionStream()
                .onBackpressureDrop(dropped ->
                    logger.warn("Backpressure: dropped transaction for user {}", dropped.getUserId())
                );
    }

    /**
     * GET /api/stream/alerts
     *
     * Dedicated SSE stream for high-priority fraud alerts.
     * The React dashboard uses this to trigger visual notifications
     * (glowing red cards, sound effects, etc.)
     */
    @GetMapping(value = "/alerts", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<AlertEvent> streamAlerts() {
        logger.info("New SSE subscriber connected to /api/stream/alerts");

        return kafkaConsumerService.getAlertStream();
    }
}
