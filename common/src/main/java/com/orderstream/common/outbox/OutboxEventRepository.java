package com.orderstream.common.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Locks a batch of unpublished rows. SKIP LOCKED lets several replicas relay in parallel
     * without publishing the same row twice at the same time.
     */
    @Query(value = """
            SELECT * FROM outbox_events
            WHERE published_at IS NULL
            ORDER BY created_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> lockUnpublishedBatch(@Param("batchSize") int batchSize);

    List<OutboxEvent> findByAggregateIdOrderByCreatedAt(String aggregateId);

    long countByAggregateIdAndEventType(String aggregateId, String eventType);
}
