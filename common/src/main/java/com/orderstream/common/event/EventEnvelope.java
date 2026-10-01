package com.orderstream.common.event;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/**
 * Wire format of every message on Kafka. {@code eventId} is assigned once, when the event is
 * written to the outbox, so a re-delivered message always carries the same id. Consumers use it
 * for de-duplication.
 */
public record EventEnvelope(
        UUID eventId,
        String eventType,
        String aggregateId,
        Instant occurredAt,
        JsonNode payload) {
}
