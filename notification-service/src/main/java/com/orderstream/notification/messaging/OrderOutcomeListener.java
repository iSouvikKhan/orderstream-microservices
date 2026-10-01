package com.orderstream.notification.messaging;

import com.orderstream.common.event.EventCodec;
import com.orderstream.common.event.EventEnvelope;
import com.orderstream.common.event.EventTypes;
import com.orderstream.common.event.Events;
import com.orderstream.common.event.Topics;
import com.orderstream.common.idempotency.IdempotencyGuard;
import com.orderstream.notification.domain.Notification;
import com.orderstream.notification.domain.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns final order outcomes into customer notifications. Delivery is simulated with a log line;
 * swap in an email/SMS client here. Idempotency guarantees a customer is never notified twice.
 */
@Component
public class OrderOutcomeListener {

    private static final Logger log = LoggerFactory.getLogger(OrderOutcomeListener.class);
    private static final String CONSUMER = "notification-service";

    private final EventCodec codec;
    private final IdempotencyGuard idempotency;
    private final NotificationRepository notifications;

    public OrderOutcomeListener(EventCodec codec, IdempotencyGuard idempotency, NotificationRepository notifications) {
        this.codec = codec;
        this.idempotency = idempotency;
        this.notifications = notifications;
    }

    @KafkaListener(topics = Topics.ORDER_EVENTS)
    @Transactional
    public void onMessage(String message) {
        EventEnvelope envelope = codec.readEnvelope(message);
        Notification notification = switch (envelope.eventType()) {
            case EventTypes.ORDER_CONFIRMED -> {
                var e = codec.payload(envelope, Events.OrderConfirmed.class);
                yield new Notification(e.orderId(), e.customerId(), "EMAIL",
                        "Your order is confirmed",
                        "Thanks! Order " + e.orderId() + " for " + e.totalAmount() + " has been paid and confirmed.");
            }
            case EventTypes.ORDER_CANCELLED -> {
                var e = codec.payload(envelope, Events.OrderCancelled.class);
                yield new Notification(e.orderId(), e.customerId(), "EMAIL",
                        "Your order was cancelled",
                        "Order " + e.orderId() + " could not be completed: " + e.reason());
            }
            default -> null;
        };
        if (notification == null || !idempotency.firstTime(envelope.eventId(), CONSUMER)) {
            return;
        }
        notifications.save(notification);
        log.info("[{}] to {}: {}", notification.getChannel(), notification.getCustomerId(), notification.getSubject());
    }
}
