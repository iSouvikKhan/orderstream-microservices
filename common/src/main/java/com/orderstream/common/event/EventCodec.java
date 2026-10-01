package com.orderstream.common.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/** JSON (de)serialisation of envelopes and payloads. */
@Component
public class EventCodec {

    private final ObjectMapper objectMapper;

    public EventCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise " + value.getClass().getSimpleName(), e);
        }
    }

    public EventEnvelope readEnvelope(String json) {
        try {
            return objectMapper.readValue(json, EventEnvelope.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Malformed event envelope", e);
        }
    }

    public <T> T payload(EventEnvelope envelope, Class<T> type) {
        return objectMapper.convertValue(envelope.payload(), type);
    }

    public ObjectMapper mapper() {
        return objectMapper;
    }
}
