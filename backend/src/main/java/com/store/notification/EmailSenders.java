package com.store.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.store.common.config.AppProperties;
import com.store.common.util.Json;
import jakarta.mail.internet.MimeMessage;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

/**
 * Pick the provider with EMAIL_PROVIDER:
 *  - smtp   : Mailpit locally, or Amazon SES via its SMTP interface in production
 *  - resend : Resend HTTP API
 *  - log    : print to logs (tests)
 */
@Configuration
public class EmailSenders {

    private static final Logger log = LoggerFactory.getLogger(EmailSenders.class);

    @Bean
    @ConditionalOnProperty(prefix = "app.email", name = "provider", havingValue = "smtp", matchIfMissing = true)
    public EmailSender smtpEmailSender(JavaMailSender mailSender, AppProperties props) {
        return (to, subject, html) -> {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, "UTF-8");
            helper.setFrom(props.email().from());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(msg);
            return msg.getMessageID();
        };
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.email", name = "provider", havingValue = "resend")
    public EmailSender resendEmailSender(HttpClient http, AppProperties props, Json json) {
        return (to, subject, html) -> {
            String body = json.write(Map.of(
                    "from", props.email().from(),
                    "to", List.of(to),
                    "subject", subject,
                    "html", html));
            HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.resend.com/emails"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + props.email().resendApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 300) {
                throw new IllegalStateException("Resend error " + res.statusCode() + ": " + res.body());
            }
            JsonNode node = json.read(res.body());
            return node.path("id").asText(null);
        };
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.email", name = "provider", havingValue = "log")
    public EmailSender logEmailSender() {
        return (to, subject, html) -> {
            log.info("EMAIL to={} subject={}\n{}", to, subject, html);
            return null;
        };
    }
}
