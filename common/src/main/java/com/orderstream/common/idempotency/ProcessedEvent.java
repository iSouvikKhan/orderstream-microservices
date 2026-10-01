package com.orderstream.common.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Records that a consumer has handled an event, so a re-delivery is ignored. */
@Entity
@Table(name = "processed_events")
@IdClass(ProcessedEvent.Key.class)
public class ProcessedEvent {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Id
    @Column(name = "consumer")
    private String consumer;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedEvent() {
    }

    public ProcessedEvent(UUID eventId, String consumer) {
        this.eventId = eventId;
        this.consumer = consumer;
        this.processedAt = Instant.now();
    }

    public UUID getEventId() { return eventId; }
    public String getConsumer() { return consumer; }
    public Instant getProcessedAt() { return processedAt; }

    public static class Key implements Serializable {
        private UUID eventId;
        private String consumer;

        public Key() {
        }

        public Key(UUID eventId, String consumer) {
            this.eventId = eventId;
            this.consumer = consumer;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key key)) return false;
            return Objects.equals(eventId, key.eventId) && Objects.equals(consumer, key.consumer);
        }

        @Override
        public int hashCode() {
            return Objects.hash(eventId, consumer);
        }
    }
}
