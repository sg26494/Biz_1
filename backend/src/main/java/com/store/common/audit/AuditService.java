package com.store.common.audit;

import com.store.common.util.Json;
import com.store.common.web.RequestContextFilter;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Every admin write goes through here: who, what, before/after, why. Runs inside the caller's transaction. */
@Service
public class AuditService {

    private final JdbcClient jdbc;
    private final Json json;

    public AuditService(JdbcClient jdbc, Json json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Long actorUserId, String action, String entityType, Object entityId,
                       Object before, Object after, String reason) {
        jdbc.sql("""
                INSERT INTO audit_logs (actor_user_id, action, entity_type, entity_id, before_state, after_state,
                                        reason, ip, request_id)
                VALUES (:actor, :action, :type, :id, CAST(:before AS jsonb), CAST(:after AS jsonb), :reason, :ip, :rid)
                """)
                .param("actor", actorUserId)
                .param("action", action)
                .param("type", entityType)
                .param("id", entityId == null ? null : String.valueOf(entityId))
                .param("before", json.write(before))
                .param("after", json.write(after))
                .param("reason", reason)
                .param("ip", RequestContextFilter.currentIp())
                .param("rid", RequestContextFilter.currentRequestId())
                .update();
    }

    public record AuditRow(long id, Long actorUserId, String actorEmail, String action, String entityType,
                           String entityId, String beforeState, String afterState, String reason, String ip,
                           OffsetDateTime createdAt) {}

    @Transactional(readOnly = true)
    public List<AuditRow> search(String entityType, String entityId, Long actorUserId, int page, int size) {
        return jdbc.sql("""
                SELECT a.id, a.actor_user_id, u.email AS actor_email, a.action, a.entity_type, a.entity_id,
                       a.before_state::text AS before_state, a.after_state::text AS after_state, a.reason, a.ip,
                       a.created_at
                FROM audit_logs a LEFT JOIN users u ON u.id = a.actor_user_id
                WHERE (CAST(:type AS varchar) IS NULL OR a.entity_type = :type)
                  AND (CAST(:eid AS varchar) IS NULL OR a.entity_id = :eid)
                  AND (CAST(:actor AS bigint) IS NULL OR a.actor_user_id = :actor)
                ORDER BY a.created_at DESC
                LIMIT :limit OFFSET :offset
                """)
                .param("type", entityType)
                .param("eid", entityId)
                .param("actor", actorUserId)
                .param("limit", size)
                .param("offset", page * size)
                .query(AuditRow.class)
                .list();
    }
}
