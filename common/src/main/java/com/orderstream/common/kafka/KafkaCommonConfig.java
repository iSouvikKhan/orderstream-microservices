package com.orderstream.common.kafka;

import com.orderstream.common.event.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Shared Kafka setup: topics, and an error handler that retries with exponential back-off and
 * then parks the message on {@code <topic>.DLT} instead of blocking the partition.
 */
@Configuration
@EnableScheduling
public class KafkaCommonConfig {

    private static final int PARTITIONS = 3;

    @Bean
    public NewTopic orderEventsTopic() {
        return TopicBuilder.name(Topics.ORDER_EVENTS).partitions(PARTITIONS).replicas(1).build();
    }

    @Bean
    public NewTopic inventoryEventsTopic() {
        return TopicBuilder.name(Topics.INVENTORY_EVENTS).partitions(PARTITIONS).replicas(1).build();
    }

    @Bean
    public NewTopic paymentEventsTopic() {
        return TopicBuilder.name(Topics.PAYMENT_EVENTS).partitions(PARTITIONS).replicas(1).build();
    }

    @Bean
    public NewTopic orderEventsDlt() {
        return TopicBuilder.name(Topics.ORDER_EVENTS + ".DLT").partitions(PARTITIONS).replicas(1).build();
    }

    @Bean
    public NewTopic inventoryEventsDlt() {
        return TopicBuilder.name(Topics.INVENTORY_EVENTS + ".DLT").partitions(PARTITIONS).replicas(1).build();
    }

    @Bean
    public NewTopic paymentEventsDlt() {
        return TopicBuilder.name(Topics.PAYMENT_EVENTS + ".DLT").partitions(PARTITIONS).replicas(1).build();
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
        ExponentialBackOff backOff = new ExponentialBackOff(500L, 2.0);
        backOff.setMaxElapsedTime(10_000L);
        DefaultErrorHandler handler = new DefaultErrorHandler(new DeadLetterPublishingRecoverer(template), backOff);
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        return handler;
    }
}
