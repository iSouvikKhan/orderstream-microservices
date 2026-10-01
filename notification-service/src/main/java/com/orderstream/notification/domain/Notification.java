package com.orderstream.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Column(nullable = false)
    private String channel;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Notification() {
    }

    public Notification(UUID orderId, String customerId, String channel, String subject, String body) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.customerId = customerId;
        this.channel = channel;
        this.subject = subject;
        this.body = body;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public String getCustomerId() { return customerId; }
    public String getChannel() { return channel; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public Instant getCreatedAt() { return createdAt; }
}
