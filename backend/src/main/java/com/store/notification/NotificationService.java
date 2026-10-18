package com.store.notification;

import com.store.common.config.AppProperties;
import com.store.common.util.Money;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Renders Thymeleaf email templates (resources/templates/email/*.html) and sends them.
 * Every attempt is logged in `notifications`. Called by outbox handlers (so failures are retried),
 * except OTP emails which are sent inline for speed.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    public static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH);

    private final EmailSender sender;
    private final JdbcClient jdbc;
    private final AppProperties props;
    private final TemplateEngine engine;
    private final com.store.common.util.Json json;

    public NotificationService(EmailSender sender, JdbcClient jdbc, AppProperties props,
                               com.store.common.util.Json json) {
        this.sender = sender;
        this.jdbc = jdbc;
        this.props = props;
        this.json = json;
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/email/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(true);
        this.engine = new TemplateEngine();
        this.engine.setTemplateResolver(resolver);
    }

    public String render(String template, Map<String, Object> model) {
        Context ctx = new Context(Locale.ENGLISH);
        // Records/DTOs become plain maps so templates can use ${order.orderNumber} (Thymeleaf's OGNL
        // does not understand record accessors). Instants become ISO strings; use money.istDate(...) to show them.
        @SuppressWarnings("unchecked")
        Map<String, Object> vars = new HashMap<>(json.mapper().convertValue(model, Map.class));
        vars.put("siteUrl", props.publicBaseUrl());
        vars.put("money", new MoneyFormatter());
        ctx.setVariables(vars);
        return engine.process(template, ctx);
    }

    /** Sends and logs. Throws on failure so the caller (outbox worker) can retry. */
    public void send(String template, String to, String subject, Map<String, Object> model, Long userId,
                     Long outboxEventId) throws Exception {
        String html = render(template, model);
        try {
            String messageId = sender.send(to, subject, html);
            logRow(userId, to, template, subject, "SENT", messageId, null, outboxEventId);
        } catch (Exception e) {
            logRow(userId, to, template, subject, "FAILED", null, e.getMessage(), outboxEventId);
            throw e;
        }
    }

    public void sendToAdmins(String template, String subject, Map<String, Object> model, Long outboxEventId)
            throws Exception {
        List<String> to = props.email().adminAlertTo();
        if (to == null) {
            return;
        }
        for (String addr : to) {
            if (addr != null && !addr.isBlank()) {
                send(template, addr.trim(), subject, model, null, outboxEventId);
            }
        }
    }

    private void logRow(Long userId, String to, String template, String subject, String status, String messageId,
                        String error, Long outboxEventId) {
        try {
            jdbc.sql("""
                    INSERT INTO notifications (user_id, to_address, template, subject, status, provider_message_id,
                                               error, outbox_event_id)
                    VALUES (:uid, :to, :tpl, :subject, :status, :mid, :err, :oid)
                    """)
                    .param("uid", userId).param("to", to).param("tpl", template).param("subject", subject)
                    .param("status", status).param("mid", messageId).param("err", error).param("oid", outboxEventId)
                    .update();
        } catch (Exception e) {
            log.warn("Could not log notification: {}", e.getMessage());
        }
    }

    /** Exposed to templates as ${money.format(paise)}. */
    public static class MoneyFormatter {
        public String format(Number paise) {
            return paise == null ? "" : Money.format(paise.longValue());
        }

        public String istDate(String isoInstant) {
            return isoInstant == null || isoInstant.isBlank() ? ""
                    : DATE_FMT.format(java.time.Instant.parse(isoInstant).atZone(IST));
        }
    }
}
