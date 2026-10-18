package com.store.admin.service;

import com.store.auth.service.RefreshTokenService;
import com.store.common.audit.AuditService;
import com.store.common.error.ApiException;
import com.store.common.web.PageResponse;
import com.store.user.domain.Role;
import com.store.user.domain.User;
import com.store.user.domain.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserService {

    public record CustomerRow(long id, String name, String email, String phone, String role, String status,
                              Instant createdAt, Instant lastLoginAt) {
        static CustomerRow of(User u) {
            return new CustomerRow(u.getId(), u.getName(), u.getEmail(), u.getPhone(), u.getRole().name(),
                    u.getStatus().name(), u.getCreatedAt(), u.getLastLoginAt());
        }
    }

    public record CustomerStats(long orders, long paidOrders, long totalSpentPaise) {}

    public record CustomerDetail(CustomerRow customer, CustomerStats stats) {}

    private final UserRepository users;
    private final RefreshTokenService refreshTokens;
    private final AuditService audit;
    private final JdbcClient jdbc;

    public AdminUserService(UserRepository users, RefreshTokenService refreshTokens, AuditService audit,
                            JdbcClient jdbc) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerRow> customers(String q, int page, int size) {
        return PageResponse.from(users.search(q == null ? "" : q.trim(), PageRequest.of(page, size)), CustomerRow::of);
    }

    @Transactional(readOnly = true)
    public CustomerDetail customer(long id) {
        User u = users.findById(id).orElseThrow(() -> ApiException.notFound("Customer"));
        CustomerStats stats = jdbc.sql("""
                SELECT COUNT(*) AS orders,
                       COUNT(*) FILTER (WHERE paid_at IS NOT NULL) AS paid_orders,
                       COALESCE(SUM(total_paise) FILTER (WHERE paid_at IS NOT NULL AND status <> 'CANCELLED'), 0)
                         AS total_spent_paise
                FROM orders WHERE user_id = :u
                """).param("u", id).query(CustomerStats.class).single();
        return new CustomerDetail(CustomerRow.of(u), stats);
    }

    @Transactional
    public CustomerRow setBlocked(long id, boolean blocked, String reason, long actor) {
        User u = users.findById(id).orElseThrow(() -> ApiException.notFound("Customer"));
        if (u.getId() == actor) {
            throw ApiException.badRequest("SELF_BLOCK", "You cannot block yourself");
        }
        if (u.getRole() == Role.ADMIN && blocked) {
            throw ApiException.badRequest("ADMIN_BLOCK", "Remove admin access before blocking this account");
        }
        User.Status before = u.getStatus();
        u.setStatus(blocked ? User.Status.BLOCKED : User.Status.ACTIVE);
        if (blocked) {
            refreshTokens.revokeAll(id);
        }
        audit.record(actor, blocked ? "USER_BLOCKED" : "USER_UNBLOCKED", "user", id, Map.of("status", before),
                Map.of("status", u.getStatus()), reason);
        return CustomerRow.of(u);
    }

    @Transactional(readOnly = true)
    public List<CustomerRow> admins() {
        return users.findByRole(Role.ADMIN).stream().map(CustomerRow::of).toList();
    }

    /** Add by email. If the person has no account yet, one is created; they log in with Google or email OTP. */
    @Transactional
    public CustomerRow addAdmin(String rawEmail, long actor) {
        String email = rawEmail.trim().toLowerCase();
        User u = users.findByEmailIgnoreCase(email).orElseGet(() -> {
            User n = new User();
            n.setEmail(email);
            n.setName(email.substring(0, email.indexOf('@')));
            return n;
        });
        if (u.getRole() == Role.ADMIN && u.getId() != null) {
            return CustomerRow.of(u);
        }
        if (u.isBlocked()) {
            throw ApiException.badRequest("USER_BLOCKED", "Unblock this account first");
        }
        u.setRole(Role.ADMIN);
        users.saveAndFlush(u);
        refreshTokens.revokeAll(u.getId()); // pick up the new role on next login
        audit.record(actor, "ADMIN_ADDED", "user", u.getId(), Map.of("role", "USER"), Map.of("role", "ADMIN"), null);
        return CustomerRow.of(u);
    }

    @Transactional
    public void removeAdmin(long id, long actor) {
        User u = users.findById(id).filter(x -> x.getRole() == Role.ADMIN)
                .orElseThrow(() -> ApiException.notFound("Admin"));
        if (u.getId() == actor) {
            throw ApiException.badRequest("SELF_REMOVE", "Ask another admin to remove your access");
        }
        if (users.countByRole(Role.ADMIN) <= 1) {
            throw ApiException.badRequest("LAST_ADMIN", "At least one admin must remain");
        }
        u.setRole(Role.USER);
        refreshTokens.revokeAll(id);
        audit.record(actor, "ADMIN_REMOVED", "user", id, Map.of("role", "ADMIN"), Map.of("role", "USER"), null);
    }
}
