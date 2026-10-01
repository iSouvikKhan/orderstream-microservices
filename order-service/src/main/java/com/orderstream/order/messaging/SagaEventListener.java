package com.orderstream.order.messaging;

import com.orderstream.common.event.EventCodec;
import com.orderstream.common.event.EventEnvelope;
import com.orderstream.common.event.EventTypes;
import com.orderstream.common.event.Events;
import com.orderstream.common.event.Topics;
import com.orderstream.common.idempotency.IdempotencyGuard;
import com.orderstream.order.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Reacts to inventory and payment outcomes. */
@Component
public class SagaEventListener {

    private static final Logger log = LoggerFactory.getLogger(SagaEventListener.class);
    private static final String CONSUMER = "order-service";

    private final EventCodec codec;
    private final IdempotencyGuard idempotency;
    private final OrderService orders;

    public SagaEventListener(EventCodec codec, IdempotencyGuard idempotency, OrderService orders) {
        this.codec = codec;
        this.idempotency = idempotency;
        this.orders = orders;
    }

    @KafkaListener(topics = {Topics.INVENTORY_EVENTS, Topics.PAYMENT_EVENTS})
    @Transactional
    public void onMessage(String message) {
        EventEnvelope envelope = codec.readEnvelope(message);
        if (!idempotency.firstTime(envelope.eventId(), CONSUMER)) {
            log.debug("Skipping duplicate event {}", envelope.eventId());
            return;
        }
        switch (envelope.eventType()) {
            case EventTypes.INVENTORY_RESERVED -> {
                var e = codec.payload(envelope, Events.InventoryReserved.class);
                orders.onInventoryReserved(e.orderId());
            }
            case EventTypes.INVENTORY_REJECTED -> {
                var e = codec.payload(envelope, Events.InventoryRejected.class);
                orders.onInventoryRejected(e.orderId(), e.reason());
            }
            case EventTypes.PAYMENT_COMPLETED -> {
                var e = codec.payload(envelope, Events.PaymentCompleted.class);
                orders.onPaymentCompleted(e.orderId());
            }
            case EventTypes.PAYMENT_FAILED -> {
                var e = codec.payload(envelope, Events.PaymentFailed.class);
                orders.onPaymentFailed(e.orderId(), e.reason());
            }
            default -> log.debug("Ignoring {}", envelope.eventType());
        }
    }
}
