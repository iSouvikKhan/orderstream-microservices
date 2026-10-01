package com.orderstream.common.event;

/** Kafka topic names. Each service publishes to exactly one topic. */
public final class Topics {
    public static final String ORDER_EVENTS = "order.events";
    public static final String INVENTORY_EVENTS = "inventory.events";
    public static final String PAYMENT_EVENTS = "payment.events";

    private Topics() {
    }
}
