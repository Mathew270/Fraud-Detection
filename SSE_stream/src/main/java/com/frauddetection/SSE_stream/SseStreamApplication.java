package com.frauddetection.sse_stream;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Entry point for the Fraud Detection Dashboard API.
 *
 * This Spring Boot application acts as a proxy between the Kafka event bus
 * and browser-based clients. It consumes messages from the {@code transactions}
 * and {@code fraud-alerts} Kafka topics and re-broadcasts them to connected
 * clients via Server-Sent Events (SSE).
 *
 * <p>{@code @EnableKafka} activates Spring's annotation-driven Kafka listener
 * infrastructure, enabling {@code @KafkaListener} discovery in
 * {@link com.frauddetection.sse_stream.service.KafkaConsumerService}.
 *
 * <p>The application runs on an embedded Netty server (provided by
 * {@code spring-boot-starter-webflux}) on port 8085.
 *
 * @see com.frauddetection.sse_stream.config.KafkaConfig
 * @see com.frauddetection.sse_stream.controller.SseController
 */
@SpringBootApplication
@EnableKafka
public class SseStreamApplication {

	public static void main(String[] args) {
		SpringApplication.run(SseStreamApplication.class, args);
	}

}
