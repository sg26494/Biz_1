package com.store.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.store.common.util.Hashing;
import com.store.common.util.Json;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Stores every Razorpay webhook in webhook_events (unique on event id, so duplicates are dropped),
 * then processes it in its own transaction. A processing failure is recorded and retried by a job;
 * the HTTP response is still 200 so Razorpay does not hammer us.
 */
@Service
public class RazorpayWebhookService {

    private static final Logger log = LoggerFactory.getLogger(RazorpayWebhookService.class);
    public static final String SOURCE = "RAZORPAY";
    private static final int MAX_ATTEMPTS = 8;

    private record EventRow(long id, String eventType, String payload, int attempts) {}

    private final JdbcClient jdbc;
    private final Json json;
    private final PaymentService payments;
    private final RefundService refunds;
    private final TransactionTemplate tx;

    public RazorpayWebhookService(JdbcClient jdbc, Json json, PaymentService payments, RefundService refunds,
                                  PlatformTransactionManager txManager) {
        this.jdbc = jdbc;
        this.json = json;
        this.payments = payments;
        this.refunds = refunds;
        this.tx = new TransactionTemplate(txManager);
    }

    /** Returns the stored row id, or empty if this event id was already received. */
    public Optional<Long> store(byte[] rawBody, String eventIdHeader) {
        String body = new String(rawBody, StandardCharsets.UTF_8);
        JsonNode n = json.read(body);
        String eventType = n.path("event").asText("unknown");
        String eventId = eventIdHeader != null && !eventIdHeader.isBlank() ? eventIdHeader : "sha256:" + Hashing.sha256Hex(body);
        return jdbc.sql("""
                INSERT INTO webhook_events (source, event_id, event_type, payload)
                VALUES (:s, :id, :t, CAST(:p AS jsonb))
                ON CONFLICT (source, event_id) DO NOTHING
                RETURNING id
                """).param("s", SOURCE).param("id", eventId).param("t", eventType).param("p", body)
                .query(Long.class).optional();
    }

    /** Processes one stored event. Never throws. */
    public void process(long webhookEventId) {
        EventRow row = jdbc.sql("""
                SELECT id, event_type, payload::text AS payload, attempts FROM webhook_events WHERE id = :id
                """).param("id", webhookEventId).query(EventRow.class).single();
        try {
            tx.executeWithoutResult(status -> {
                // Lock the event row so the retry job and the live request never process it at the same time.
                String current = jdbc.sql("SELECT status FROM webhook_events WHERE id = :id FOR UPDATE")
                        .param("id", row.id()).query(String.class).single();
                if ("PROCESSED".equals(current) || "IGNORED".equals(current)) {
                    return;
                }
                boolean handled = dispatch(row.eventType(), json.read(row.payload()));
                jdbc.sql("""
                        UPDATE webhook_events SET status = :s, processed_at = now(), attempts = attempts + 1, error = NULL
                        WHERE id = :id
                        """).param("s", handled ? "PROCESSED" : "IGNORED").param("id", row.id()).update();
            });
        } catch (Exception e) {
            log.error("Webhook {} ({}) failed: {}", row.id(), row.eventType(), e.getMessage(), e);
            jdbc.sql("""
                    UPDATE webhook_events SET status = 'FAILED', attempts = attempts + 1, error = :err WHERE id = :id
                    """).param("err", String.valueOf(e.getMessage())).param("id", row.id()).update();
        }
    }

    /** Retry job: failed or never-processed events (e.g. crash after store). */
    public int retryPending() {
        List<Long> ids = jdbc.sql("""
                SELECT id FROM webhook_events
                WHERE source = :s AND status IN ('RECEIVED', 'FAILED') AND attempts < :max
                  AND received_at < now() - interval '30 seconds'
                ORDER BY id LIMIT 50
                """).param("s", SOURCE).param("max", MAX_ATTEMPTS).query(Long.class).list();
        ids.forEach(this::process);
        return ids.size();
    }

    private boolean dispatch(String event, JsonNode n) {
        JsonNode payment = n.path("payload").path("payment").path("entity");
        JsonNode refund = n.path("payload").path("refund").path("entity");
        switch (event) {
            case "payment.captured", "order.paid" -> {
                if (payment.isMissingNode()) {
                    return false;
                }
                payments.onCaptured(payment.path("order_id").asText(), payment.path("id").asText(),
                        payment.path("amount").asLong(), payment.path("method").asText(null));
                return true;
            }
            case "payment.failed" -> {
                payments.onFailed(payment.path("order_id").asText(), payment.path("id").asText(),
                        payment.path("error_code").asText(null), payment.path("error_description").asText(null));
                return true;
            }
            case "refund.processed" -> {
                refunds.onGatewayProcessed(refund.path("id").asText(null), refund.path("receipt").asText(null));
                return true;
            }
            case "refund.failed" -> {
                refunds.onGatewayFailed(refund.path("id").asText(null), refund.path("receipt").asText(null),
                        "Gateway reported refund failure");
                return true;
            }
            default -> {
                log.info("Ignoring Razorpay event {}", event);
                return false;
            }
        }
    }
}
