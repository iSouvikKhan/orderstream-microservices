package com.orderstream.order.domain;

public enum OrderStatus {
    /** Created, waiting for inventory. */
    PENDING,
    /** Stock reserved, waiting for payment. */
    INVENTORY_RESERVED,
    /** Paid. Terminal. */
    CONFIRMED,
    /** Rejected by inventory or payment. Terminal. */
    CANCELLED;

    public boolean isTerminal() {
        return this == CONFIRMED || this == CANCELLED;
    }
}
