package com.store.common.outbox;

import com.fasterxml.jackson.databind.JsonNode;

/** One handler per event type. Handlers must be safe to run more than once (at-least-once delivery). */
public interface OutboxHandler {

    String eventType();

    void handle(long outboxEventId, JsonNode payload) throws Exception;

    @FunctionalInterface
    interface Body {
        void handle(long outboxEventId, JsonNode payload) throws Exception;
    }

    static OutboxHandler of(String eventType, Body body) {
        return new OutboxHandler() {
            @Override
            public String eventType() {
                return eventType;
            }

            @Override
            public void handle(long outboxEventId, JsonNode payload) throws Exception {
                body.handle(outboxEventId, payload);
            }
        };
    }
}
