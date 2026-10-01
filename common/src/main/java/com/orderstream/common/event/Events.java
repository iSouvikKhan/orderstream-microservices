package com.orderstream.common.event;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Payload contracts shared by all services. */
public final class Events {

    private Events() {
    }

    public record OrderLine(String productId, int quantity, BigDecimal unitPrice) {
    }

    public record OrderCreated(UUID orderId, String customerId, List<OrderLine> items, BigDecimal totalAmount) {
    }

    public record OrderConfirmed(UUID orderId, String customerId, BigDecimal totalAmount) {
    }

    public record OrderCancelled(UUID orderId, String customerId, String reason) {
    }

    public record InventoryReserved(UUID orderId, String customerId, BigDecimal totalAmount) {
    }

    public record InventoryRejected(UUID orderId, String reason) {
    }

    public record PaymentCompleted(UUID orderId, UUID paymentId, BigDecimal amount) {
    }

    public record PaymentFailed(UUID orderId, String reason) {
    }
}
