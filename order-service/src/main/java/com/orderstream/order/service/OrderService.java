package com.orderstream.order.service;

import com.orderstream.common.event.EventTypes;
import com.orderstream.common.event.Events;
import com.orderstream.common.event.Topics;
import com.orderstream.common.outbox.OutboxService;
import com.orderstream.order.api.OrderDtos.CreateOrderRequest;
import com.orderstream.order.domain.Order;
import com.orderstream.order.domain.OrderItem;
import com.orderstream.order.domain.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Order side of the saga. Every state change and the event announcing it are written in one
 * local transaction (order row + outbox row), so the two can never diverge.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final String AGGREGATE = "Order";

    private final OrderRepository orders;
    private final OutboxService outbox;

    public OrderService(OrderRepository orders, OutboxService outbox) {
        this.orders = orders;
        this.outbox = outbox;
    }

    @Transactional
    public Order placeOrder(String customerId, CreateOrderRequest request) {
        List<OrderItem> items = request.items().stream()
                .map(i -> new OrderItem(i.productId(), i.quantity(), i.unitPrice()))
                .toList();
        Order order = orders.save(Order.place(customerId, items));

        List<Events.OrderLine> lines = order.getItems().stream()
                .map(i -> new Events.OrderLine(i.getProductId(), i.getQuantity(), i.getUnitPrice()))
                .toList();
        outbox.enqueue(AGGREGATE, order.getId().toString(), Topics.ORDER_EVENTS, EventTypes.ORDER_CREATED,
                new Events.OrderCreated(order.getId(), customerId, lines, order.getTotalAmount()));
        log.info("Order {} placed by {} for {}", order.getId(), customerId, order.getTotalAmount());
        return order;
    }

    @Transactional(readOnly = true)
    public Order get(UUID id) {
        return orders.findWithItemsById(id).orElseThrow(() -> new OrderNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<Order> forCustomer(String customerId) {
        return orders.findByCustomerIdOrderByCreatedAtDesc(customerId);
    }

    // ---- saga reactions (called inside the consumer's transaction) ----

    @Transactional
    public void onInventoryReserved(UUID orderId) {
        Order order = get(orderId);
        if (order.markInventoryReserved()) {
            log.info("Order {} inventory reserved, awaiting payment", orderId);
        }
    }

    @Transactional
    public void onInventoryRejected(UUID orderId, String reason) {
        cancel(orderId, "Inventory rejected: " + reason);
    }

    @Transactional
    public void onPaymentCompleted(UUID orderId) {
        Order order = get(orderId);
        if (order.confirm()) {
            outbox.enqueue(AGGREGATE, orderId.toString(), Topics.ORDER_EVENTS, EventTypes.ORDER_CONFIRMED,
                    new Events.OrderConfirmed(orderId, order.getCustomerId(), order.getTotalAmount()));
            log.info("Order {} confirmed", orderId);
        }
    }

    @Transactional
    public void onPaymentFailed(UUID orderId, String reason) {
        // OrderCancelled is also the compensation trigger: inventory releases its reservation.
        cancel(orderId, "Payment failed: " + reason);
    }

    private void cancel(UUID orderId, String reason) {
        Order order = get(orderId);
        if (order.cancel(reason)) {
            outbox.enqueue(AGGREGATE, orderId.toString(), Topics.ORDER_EVENTS, EventTypes.ORDER_CANCELLED,
                    new Events.OrderCancelled(orderId, order.getCustomerId(), reason));
            log.info("Order {} cancelled: {}", orderId, reason);
        }
    }
}
