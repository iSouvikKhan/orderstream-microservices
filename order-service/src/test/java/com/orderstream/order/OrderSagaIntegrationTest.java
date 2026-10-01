package com.orderstream.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderstream.common.event.EventEnvelope;
import com.orderstream.common.event.EventTypes;
import com.orderstream.common.event.Events;
import com.orderstream.common.event.Topics;
import com.orderstream.common.outbox.OutboxEvent;
import com.orderstream.common.outbox.OutboxEventRepository;
import com.orderstream.order.api.OrderDtos.CreateOrderRequest;
import com.orderstream.order.api.OrderDtos.Item;
import com.orderstream.order.api.OrderDtos.OrderResponse;
import com.orderstream.order.domain.OrderRepository;
import com.orderstream.order.domain.OrderStatus;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "orderstream.outbox.poll-interval-ms=100")
@Testcontainers
class OrderSagaIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @Autowired TestRestTemplate rest;
    @Autowired KafkaTemplate<String, String> kafkaTemplate;
    @Autowired OrderRepository orders;
    @Autowired OutboxEventRepository outbox;
    @Autowired ObjectMapper mapper;

    @Test
    void placingAnOrderPublishesOrderCreatedThroughTheOutbox() {
        UUID orderId = placeOrder("alice");

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            List<OutboxEvent> rows = outbox.findByAggregateIdOrderByCreatedAt(orderId.toString());
            assertThat(rows).singleElement().satisfies(r -> {
                assertThat(r.getEventType()).isEqualTo(EventTypes.ORDER_CREATED);
                assertThat(r.getPublishedAt()).isNotNull();
            });
        });

        try (KafkaConsumer<String, String> consumer = testConsumer()) {
            consumer.subscribe(List.of(Topics.ORDER_EVENTS));
            await().atMost(Duration.ofSeconds(15)).until(() -> {
                for (ConsumerRecord<String, String> r : consumer.poll(Duration.ofMillis(500))) {
                    if (r.key().equals(orderId.toString()) && r.value().contains(EventTypes.ORDER_CREATED)) {
                        return true;
                    }
                }
                return false;
            });
        }
    }

    @Test
    void paymentCompletedConfirmsTheOrderExactlyOnceEvenWhenRedelivered() throws Exception {
        UUID orderId = placeOrder("bob");
        String duplicated = envelope(EventTypes.PAYMENT_COMPLETED, orderId,
                new Events.PaymentCompleted(orderId, UUID.randomUUID(), new BigDecimal("40.00")));

        kafkaTemplate.send(Topics.PAYMENT_EVENTS, orderId.toString(), duplicated).get();
        kafkaTemplate.send(Topics.PAYMENT_EVENTS, orderId.toString(), duplicated).get();

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(orders.findById(orderId)).get()
                        .extracting(o -> o.getStatus()).isEqualTo(OrderStatus.CONFIRMED));
        // Give the duplicate time to be (not) processed, then check only one event was emitted.
        Thread.sleep(1_000);
        assertThat(outbox.countByAggregateIdAndEventType(orderId.toString(), EventTypes.ORDER_CONFIRMED))
                .isEqualTo(1);
    }

    @Test
    void paymentFailureCancelsTheOrderAndEmitsCompensation() throws Exception {
        UUID orderId = placeOrder("carol");

        kafkaTemplate.send(Topics.INVENTORY_EVENTS, orderId.toString(), envelope(EventTypes.INVENTORY_RESERVED,
                orderId, new Events.InventoryReserved(orderId, "carol", new BigDecimal("40.00")))).get();
        kafkaTemplate.send(Topics.PAYMENT_EVENTS, orderId.toString(), envelope(EventTypes.PAYMENT_FAILED,
                orderId, new Events.PaymentFailed(orderId, "card declined"))).get();

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            var order = orders.findById(orderId).orElseThrow();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(order.getFailureReason()).contains("card declined");
            assertThat(outbox.countByAggregateIdAndEventType(orderId.toString(), EventTypes.ORDER_CANCELLED))
                    .isEqualTo(1);
        });
    }

    @Test
    void requestsWithoutAnAuthenticatedUserAreRejected() {
        var request = new CreateOrderRequest(List.of(new Item("SKU-1", 1, BigDecimal.TEN)));
        ResponseEntity<String> response = rest.postForEntity("/api/orders", request, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private UUID placeOrder(String customer) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", customer);
        var request = new CreateOrderRequest(List.of(
                new Item("SKU-KEYBOARD", 1, new BigDecimal("30.00")),
                new Item("SKU-MOUSE", 2, new BigDecimal("5.00"))));
        ResponseEntity<OrderResponse> response =
                rest.postForEntity("/api/orders", new HttpEntity<>(request, headers), OrderResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody().totalAmount()).isEqualByComparingTo("40.00");
        assertThat(response.getBody().status()).isEqualTo(OrderStatus.PENDING);
        return response.getBody().id();
    }

    private String envelope(String type, UUID orderId, Object payload) throws Exception {
        return mapper.writeValueAsString(new EventEnvelope(UUID.randomUUID(), type, orderId.toString(),
                Instant.now(), mapper.valueToTree(payload)));
    }

    private KafkaConsumer<String, String> testConsumer() {
        return new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"),
                new StringDeserializer(), new StringDeserializer());
    }
}
