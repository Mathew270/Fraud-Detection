package com.frauddetection.cluster_controller.grpc;

import com.frauddetection.cluster_controller.orchestrator.OrchestratorService;
import com.frauddetection.cluster_controller.service.SimulationConfigPublisher;

import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;

// =============================================================================
// ClusterServiceImpl — The gRPC Endpoint Handler
//
// This is the HEART of the Cluster Controller. It's the class that actually
// receives incoming gRPC requests and processes them.
//
// HOW GRPC METHODS WORK:
//   In REST, you write @GetMapping("/health") and Spring calls your method
//   when someone hits that URL.
//   In gRPC, you extend a generated base class (*ImplBase) and override methods.
//   Each method you defined in the .proto file becomes an overridable Java method.
//
//   For example, the proto definition:
//     rpc ScaleWorker (ScaleRequest) returns (ScaleResponse);
//   becomes this Java method signature:
//     public void scaleWorker(ScaleRequest request, StreamObserver<ScaleResponse> responseObserver)
//
// WHAT IS StreamObserver?
//   In REST, you just return an object and Spring converts it to JSON.
//   In gRPC, the response is sent through a StreamObserver. You call:
//     responseObserver.onNext(response);    // "Here's the data"
//     responseObserver.onCompleted();       // "I'm done, close the connection"
//
//   This pattern exists because gRPC supports STREAMING responses (sending
//   multiple messages over time), not just single responses like REST.
//   For our simple request-response methods, we always call onNext exactly once.
//
// WHAT IS @GrpcService?
//   It's like @RestController but for gRPC. It tells the grpc-spring-boot-starter:
//   "Register this class as a gRPC service handler on port 9090."
// =============================================================================
@GrpcService
public class ClusterServiceImpl extends ClusterServiceGrpc.ClusterServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(ClusterServiceImpl.class);

    // The OrchestratorService is injected by Spring.
    // Thanks to the Strategy Pattern, this could be DockerOrchestratorServiceImpl
    // OR KubernetesOrchestratorServiceImpl, depending on the active profile.
    // This class doesn't know or care which one it is — it just calls the interface.
    private final OrchestratorService orchestrator;

    // The SimulationConfigPublisher handles broadcasting config to Redis.
    private final SimulationConfigPublisher configPublisher;

    // A safety list of services that are ALLOWED to be scaled.
    // This prevents someone from accidentally scaling "kafka" or "redis"
    // through the API, which would break the infrastructure.
    // These values come from application.yml → cluster.scalable-services.
    @Value("${cluster.scalable-services}")
    private List<String> scalableServices;

    /**
     * Constructor injection — Spring provides the correct implementations.
     */
    public ClusterServiceImpl(OrchestratorService orchestrator,
                              SimulationConfigPublisher configPublisher) {
        this.orchestrator = orchestrator;
        this.configPublisher = configPublisher;
    }

    // =========================================================================
    // RPC: ScaleWorker
    //
    // Called when someone sends a ScaleRequest via gRPC.
    // This is equivalent to a "POST /api/cluster/scale" REST endpoint.
    //
    // Example gRPC call (via grpcurl):
    //   grpcurl -plaintext -d '{"service_name":"producer","replicas":5}' \
    //     localhost:9090 cluster.ClusterService/ScaleWorker
    // =========================================================================
    @Override
    public void scaleWorker(ScaleRequest request,
                            StreamObserver<ScaleResponse> responseObserver) {

        String serviceName = request.getServiceName();
        int replicas = request.getReplicas();

        log.info("Received ScaleWorker request: service='{}', replicas={}", serviceName, replicas);

        // SAFETY CHECK: Only allow scaling services that are in our whitelist.
        // This is a basic form of authorization — in production, you'd use
        // RBAC (Role-Based Access Control) for more granular permissions.
        if (!scalableServices.contains(serviceName)) {
            log.warn("Rejected scale request for non-scalable service: {}", serviceName);

            ScaleResponse response = ScaleResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Service '" + serviceName + "' is not in the list of scalable services. "
                            + "Allowed services: " + scalableServices)
                    .setCurrentReplicas(-1)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
            return;
        }

        // VALIDATION: Replicas must be a non-negative number.
        if (replicas < 0) {
            ScaleResponse response = ScaleResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Replica count must be >= 0. Received: " + replicas)
                    .setCurrentReplicas(-1)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
            return;
        }

        try {
            // Delegate the actual scaling to the OrchestratorService.
            // If the profile is "docker", this calls DockerOrchestratorServiceImpl.
            // If the profile is "kubernetes", this calls the K8s implementation.
            String resultMessage = orchestrator.scaleService(serviceName, replicas);

            // After scaling, check how many containers are actually running now.
            int currentCount = orchestrator.getRunningCount(serviceName);

            ScaleResponse response = ScaleResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage(resultMessage)
                    .setCurrentReplicas(currentCount)
                    .build();

            log.info("ScaleWorker completed: {}", resultMessage);
            responseObserver.onNext(response);

        } catch (Exception e) {
            log.error("ScaleWorker failed for service '{}'", serviceName, e);

            ScaleResponse response = ScaleResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Scaling failed: " + e.getMessage())
                    .setCurrentReplicas(-1)
                    .build();

            responseObserver.onNext(response);
        }

        // Always call onCompleted() to signal "this RPC is finished."
        // Forgetting this will cause the client to hang forever waiting.
        responseObserver.onCompleted();
    }

    // =========================================================================
    // RPC: UpdateSimulationConfig
    //
    // Called when someone sends a SimulationConfigRequest via gRPC.
    // This publishes new simulation parameters to Redis so all Python
    // producers pick them up instantly.
    //
    // Example gRPC call:
    //   grpcurl -plaintext -d '{"num_users":50,"burst_probability":0.4,"speed_multiplier":2.0}' \
    //     localhost:9090 cluster.ClusterService/UpdateSimulationConfig
    // =========================================================================
    @Override
    public void updateSimulationConfig(SimulationConfigRequest request,
                                       StreamObserver<SimulationConfigResponse> responseObserver) {

        int numUsers = request.getNumUsers();
        double burstProbability = request.getBurstProbability();
        double speedMultiplier = request.getSpeedMultiplier();

        log.info("Received UpdateSimulationConfig: users={}, burst={}, speed={}",
                numUsers, burstProbability, speedMultiplier);

        try {
            // Delegate to the SimulationConfigPublisher.
            // It handles the Redis PUBLISH and also stores the latest config
            // as a persistent key for new producers that start up later.
            configPublisher.publishConfig(numUsers, burstProbability, speedMultiplier);

            SimulationConfigResponse response = SimulationConfigResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Config broadcasted: users=" + numUsers
                            + ", burst=" + burstProbability
                            + ", speed=" + speedMultiplier)
                    .build();

            responseObserver.onNext(response);

        } catch (Exception e) {
            log.error("UpdateSimulationConfig failed", e);

            SimulationConfigResponse response = SimulationConfigResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Config broadcast failed: " + e.getMessage())
                    .build();

            responseObserver.onNext(response);
        }

        responseObserver.onCompleted();
    }

    // =========================================================================
    // RPC: GetClusterHealth
    //
    // Called when someone asks "How is service X doing?"
    // Returns the number of running containers and a status string.
    //
    // Example gRPC call:
    //   grpcurl -plaintext -d '{"service_name":"fraud-detector"}' \
    //     localhost:9090 cluster.ClusterService/GetClusterHealth
    // =========================================================================
    @Override
    public void getClusterHealth(HealthRequest request,
                                 StreamObserver<HealthResponse> responseObserver) {

        String serviceName = request.getServiceName();
        log.info("Received GetClusterHealth request for service: '{}'", serviceName);

        try {
            int activeReplicas = orchestrator.getRunningCount(serviceName);
            String status = orchestrator.getServiceStatus(serviceName);

            HealthResponse response = HealthResponse.newBuilder()
                    .setActiveReplicas(activeReplicas)
                    .setStatus(status)
                    .setServiceName(serviceName)
                    .build();

            log.info("ClusterHealth for '{}': {} replicas, status={}",
                    serviceName, activeReplicas, status);
            responseObserver.onNext(response);

        } catch (Exception e) {
            log.error("GetClusterHealth failed for service '{}'", serviceName, e);

            HealthResponse response = HealthResponse.newBuilder()
                    .setActiveReplicas(-1)
                    .setStatus("UNKNOWN")
                    .setServiceName(serviceName)
                    .build();

            responseObserver.onNext(response);
        }

        responseObserver.onCompleted();
    }
}
