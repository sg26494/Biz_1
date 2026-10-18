package com.store.jobs;

import com.store.common.outbox.OutboxHandler;
import com.store.common.util.Json;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Picks pending outbox rows every few seconds. Each row is first leased (next_attempt_at pushed out) so a
 * crash mid-handling just means a retry later. Failures back off exponentially; after 10 attempts the row
 * is marked FAILED and shows up for admins.
 */
@Component
@ConditionalOnProperty(prefix = "app.jobs", name = "enabled", havingValue = "true")
public class OutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(OutboxWorker.class);
    private static final int BATCH = 20;
    private static final int MAX_ATTEMPTS = 10;

    private record Claimed(long id, String eventType, String payload, int attempts) {}

    private final JdbcClient jdbc;
    private final Json json;
    private final Map<String, OutboxHandler> handlers;

    public OutboxWorker(JdbcClient jdbc, Json json, List<OutboxHandler> handlers) {
        this.jdbc = jdbc;
        this.json = json;
        this.handlers = handlers.stream().collect(Collectors.toMap(OutboxHandler::eventType, Function.identity()));
    }

    @Scheduled(fixedDelayString = "PT3S")
    @SchedulerLock(name = "outbox-worker", lockAtMostFor = "PT2M")
    public void run() {
        List<Claimed> batch = jdbc.sql("""
                UPDATE outbox_events SET next_attempt_at = now() + interval '5 minutes'
                WHERE id IN (
                    SELECT id FROM outbox_events
                    WHERE status = 'PENDING' AND next_attempt_at <= now()
                    ORDER BY id LIMIT :n FOR UPDATE SKIP LOCKED)
                RETURNING id, event_type, payload::text AS payload, attempts
                """).param("n", BATCH).query(Claimed.class).list();
        for (Claimed c : batch) {
            handle(c);
        }
    }

    private void handle(Claimed c) {
        OutboxHandler h = handlers.get(c.eventType());
        try {
            if (h == null) {
                log.warn("No handler for outbox event type {}; marking done", c.eventType());
            } else {
                h.handle(c.id(), json.read(c.payload()));
            }
            jdbc.sql("UPDATE outbox_events SET status = 'DONE', processed_at = now(), attempts = attempts + 1 WHERE id = :id")
                    .param("id", c.id()).update();
        } catch (Exception e) {
            int attempts = c.attempts() + 1;
            boolean dead = attempts >= MAX_ATTEMPTS;
            long backoffSeconds = Math.min(Duration.ofHours(1).toSeconds(), (long) Math.pow(2, attempts) * 10);
            log.warn("Outbox {} ({}) attempt {} failed: {}", c.id(), c.eventType(), attempts, e.getMessage());
            jdbc.sql("""
                    UPDATE outbox_events
                    SET attempts = :a, last_error = :err, status = :st,
                        next_attempt_at = now() + make_interval(secs => :backoff)
                    WHERE id = :id
                    """)
                    .param("a", attempts).param("err", String.valueOf(e.getMessage()))
                    .param("st", dead ? "FAILED" : "PENDING").param("backoff", (double) backoffSeconds)
                    .param("id", c.id()).update();
        }
    }
}
