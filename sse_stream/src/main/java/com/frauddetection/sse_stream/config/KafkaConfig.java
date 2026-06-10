package com.frauddetection.sse_stream.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka consumer configuration.
 *
 * Spring Boot auto-configures the {@code kafkaListenerContainerFactory} bean
 * when using {@code spring-boot-starter-web} (Servlet stack). However, this
 * project uses {@code spring-boot-starter-webflux} (reactive stack) for its
 * non-blocking SSE streaming capabilities. The WebFlux auto-configuration
 * does not register the listener container factory, so it must be defined
 * explicitly here.
 *
 * This factory controls how {@code @KafkaListener} methods in
 * {@link com.frauddetection.sse_stream.service.KafkaConsumerService}
 * are connected to the Kafka broker. All consumer properties are read
 * from {@code application.yml} via {@code @Value} injection.
 *
 * @see com.frauddetection.sse_stream.service.KafkaConsumerService
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Value("${spring.kafka.consumer.auto-offset-reset}")
    private String autoOffsetReset;

    /**
     * Creates a {@link ConsumerFactory} that builds Kafka consumer instances.
     *
     * Messages are deserialized as plain strings. JSON-to-POJO conversion
     * is handled downstream in {@code KafkaConsumerService} using Jackson,
     * because this application consumes two topics with different schemas
     * (TransactionEvent and AlertEvent).
     */
    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * Registers the {@code kafkaListenerContainerFactory} bean.
     *
     * This is the default factory name that {@code @KafkaListener} looks for.
     * It wraps the {@link ConsumerFactory} and manages the lifecycle of
     * the listener threads that poll Kafka for new messages.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        return factory;
    }
}
