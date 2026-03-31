package com.fraudpipeline.dashboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * The entry point for the Fraud Detection Control Plane Dashboard API.
 * Uses @EnableKafka to activate Spring's Kafka listener capabilities.
 * 
 * Standard Spring Boot 3.2+ application.
 */
@SpringBootApplication
@EnableKafka
public class DashboardApiApplication {

	public static void main(String[] args) {
		// Boots the Spring context and starts the embedded server (Netty for WebFlux)
		SpringApplication.run(DashboardApiApplication.class, args);
	}

}
