package com.frauddetection.cluster_controller.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

// =============================================================================
// SimulationConfigPublisher — The Redis Broadcasting Service
//
// This service is responsible for ONE thing: taking simulation configuration
// values (num_users, burst_probability, speed_multiplier) and broadcasting
// them to all running Python producer containers via Redis Pub/Sub.
//
// THE FLOW:
//   1. The frontend UI sends a REST request to the API Gateway.
//   2. The Gateway translates it into a gRPC call → ClusterServiceImpl.
//   3. ClusterServiceImpl calls THIS class.
//   4. This class serializes the config into a JSON string.
//   5. This class publishes the JSON to a Redis channel called "simulation-config".
//   6. Every Python producer subscribed to "simulation-config" receives it instantly.
//   7. The producers update their internal variables without restarting.
//
// WHY USE A SEPARATE CLASS FOR THIS?
//   We could put this logic directly inside ClusterServiceImpl, but that would
//   violate the Single Responsibility Principle. By extracting it into its own
//   class, we can:
//     - Test it independently (mock Redis, verify JSON format).
//     - Reuse it from other places if needed.
//     - Swap the implementation later (e.g., use Kafka instead of Redis).
// =============================================================================
@Service
public class SimulationConfigPublisher {

    private static final Logger log = LoggerFactory.getLogger(SimulationConfigPublisher.class);

    // The name of the Redis Pub/Sub channel we publish config updates to.
    // The Python producers must subscribe to this exact same channel name.
    private static final String CONFIG_CHANNEL = "simulation-config";

    // The Redis key where we ALSO store the latest config as a persistent value.
    // This acts as a "fallback" — if a new producer starts up AFTER the config
    // was broadcast, it can read this key to get the current config instead
    // of waiting for the next broadcast.
    private static final String CONFIG_KEY = "simulation:current-config";

    // StringRedisTemplate is Spring's helper for Redis string operations.
    // It's injected by Spring's dependency injection (constructor injection).
    private final StringRedisTemplate redisTemplate;

    // Jackson ObjectMapper converts Java objects into JSON strings.
    // We use it to serialize a Map<String, Object> into a JSON string
    // like: {"num_users": 50, "burst_probability": 0.3, "speed_multiplier": 1.5}
    private final ObjectMapper objectMapper;

    /**
     * Constructor injection — Spring automatically provides the dependencies.
     * This is the recommended way to inject dependencies in Spring (not @Autowired).
     */
    public SimulationConfigPublisher(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Broadcast a new simulation configuration to all running producers.
     *
     * This method does two things:
     *   1. STORES the config in a Redis key (for new producers to read on startup).
     *   2. PUBLISHES the config to a Redis channel (for running producers to react).
     *
     * @param numUsers         How many simulated users are in the active pool.
     * @param burstProbability The probability of transaction bursts (0.0 to 1.0).
     * @param speedMultiplier  How fast transactions are generated (1.0 = normal).
     * @throws Exception if Redis is unavailable or JSON serialization fails.
     */
    public void publishConfig(int numUsers, double burstProbability, double speedMultiplier) throws Exception {
        // Build a map of the config values.
        // We use a Map instead of a custom class because:
        //   1. It's simple and doesn't need its own file.
        //   2. The Python side reads it as a dictionary anyway.
        Map<String, Object> config = new HashMap<>();
        config.put("num_users", numUsers);
        config.put("burst_probability", burstProbability);
        config.put("speed_multiplier", speedMultiplier);

        // Convert the map to a JSON string.
        // ObjectMapper handles all the formatting and escaping for us.
        String jsonPayload = objectMapper.writeValueAsString(config);

        log.info("Publishing simulation config to Redis channel '{}': {}", CONFIG_CHANNEL, jsonPayload);

        // Step 1: Store the config persistently so new producers can read it.
        // opsForValue() gives us access to simple key-value operations in Redis.
        // This is like saving a file — it stays there until we overwrite it.
        redisTemplate.opsForValue().set(CONFIG_KEY, jsonPayload);

        // Step 2: Broadcast the config to all CURRENTLY subscribed producers.
        // convertAndSend() publishes to the Pub/Sub channel. Any container
        // that has subscribed to "simulation-config" will receive this message
        // within milliseconds. Containers that are not currently subscribed
        // (e.g., they haven't started yet) will miss this message, but they
        // can read CONFIG_KEY when they start up.
        redisTemplate.convertAndSend(CONFIG_CHANNEL, jsonPayload);

        log.info("Simulation config successfully published and stored.");
    }
}
