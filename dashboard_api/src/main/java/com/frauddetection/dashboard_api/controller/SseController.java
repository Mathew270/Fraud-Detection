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
 * SSE (Server-Sent Events) streaming controller.
 *
 * Exposes HTTP endpoints that keep connections open and push events to
 * clients in real time. The React frontend subscribes to these endpoints
 * using the browser's {@code EventSource} API.
 *
 * <p>Each endpoint returns a {@link Flux} that never completes. Spring
 * WebFlux serializes each emitted DTO to JSON and wraps it in the SSE
 * wire format ({@code data:{...}\n\n}), which the browser parses
 * automatically.
 *
 * <p>Data flow:
 * <pre>
 *   Python Producer → Kafka → KafkaConsumerService → Reactor Sink → SseController → Browser
 * </pre>
 *
 * @see com.frauddetection.dashboard_api.service.KafkaConsumerService
 */
@RestController
@RequestMapping("/api/stream")
@CrossOrigin(origins = "*") // TODO: Restrict to frontend origin in production
public class SseController {

    private static final Logger logger = LoggerFactory.getLogger(SseController.class);

    private final KafkaConsumerService kafkaConsumerService;

    public SseController(KafkaConsumerService kafkaConsumerService) {
        this.kafkaConsumerService = kafkaConsumerService;
    }

    /**
     * Streams all incoming financial transactions as SSE events.
     *
     * <p>{@code MediaType.TEXT_EVENT_STREAM_VALUE} sets the response
     * Content-Type to {@code text/event-stream}, enabling the browser's
     * EventSource to parse the response as a continuous event stream.
     *
     * <p>Backpressure is handled by dropping events that a slow client
     * cannot consume, preventing unbounded memory growth.
     *
     * @return an infinite Flux of {@link TransactionEvent} objects serialized as JSON
     */
    @GetMapping(value = "/transactions", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<TransactionEvent> streamTransactions() {
        logger.info("SSE subscriber connected: /api/stream/transactions");

        return kafkaConsumerService.getTransactionStream()
                .onBackpressureDrop(dropped ->
                    logger.warn("Dropped transaction event (backpressure): userId={}", dropped.getUserId())
                );
    }

    /**
     * Streams fraud alerts as SSE events.
     *
     * <p>These are high-priority events triggered by the Python fraud detector.
     * The frontend typically renders these with prominent visual indicators.
     *
     * @return an infinite Flux of {@link AlertEvent} objects serialized as JSON
     */
    @GetMapping(value = "/alerts", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<AlertEvent> streamAlerts() {
        logger.info("SSE subscriber connected: /api/stream/alerts");

        return kafkaConsumerService.getAlertStream();
    }
}
