package com.orderstream.payment.service;

import com.orderstream.common.event.EventTypes;
import com.orderstream.common.event.Events;
import com.orderstream.common.event.Topics;
import com.orderstream.common.outbox.OutboxService;
import com.orderstream.payment.domain.Payment;
import com.orderstream.payment.domain.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final String AGGREGATE = "Order";

    private final PaymentRepository payments;
    private final PaymentProvider provider;
    private final OutboxService outbox;

    public PaymentService(PaymentRepository payments, PaymentProvider provider, OutboxService outbox) {
        this.payments = payments;
        this.provider = provider;
        this.outbox = outbox;
    }

    @Transactional
    public void charge(Events.InventoryReserved reserved) {
        // Business-level idempotency on top of event-id dedup: one payment per order, ever.
        if (payments.findByOrderId(reserved.orderId()).isPresent()) {
            log.info("Order {} already charged, skipping", reserved.orderId());
            return;
        }
        String key = reserved.orderId().toString();
        Optional<String> declined = provider.charge(reserved.customerId(), reserved.totalAmount());
        if (declined.isPresent()) {
            payments.save(Payment.failed(reserved.orderId(), reserved.customerId(), reserved.totalAmount(), declined.get()));
            outbox.enqueue(AGGREGATE, key, Topics.PAYMENT_EVENTS, EventTypes.PAYMENT_FAILED,
                    new Events.PaymentFailed(reserved.orderId(), declined.get()));
            log.info("Payment for order {} declined: {}", reserved.orderId(), declined.get());
            return;
        }
        Payment payment = payments.save(Payment.completed(reserved.orderId(), reserved.customerId(), reserved.totalAmount()));
        outbox.enqueue(AGGREGATE, key, Topics.PAYMENT_EVENTS, EventTypes.PAYMENT_COMPLETED,
                new Events.PaymentCompleted(reserved.orderId(), payment.getId(), payment.getAmount()));
        log.info("Payment {} completed for order {}", payment.getId(), reserved.orderId());
    }
}
