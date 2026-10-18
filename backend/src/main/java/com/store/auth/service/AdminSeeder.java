package com.store.auth.service;

import com.store.common.audit.AuditService;
import com.store.common.config.AppProperties;
import com.store.user.domain.Role;
import com.store.user.domain.User;
import com.store.user.domain.UserRepository;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin is never self-service. On API startup, every email in ADMIN_SEED_EMAILS becomes an ADMIN
 * (existing accounts are promoted; new ones are created and finish login via Google or email OTP).
 * After launch, admins add each other from the admin panel.
 */
@Component
@ConditionalOnWebApplication
public class AdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final UserRepository users;
    private final AuditService audit;
    private final List<String> seedEmails;

    public AdminSeeder(UserRepository users, AuditService audit, AppProperties props) {
        this.users = users;
        this.audit = audit;
        this.seedEmails = props.admin() == null || props.admin().seedEmails() == null
                ? List.of() : props.admin().seedEmails();
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (String raw : seedEmails) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String email = raw.trim().toLowerCase();
            User u = users.findByEmailIgnoreCase(email).orElse(null);
            if (u == null) {
                u = new User();
                u.setEmail(email);
                u.setName(email.substring(0, email.indexOf('@')));
                u.setRole(Role.ADMIN);
                users.save(u);
                audit.record(null, "ADMIN_SEEDED", "user", email, null, Map.of("role", "ADMIN"), "seed");
                log.info("Seeded admin {}", email);
            } else if (u.getRole() != Role.ADMIN) {
                u.setRole(Role.ADMIN);
                audit.record(null, "ADMIN_SEEDED", "user", u.getId(), Map.of("role", "USER"), Map.of("role", "ADMIN"),
                        "seed");
                log.info("Promoted {} to admin", email);
            }
        }
    }
}
