package com.orderstream.common.outbox;

import com.orderstream.common.event.EventCodec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Writes events to the outbox. Must be called inside the business transaction. */
@Service
public class OutboxService {

    private final OutboxEventRepository repository;
    private final EventCodec codec;

    public OutboxService(OutboxEventRepository repository, EventCodec codec) {
        this.repository = repository;
        this.codec = codec;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent enqueue(String aggregateType, String aggregateId, String topic, String eventType, Object payload) {
        return repository.save(new OutboxEvent(aggregateType, aggregateId, topic, eventType, codec.write(payload)));
    }
}
