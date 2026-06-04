package com.frauddetection.cluster_controller;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// =============================================================================
// ClusterControllerApplication — The Entry Point
//
// This is the main class that boots up the Cluster Controller microservice.
// When Spring starts, it automatically:
//   1. Scans this package (and sub-packages) for beans like @GrpcService,
//      @Service, @Configuration, etc.
//   2. Starts the gRPC server on port 9090 (configured in application.yml).
//   3. Connects to Redis (configured in application.yml).
//
// WHAT THIS SERVICE DOES:
//   The Cluster Controller is the "brain" of the fraud detection pipeline.
//   It receives commands (via gRPC) to scale up/down Docker containers,
//   change simulation parameters, and report cluster health status.
//   Think of it as the "control tower" of an airport — it doesn't fly planes,
//   but it tells every plane where to go.
// =============================================================================
@SpringBootApplication
public class ClusterControllerApplication {

	public static void main(String[] args) {
		SpringApplication.run(ClusterControllerApplication.class, args);
	}

}
