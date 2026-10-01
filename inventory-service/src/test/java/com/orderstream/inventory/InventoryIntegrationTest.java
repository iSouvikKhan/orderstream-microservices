package com.orderstream.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderstream.common.event.EventEnvelope;
import com.orderstream.common.event.EventTypes;
import com.orderstream.common.event.Events;
import com.orderstream.common.event.Topics;
import com.orderstream.common.outbox.OutboxEventRepository;
import com.orderstream.inventory.domain.ProductRepository;
import com.orderstream.inventory.domain.Reservation;
import com.orderstream.inventory.domain.ReservationRepository;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = "orderstream.outbox.poll-interval-ms=100")
@Testcontainers
class InventoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @Autowired KafkaTemplate<String, String> kafkaTemplate;
    @Autowired ProductRepository products;
    @Autowired ReservationRepository reservations;
    @Autowired OutboxEventRepository outbox;
    @Autowired ObjectMapper mapper;

    @Test
    void reservesStockOnceAndReleasesItOnCancellation() throws Exception {
        UUID orderId = UUID.randomUUID();
        int before = stock("SKU-MONITOR");
        String created = envelope(EventTypes.ORDER_CREATED, orderId, new Events.OrderCreated(orderId, "dave",
                List.of(new Events.OrderLine("SKU-MONITOR", 2, new BigDecimal("300.00"))), new BigDecimal("600.00")));

        // Delivered twice: the idempotent consumer must reserve only once.
        send(orderId, created);
        send(orderId, created);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(stock("SKU-MONITOR")).isEqualTo(before - 2);
            assertThat(outbox.countByAggregateIdAndEventType(orderId.toString(), EventTypes.INVENTORY_RESERVED))
                    .isEqualTo(1);
        });

        send(orderId, envelope(EventTypes.ORDER_CANCELLED, orderId,
                new Events.OrderCancelled(orderId, "dave", "Payment failed")));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(stock("SKU-MONITOR")).isEqualTo(before);
            assertThat(reservations.findByOrderId(orderId))
                    .allMatch(r -> r.getStatus() == Reservation.Status.RELEASED);
        });
    }

    @Test
    void rejectsOrdersThatExceedStockWithoutTouchingOtherLines() throws Exception {
        UUID orderId = UUID.randomUUID();
        int keyboardsBefore = stock("SKU-KEYBOARD");
        send(orderId, envelope(EventTypes.ORDER_CREATED, orderId, new Events.OrderCreated(orderId, "erin",
                List.of(new Events.OrderLine("SKU-KEYBOARD", 1, new BigDecimal("30.00")),
                        new Events.OrderLine("SKU-LAPTOP", 10_000, new BigDecimal("1500.00"))),
                new BigDecimal("15000030.00"))));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(outbox.countByAggregateIdAndEventType(orderId.toString(), EventTypes.INVENTORY_REJECTED))
                        .isEqualTo(1));
        assertThat(stock("SKU-KEYBOARD")).isEqualTo(keyboardsBefore);
        assertThat(reservations.findByOrderId(orderId)).isEmpty();
    }

    private int stock(String sku) {
        return products.findById(sku).orElseThrow().getAvailableQuantity();
    }

    private void send(UUID orderId, String value) throws Exception {
        kafkaTemplate.send(Topics.ORDER_EVENTS, orderId.toString(), value).get();
    }

    private String envelope(String type, UUID orderId, Object payload) throws Exception {
        return mapper.writeValueAsString(new EventEnvelope(UUID.randomUUID(), type, orderId.toString(),
                Instant.now(), mapper.valueToTree(payload)));
    }
}
