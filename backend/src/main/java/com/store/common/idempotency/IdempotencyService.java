package com.store.common.idempotency;

import com.fasterxml.jackson.databind.JsonNode;
import com.store.common.error.ApiException;
import com.store.common.util.Hashing;
import com.store.common.util.Json;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generic Idempotency-Key handling for money-moving admin calls (refunds, cancellations).
 * Checkout uses orders.idempotency_key directly.
 *
 * The key row is inserted in the same transaction as the work, so a crash leaves no half state:
 * a replay with the same key and body returns the stored response; a different body is rejected.
 */
@Service
public class IdempotencyService {

    private record KeyRow(String requestHash, String status, Integer responseStatus, String responseBody) {}

    private final JdbcClient jdbc;
    private final Json json;

    public IdempotencyService(JdbcClient jdbc, Json json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public static void requireKey(String key) {
        if (key == null || key.isBlank() || key.length() > 100) {
            throw ApiException.badRequest("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required (max 100 chars)");
        }
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public <T> JsonNode execute(String scope, Long userId, String key, Object request, Supplier<T> work) {
        requireKey(key);
        String hash = Hashing.sha256Hex(json.write(request));
        Optional<KeyRow> existing = find(scope, userId, key);
        if (existing.isPresent()) {
            return replay(existing.get(), hash);
        }
        int inserted = jdbc.sql("""
                INSERT INTO idempotency_keys (scope, user_id, idem_key, request_hash, expires_at)
                VALUES (:scope, :uid, :key, :hash, now() + interval '7 days')
                ON CONFLICT DO NOTHING
                """)
                .param("scope", scope).param("uid", userId).param("key", key).param("hash", hash)
                .update();
        if (inserted == 0) {
            return replay(find(scope, userId, key).orElseThrow(), hash);
        }
        T result = work.get();
        JsonNode body = json.tree(result);
        jdbc.sql("""
                UPDATE idempotency_keys SET status = 'COMPLETED', response_status = 200, response_body = CAST(:body AS jsonb)
                WHERE scope = :scope AND COALESCE(user_id, 0) = COALESCE(CAST(:uid AS bigint), 0) AND idem_key = :key
                """)
                .param("body", json.write(body)).param("scope", scope).param("uid", userId).param("key", key)
                .update();
        return body;
    }

    private Optional<KeyRow> find(String scope, Long userId, String key) {
        return jdbc.sql("""
                SELECT request_hash, status, response_status, response_body::text AS response_body
                FROM idempotency_keys
                WHERE scope = :scope AND COALESCE(user_id, 0) = COALESCE(CAST(:uid AS bigint), 0) AND idem_key = :key
                """)
                .param("scope", scope).param("uid", userId).param("key", key)
                .query(KeyRow.class).optional();
    }

    private JsonNode replay(KeyRow row, String hash) {
        if (!row.requestHash().equals(hash)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "IDEMPOTENCY_KEY_REUSED",
                    "This Idempotency-Key was already used with a different request");
        }
        if (!"COMPLETED".equals(row.status()) || row.responseBody() == null) {
            throw ApiException.conflict("REQUEST_IN_PROGRESS", "The same request is still being processed");
        }
        return json.read(row.responseBody());
    }
}
