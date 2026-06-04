package com.frauddetection.cluster_controller.orchestrator;

// =============================================================================
// OrchestratorService — The Strategy Pattern Interface
//
// WHAT IS THE STRATEGY PATTERN?
//   Imagine you have a GPS app that can give directions by car, by bus, or by
//   walking. The destination is the same, but the "strategy" for getting there
//   is different. The Strategy Pattern works the same way in code:
//     - This interface defines WHAT we want to do (scale services, check health).
//     - Different implementations define HOW to do it (Docker vs Kubernetes).
//
// WHY DO WE NEED THIS?
//   Right now, our system runs on Docker Compose. But in the future, we plan
//   to migrate to Kubernetes. Without this interface, switching would mean
//   rewriting every class that touches Docker. With it, we just swap ONE
//   implementation and everything else stays the same.
//
// HOW SPRING SELECTS THE RIGHT IMPLEMENTATION:
//   Each implementation is annotated with @Profile("docker") or @Profile("kubernetes").
//   Spring reads the active profile from application.yml and automatically
//   injects the correct implementation. You never write an "if docker else k8s"
//   statement anywhere.
//
//   application.yml:  spring.profiles.active: docker
//     → Spring creates DockerOrchestratorServiceImpl
//
//   application.yml:  spring.profiles.active: kubernetes
//     → Spring creates KubernetesOrchestratorServiceImpl (future)
// =============================================================================
public interface OrchestratorService {

    /**
     * Scale a specific Docker Compose service to the desired number of replicas.
     *
     * @param serviceName The name of the service as defined in docker-compose.yml
     *                    (e.g., "producer", "fraud-detector").
     * @param replicas    The target number of running containers.
     * @return A human-readable result message describing what happened.
     * @throws Exception if the scaling command fails (e.g., Docker is not running).
     */
    String scaleService(String serviceName, int replicas) throws Exception;

    /**
     * Get the number of currently running containers for a specific service.
     *
     * @param serviceName The name of the service to inspect.
     * @return The count of containers in the "running" state.
     * @throws Exception if the health check command fails.
     */
    int getRunningCount(String serviceName) throws Exception;

    /**
     * Get a status string describing the overall health of a service.
     * Possible return values: "HEALTHY", "DEGRADED", "STOPPED".
     *
     * @param serviceName The name of the service to inspect.
     * @return A status string.
     * @throws Exception if the health check command fails.
     */
    String getServiceStatus(String serviceName) throws Exception;
}
