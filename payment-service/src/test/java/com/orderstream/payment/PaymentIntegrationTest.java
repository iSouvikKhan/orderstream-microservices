package com.orderstream.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderstream.common.event.EventEnvelope;
import com.orderstream.common.event.EventTypes;
import com.orderstream.common.event.Events;
import com.orderstream.common.event.Topics;
import com.orderstream.common.outbox.OutboxEventRepository;
import com.orderstream.payment.domain.Payment;
import com.orderstream.payment.domain.PaymentRepository;
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

@SpringBootTest(properties = {
        "orderstream.outbox.poll-interval-ms=100",
        "payment.provider.max-charge=1000.00"})
@Testcontainers
class PaymentIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @Autowired KafkaTemplate<String, String> kafkaTemplate;
    @Autowired PaymentRepository payments;
    @Autowired OutboxEventRepository outbox;
    @Autowired ObjectMapper mapper;

    @Test
    void chargesOnceForAReservedOrder() throws Exception {
        UUID orderId = UUID.randomUUID();
        String reserved = reserved(orderId, "250.00");
        send(orderId, reserved);
        send(orderId, reserved); // redelivery

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(payments.findByOrderId(orderId)).get()
                    .extracting(Payment::getStatus).isEqualTo(Payment.Status.COMPLETED);
            assertThat(outbox.countByAggregateIdAndEventType(orderId.toString(), EventTypes.PAYMENT_COMPLETED))
                    .isEqualTo(1);
        });
    }

    @Test
    void declinesChargesOverTheLimit() throws Exception {
        UUID orderId = UUID.randomUUID();
        send(orderId, reserved(orderId, "1500.00"));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(payments.findByOrderId(orderId)).get()
                    .extracting(Payment::getStatus).isEqualTo(Payment.Status.FAILED);
            assertThat(outbox.countByAggregateIdAndEventType(orderId.toString(), EventTypes.PAYMENT_FAILED))
                    .isEqualTo(1);
        });
    }

    private void send(UUID orderId, String value) throws Exception {
        kafkaTemplate.send(Topics.INVENTORY_EVENTS, orderId.toString(), value).get();
    }

    private String reserved(UUID orderId, String amount) throws Exception {
        var payload = new Events.InventoryReserved(orderId, "frank", new BigDecimal(amount));
        return mapper.writeValueAsString(new EventEnvelope(UUID.randomUUID(), EventTypes.INVENTORY_RESERVED,
                orderId.toString(), Instant.now(), mapper.valueToTree(payload)));
    }
}
