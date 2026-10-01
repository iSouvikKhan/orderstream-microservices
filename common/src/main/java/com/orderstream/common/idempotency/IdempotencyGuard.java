package com.orderstream.common.idempotency;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Idempotent-consumer helper. Call {@link #firstTime} at the start of a transactional handler:
 * the processed-event row commits atomically with the handler's side effects. If two deliveries
 * race, the primary key rejects the second insert, its transaction rolls back, and the retry
 * sees the event as already processed.
 */
@Component
public class IdempotencyGuard {

    private final ProcessedEventRepository repository;

    public IdempotencyGuard(ProcessedEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean firstTime(UUID eventId, String consumer) {
        ProcessedEvent.Key key = new ProcessedEvent.Key(eventId, consumer);
        if (repository.existsById(key)) {
            return false;
        }
        repository.saveAndFlush(new ProcessedEvent(eventId, consumer));
        return true;
    }
}
