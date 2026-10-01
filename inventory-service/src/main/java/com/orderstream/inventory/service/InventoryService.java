package com.orderstream.inventory.service;

import com.orderstream.common.event.EventTypes;
import com.orderstream.common.event.Events;
import com.orderstream.common.event.Topics;
import com.orderstream.common.outbox.OutboxService;
import com.orderstream.inventory.domain.Product;
import com.orderstream.inventory.domain.ProductRepository;
import com.orderstream.inventory.domain.Reservation;
import com.orderstream.inventory.domain.ReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);
    private static final String AGGREGATE = "Order";

    private final ProductRepository products;
    private final ReservationRepository reservations;
    private final OutboxService outbox;

    public InventoryService(ProductRepository products, ReservationRepository reservations, OutboxService outbox) {
        this.products = products;
        this.reservations = reservations;
        this.outbox = outbox;
    }

    /** All-or-nothing reservation of every line of the order. */
    @Transactional
    public void reserve(Events.OrderCreated order) {
        if (reservations.existsByOrderId(order.orderId())) {
            log.info("Order {} already has reservations, skipping", order.orderId());
            return;
        }
        Map<String, Integer> wanted = order.items().stream()
                .collect(Collectors.toMap(Events.OrderLine::productId, Events.OrderLine::quantity, Integer::sum));
        Map<String, Product> locked = products.lockAllBySku(wanted.keySet()).stream()
                .collect(Collectors.toMap(Product::getSku, Function.identity()));

        String problem = wanted.entrySet().stream()
                .map(e -> {
                    Product p = locked.get(e.getKey());
                    if (p == null) return "unknown product " + e.getKey();
                    if (!p.canReserve(e.getValue())) return "insufficient stock for " + e.getKey();
                    return null;
                })
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);

        String key = order.orderId().toString();
        if (problem != null) {
            outbox.enqueue(AGGREGATE, key, Topics.INVENTORY_EVENTS, EventTypes.INVENTORY_REJECTED,
                    new Events.InventoryRejected(order.orderId(), problem));
            log.info("Order {} rejected: {}", order.orderId(), problem);
            return;
        }

        wanted.forEach((sku, qty) -> {
            locked.get(sku).reserve(qty);
            reservations.save(new Reservation(order.orderId(), sku, qty));
        });
        outbox.enqueue(AGGREGATE, key, Topics.INVENTORY_EVENTS, EventTypes.INVENTORY_RESERVED,
                new Events.InventoryReserved(order.orderId(), order.customerId(), order.totalAmount()));
        log.info("Order {} reserved {}", order.orderId(), wanted);
    }

    /** Compensating action: give back stock held for a cancelled order. Safe to call repeatedly. */
    @Transactional
    public void release(UUID orderId) {
        var held = reservations.findByOrderId(orderId).stream()
                .filter(r -> r.getStatus() == Reservation.Status.RESERVED)
                .toList();
        if (held.isEmpty()) {
            return;
        }
        Map<String, Product> locked = products.lockAllBySku(held.stream().map(Reservation::getSku).toList())
                .stream().collect(Collectors.toMap(Product::getSku, Function.identity()));
        held.forEach(r -> {
            locked.get(r.getSku()).release(r.getQuantity());
            r.release();
        });
        log.info("Released {} reservation(s) for cancelled order {}", held.size(), orderId);
    }
}
