package com.orderstream.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderstream.common.event.EventEnvelope;
import com.orderstream.common.event.EventTypes;
import com.orderstream.common.event.Events;
import com.orderstream.common.event.Topics;
import com.orderstream.notification.domain.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Testcontainers
class NotificationIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @Autowired KafkaTemplate<String, String> kafkaTemplate;
    @Autowired NotificationRepository notifications;
    @Autowired ObjectMapper mapper;

    @Test
    void notifiesExactlyOncePerOutcomeEvent() throws Exception {
        UUID orderId = UUID.randomUUID();
        String confirmed = mapper.writeValueAsString(new EventEnvelope(UUID.randomUUID(), EventTypes.ORDER_CONFIRMED,
                orderId.toString(), Instant.now(),
                mapper.valueToTree(new Events.OrderConfirmed(orderId, "gina", new BigDecimal("99.00")))));

        kafkaTemplate.send(Topics.ORDER_EVENTS, orderId.toString(), confirmed).get();
        kafkaTemplate.send(Topics.ORDER_EVENTS, orderId.toString(), confirmed).get();

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(notifications.findByOrderId(orderId)).hasSize(1));
        Thread.sleep(1_000);
        assertThat(notifications.findByOrderId(orderId)).hasSize(1);
    }
}
