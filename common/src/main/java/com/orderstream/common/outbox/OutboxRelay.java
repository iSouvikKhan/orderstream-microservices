package com.orderstream.common.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.orderstream.common.event.EventCodec;
import com.orderstream.common.event.EventEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Polls the outbox and publishes pending rows to Kafka, keyed by aggregate id so that all events
 * of one order land on the same partition (preserving per-order ordering).
 * <p>
 * Delivery is at-least-once: if the process dies after the send but before the commit, the row
 * is published again with the same event id, and idempotent consumers drop the duplicate.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final EventCodec codec;
    private final int batchSize;

    public OutboxRelay(OutboxEventRepository repository,
                       KafkaTemplate<String, String> kafkaTemplate,
                       EventCodec codec,
                       @Value("${orderstream.outbox.batch-size:100}") int batchSize) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.codec = codec;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${orderstream.outbox.poll-interval-ms:500}")
    @Transactional
    public void relay() {
        List<OutboxEvent> batch = repository.lockUnpublishedBatch(batchSize);
        for (OutboxEvent event : batch) {
            try {
                EventEnvelope envelope = new EventEnvelope(
                        event.getId(),
                        event.getEventType(),
                        event.getAggregateId(),
                        event.getCreatedAt(),
                        codec.mapper().readTree(event.getPayload()));
                kafkaTemplate.send(event.getTopic(), event.getAggregateId(), codec.write(envelope))
                        .get(10, TimeUnit.SECONDS);
                event.markPublished();
                log.debug("Relayed {} {} to {}", event.getEventType(), event.getId(), event.getTopic());
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Corrupt outbox payload " + event.getId(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return; // rows not yet marked stay pending
            } catch (Exception e) {
                // Stop the batch so ordering is preserved; remaining rows are retried next poll.
                log.warn("Kafka publish failed for outbox event {}: {}", event.getId(), e.getMessage());
                return;
            }
        }
    }
}
