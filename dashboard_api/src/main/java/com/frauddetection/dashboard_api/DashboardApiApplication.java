package com.frauddetection.dashboard_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Entry point for the Fraud Detection Dashboard API.
 *
 * This service acts as the "Secure Bridge" between the Kafka event bus
 * and the React frontend. It consumes messages from Kafka topics and
 * re-broadcasts them to connected browser clients via Server-Sent Events.
 *
 * @EnableKafka activates Spring's annotation-driven Kafka listener
 * infrastructure, allowing @KafkaListener methods to be discovered.
 *
 * @SpringBootApplication combines three annotations:
 *   - @Configuration:    marks this class as a source of bean definitions
 *   - @ComponentScan:    auto-discovers @Service, @Controller, etc.
 *   - @EnableAutoConfiguration: configures beans based on classpath dependencies
 */
@SpringBootApplication
@EnableKafka
public class DashboardApiApplication {

	public static void main(String[] args) {
		// Boots the Spring context and starts the embedded Netty server
		// (Netty is used instead of Tomcat because we chose WebFlux)
		SpringApplication.run(DashboardApiApplication.class, args);
	}

}
