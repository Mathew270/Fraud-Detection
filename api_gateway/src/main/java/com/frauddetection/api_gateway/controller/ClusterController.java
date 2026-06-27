package com.frauddetection.api_gateway.controller;

import com.frauddetection.api_gateway.dto.ConfigRequestDto;
import com.frauddetection.api_gateway.dto.ScaleRequestDto;
import com.frauddetection.cluster_controller.grpc.*;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;

/**
 * ClusterController acts as the REST-to-gRPC translator.
 * It exposes REST endpoints to the frontend, translates the JSON requests into
 * Protobuf payloads, calls the cluster-controller service over gRPC, and
 * returns the results as JSON.
 *
 * To avoid blocking Netty's reactive event loop, gRPC calls are wrapped in Mono
 * and scheduled on WebFlux's boundedElastic scheduler.
 */
@RestController
@RequestMapping("/api/cluster")
public class ClusterController {

    @GrpcClient("cluster-controller")
    private ClusterServiceGrpc.ClusterServiceBlockingStub clusterServiceStub;

    /**
     * Translates GET /api/cluster/health/{service} to GetClusterHealth RPC.
     */
    @GetMapping("/health/{service}")
    public Mono<ResponseEntity<Map<String, Object>>> getHealth(@PathVariable String service) {
        return Mono.fromCallable(() -> {
            HealthRequest request = HealthRequest.newBuilder()
                    .setServiceName(service)
                    .build();
            HealthResponse response = clusterServiceStub.getClusterHealth(request);
            return ResponseEntity.ok(Map.<String, Object>of(
                    "serviceName", response.getServiceName(),
                    "activeReplicas", response.getActiveReplicas(),
                    "status", response.getStatus()
            ));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Translates POST /api/cluster/scale to ScaleWorker RPC.
     */
    @PostMapping("/scale")
    public Mono<ResponseEntity<Map<String, Object>>> scale(@RequestBody ScaleRequestDto dto) {
        return Mono.fromCallable(() -> {
            ScaleRequest request = ScaleRequest.newBuilder()
                    .setServiceName(dto.service())
                    .setReplicas(dto.replicas())
                    .build();
            ScaleResponse response = clusterServiceStub.scaleWorker(request);
            return ResponseEntity.ok(Map.<String, Object>of(
                    "success", response.getSuccess(),
                    "message", response.getMessage(),
                    "currentReplicas", response.getCurrentReplicas()
            ));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Translates POST /api/cluster/config to UpdateSimulationConfig RPC.
     */
    @PostMapping("/config")
    public Mono<ResponseEntity<Map<String, Object>>> updateConfig(@RequestBody ConfigRequestDto dto) {
        return Mono.fromCallable(() -> {
            SimulationConfigRequest.Builder builder = SimulationConfigRequest.newBuilder();
            if (dto.numUsers() != null) {
                builder.setNumUsers(dto.numUsers());
            }
            if (dto.burstProbability() != null) {
                builder.setBurstProbability(dto.burstProbability());
            }
            if (dto.speedMultiplier() != null) {
                builder.setSpeedMultiplier(dto.speedMultiplier());
            }
            SimulationConfigResponse response = clusterServiceStub.updateSimulationConfig(builder.build());
            return ResponseEntity.ok(Map.<String, Object>of(
                    "success", response.getSuccess(),
                    "message", response.getMessage()
            ));
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
