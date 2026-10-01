package com.orderstream.payment.messaging;

import com.orderstream.common.event.EventCodec;
import com.orderstream.common.event.EventEnvelope;
import com.orderstream.common.event.EventTypes;
import com.orderstream.common.event.Events;
import com.orderstream.common.event.Topics;
import com.orderstream.common.idempotency.IdempotencyGuard;
import com.orderstream.payment.service.PaymentService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class InventoryEventListener {

    private static final String CONSUMER = "payment-service";

    private final EventCodec codec;
    private final IdempotencyGuard idempotency;
    private final PaymentService payments;

    public InventoryEventListener(EventCodec codec, IdempotencyGuard idempotency, PaymentService payments) {
        this.codec = codec;
        this.idempotency = idempotency;
        this.payments = payments;
    }

    @KafkaListener(topics = Topics.INVENTORY_EVENTS)
    @Transactional
    public void onMessage(String message) {
        EventEnvelope envelope = codec.readEnvelope(message);
        if (!EventTypes.INVENTORY_RESERVED.equals(envelope.eventType())) {
            return; // rejections are handled by the order service
        }
        if (idempotency.firstTime(envelope.eventId(), CONSUMER)) {
            payments.charge(codec.payload(envelope, Events.InventoryReserved.class));
        }
    }
}
