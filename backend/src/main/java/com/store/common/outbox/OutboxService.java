package com.store.common.outbox;

import com.store.common.util.Json;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Outbox pattern: side effects (emails, invoices, gateway/courier retries) are written as rows in the
 * same transaction as the business change, then handled by the worker. Nothing is lost on a crash.
 */
@Service
public class OutboxService {

    private final JdbcClient jdbc;
    private final Json json;

    public OutboxService(JdbcClient jdbc, Json json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public long enqueue(String aggregateType, Object aggregateId, String eventType, Object payload) {
        return jdbc.sql("""
                INSERT INTO outbox_events (aggregate_type, aggregate_id, event_type, payload)
                VALUES (:atype, :aid, :etype, CAST(:payload AS jsonb))
                RETURNING id
                """)
                .param("atype", aggregateType)
                .param("aid", String.valueOf(aggregateId))
                .param("etype", eventType)
                .param("payload", json.write(payload))
                .query(Long.class)
                .single();
    }
}
