package com.store.jobs;

import com.store.auth.domain.RefreshTokenRepository;
import com.store.cart.service.CartService;
import com.store.common.outbox.EventTypes;
import com.store.common.outbox.OutboxService;
import com.store.common.settings.SettingsService;
import com.store.order.domain.OrderRepository;
import com.store.order.service.OrderService;
import com.store.payment.service.RazorpayWebhookService;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Background jobs (worker profile only). ShedLock makes each run once even with two instances.
 * Times are cron in IST.
 */
@Component
@ConditionalOnProperty(prefix = "app.jobs", name = "enabled", havingValue = "true")
public class ScheduledJobs {

    private static final Logger log = LoggerFactory.getLogger(ScheduledJobs.class);

    private final OrderRepository orders;
    private final OrderService orderService;
    private final RazorpayWebhookService webhooks;
    private final PaymentReconciliation reconciliation;
    private final RefreshTokenRepository refreshTokens;
    private final CartService carts;
    private final SettingsService settings;
    private final OutboxService outbox;
    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final Clock clock;

    public ScheduledJobs(OrderRepository orders, OrderService orderService, RazorpayWebhookService webhooks,
                         PaymentReconciliation reconciliation, RefreshTokenRepository refreshTokens,
                         CartService carts, SettingsService settings, OutboxService outbox, JdbcClient jdbc,
                         PlatformTransactionManager txManager, Clock clock) {
        this.orders = orders;
        this.orderService = orderService;
        this.webhooks = webhooks;
        this.reconciliation = reconciliation;
        this.refreshTokens = refreshTokens;
        this.carts = carts;
        this.settings = settings;
        this.outbox = outbox;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txManager);
        this.clock = clock;
    }

    /** Every minute: free stock held by abandoned checkouts and cancel those unpaid orders. */
    @Scheduled(cron = "0 * * * * *")
    @SchedulerLock(name = "release-expired-reservations", lockAtMostFor = "PT5M")
    public void releaseExpiredReservations() {
        List<Long> ids = orders.findExpiredUnpaid(clock.instant(), PageRequest.of(0, 200));
        int n = 0;
        for (Long id : ids) {
            try {
                if (orderService.expireIfUnpaid(id)) {
                    n++;
                }
            } catch (Exception e) {
                log.error("Could not expire order {}: {}", id, e.getMessage(), e);
            }
        }
        if (n > 0) {
            log.info("Expired {} unpaid orders", n);
        }
    }

    /** Every minute: retry webhooks that failed or were stored but never processed. */
    @Scheduled(cron = "30 * * * * *")
    @SchedulerLock(name = "webhook-retry", lockAtMostFor = "PT5M")
    public void retryWebhooks() {
        webhooks.retryPending();
    }

    /** Daily 06:00 IST: compare yesterday's Razorpay payments with ours. */
    @Scheduled(cron = "0 0 6 * * *", zone = "Asia/Kolkata")
    @SchedulerLock(name = "payment-reconciliation", lockAtMostFor = "PT30M")
    public void reconcilePayments() {
        reconciliation.runForYesterday();
    }

    /**
     * Daily 02:30 IST: DELIVERED orders past the return window become COMPLETED.
     * (Return requests arrive in phase 7; until then every delivered order qualifies.)
     */
    @Scheduled(cron = "0 30 2 * * *", zone = "Asia/Kolkata")
    @SchedulerLock(name = "auto-complete-orders", lockAtMostFor = "PT30M")
    public void autoCompleteOrders() {
        int days = settings.getInt(SettingsService.RETURN_WINDOW_DAYS, 7);
        tx.executeWithoutResult(s -> {
            int n = jdbc.sql("""
                    WITH done AS (
                        UPDATE orders SET status = 'COMPLETED', updated_at = now(), version = version + 1
                        WHERE status = 'DELIVERED' AND delivered_at < now() - make_interval(days => :d)
                        RETURNING id
                    )
                    INSERT INTO order_status_history (order_id, from_status, to_status, reason, actor_type)
                    SELECT id, 'DELIVERED', 'COMPLETED', 'Return window closed', 'SYSTEM' FROM done
                    """).param("d", days).update();
            if (n > 0) {
                log.info("Auto-completed {} orders", n);
            }
        });
    }

    /** Daily 03:00 IST: delete expired tokens, OTPs, idempotency keys and stale guest carts. */
    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Kolkata")
    @SchedulerLock(name = "cleanup", lockAtMostFor = "PT30M")
    public void cleanup() {
        tx.executeWithoutResult(s -> {
            int tokens = refreshTokens.deleteExpiredBefore(clock.instant().minus(Duration.ofDays(1)));
            int otps = jdbc.sql("DELETE FROM otp_requests WHERE expires_at < now() - interval '1 day'").update();
            int signup = jdbc.sql("DELETE FROM used_signup_tokens WHERE expires_at < now() - interval '1 day'").update();
            int idem = jdbc.sql("DELETE FROM idempotency_keys WHERE expires_at < now()").update();
            int outboxDone = jdbc.sql("DELETE FROM outbox_events WHERE status = 'DONE' AND processed_at < now() - interval '30 days'").update();
            log.info("Cleanup: tokens={} otps={} signup={} idem={} outbox={}", tokens, otps, signup, idem, outboxDone);
        });
        carts.deleteStaleGuestCarts();
    }

    /** Daily 09:00 IST: email admins the low-stock list. */
    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Kolkata")
    @SchedulerLock(name = "low-stock-alert", lockAtMostFor = "PT10M")
    public void lowStockAlert() {
        int threshold = settings.getInt(SettingsService.LOW_STOCK_DEFAULT, 3);
        List<String> lines = jdbc.sql("""
                SELECT pv.sku || ' - ' || p.title || ' (' || pv.title || '): ' || (i.on_hand - i.reserved) || ' left'
                FROM inventory i JOIN product_variants pv ON pv.id = i.variant_id JOIN products p ON p.id = pv.product_id
                WHERE p.status = 'ACTIVE' AND pv.is_active
                  AND (i.on_hand - i.reserved) <= COALESCE(i.low_stock_threshold, :t)
                ORDER BY (i.on_hand - i.reserved), p.title
                """).param("t", threshold).query(String.class).list();
        if (lines.isEmpty()) {
            return;
        }
        tx.executeWithoutResult(s -> outbox.enqueue("inventory", "low-stock", EventTypes.ADMIN_ALERT,
                Map.of("title", "Low stock: " + lines.size() + " item(s)", "detail", String.join("\n", lines))));
    }
}
