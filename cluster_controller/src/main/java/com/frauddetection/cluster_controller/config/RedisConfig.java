package com.frauddetection.cluster_controller.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

// =============================================================================
// RedisConfig — Redis Pub/Sub Publisher Configuration
//
// WHAT IS REDIS PUB/SUB?
//   Pub/Sub stands for "Publish/Subscribe." It's a messaging pattern where:
//     - A "Publisher" sends a message to a named channel (like a radio station).
//     - All "Subscribers" listening to that channel receive the message instantly.
//
//   In our system:
//     - The Cluster Controller is the PUBLISHER.
//       When it receives a gRPC request to change simulation settings,
//       it publishes the new config as a JSON string to a Redis channel.
//     - The Python producer workers are the SUBSCRIBERS.
//       Each producer has a background thread listening to the same channel.
//       The moment a message arrives, the producer updates its internal
//       variables (burst_probability, num_users, etc.) without restarting.
//
// WHY REDIS AND NOT KAFKA FOR THIS?
//   Kafka is designed for "durable event logs" — messages are stored on disk
//   and can be replayed. That's great for transaction data.
//   Redis Pub/Sub is designed for "fire-and-forget broadcasts" — messages
//   are delivered instantly but NOT stored. If a producer is down when the
//   config changes, it simply misses the message. That's fine because:
//     1. Config changes are rare (a human clicks a button).
//     2. When the producer restarts, it can read the latest config from
//        a Redis key (not the Pub/Sub channel) as a fallback.
//
//   Using Kafka for config broadcasting would be overkill and add unnecessary
//   complexity.
// =============================================================================
@Configuration
public class RedisConfig {

    /**
     * Creates a StringRedisTemplate bean.
     *
     * StringRedisTemplate is a Spring helper that simplifies Redis operations.
     * It's pre-configured to work with String keys and String values, which
     * is perfect for our use case (we publish JSON strings to channels).
     *
     * Spring automatically injects the RedisConnectionFactory based on the
     * connection settings in application.yml (host, port).
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
