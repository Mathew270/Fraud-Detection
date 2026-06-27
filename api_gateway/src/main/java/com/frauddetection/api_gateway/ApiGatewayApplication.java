package com.frauddetection.api_gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ApiGatewayApplication is the main entry point for the API Gateway service.
 * It serves as a Spring Cloud Gateway reactive reverse proxy, as well as a
 * REST-to-gRPC gateway controller translating frontend requests to the cluster-controller.
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
