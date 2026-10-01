package com.orderstream.inventory.messaging;

import com.orderstream.common.event.EventCodec;
import com.orderstream.common.event.EventEnvelope;
import com.orderstream.common.event.EventTypes;
import com.orderstream.common.event.Events;
import com.orderstream.common.event.Topics;
import com.orderstream.common.idempotency.IdempotencyGuard;
import com.orderstream.inventory.service.InventoryService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OrderEventListener {

    private static final String CONSUMER = "inventory-service";

    private final EventCodec codec;
    private final IdempotencyGuard idempotency;
    private final InventoryService inventory;

    public OrderEventListener(EventCodec codec, IdempotencyGuard idempotency, InventoryService inventory) {
        this.codec = codec;
        this.idempotency = idempotency;
        this.inventory = inventory;
    }

    @KafkaListener(topics = Topics.ORDER_EVENTS)
    @Transactional
    public void onMessage(String message) {
        EventEnvelope envelope = codec.readEnvelope(message);
        if (!idempotency.firstTime(envelope.eventId(), CONSUMER)) {
            return;
        }
        switch (envelope.eventType()) {
            case EventTypes.ORDER_CREATED -> inventory.reserve(codec.payload(envelope, Events.OrderCreated.class));
            case EventTypes.ORDER_CANCELLED ->
                    inventory.release(codec.payload(envelope, Events.OrderCancelled.class).orderId());
            default -> { /* OrderConfirmed: nothing to do */ }
        }
    }
}
