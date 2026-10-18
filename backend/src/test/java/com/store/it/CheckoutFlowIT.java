package com.store.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.store.auth.security.JwtService;
import com.store.inventory.service.InventoryService;
import com.store.order.service.OrderService;
import com.store.payment.gateway.PaymentGateway;
import com.store.user.domain.Role;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end order flow against a real Postgres: cart -> checkout (reserve) -> webhook (commit) -> cancel (refund),
 * plus idempotency, duplicate webhooks, overselling and the expired-reservation / late-capture edge case.
 * Razorpay is mocked at the PaymentGateway interface.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class CheckoutFlowIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final AtomicInteger SEQ = new AtomicInteger();

    @MockitoBean
    PaymentGateway gateway;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcClient jdbc;
    @Autowired JwtService jwt;
    @Autowired InventoryService inventory;
    @Autowired OrderService orderService;
    @Autowired PlatformTransactionManager txManager;

    long adminId;

    @BeforeEach
    void setUp() {
        when(gateway.keyId()).thenReturn("rzp_test_key");
        when(gateway.verifyWebhookSignature(any(), anyString())).thenReturn(true);
        when(gateway.createOrder(anyLong(), anyString(), anyMap())).thenAnswer(inv ->
                new PaymentGateway.GatewayOrder("order_" + UUID.randomUUID().toString().substring(0, 12),
                        inv.getArgument(0), "INR", inv.getArgument(1)));
        adminId = createUser(Role.ADMIN);
    }

    @Test
    void fullFlowReserveCommitCancelRefund() throws Exception {
        long variantId = createVariant(249_900, 5);
        long userId = createUser(Role.USER);
        String token = jwt.issueAccessToken(userId, Role.USER);
        long addressId = createAddress(token, "Rajasthan");

        addToCart(token, variantId, 2).andExpect(status().isOk());

        String key = UUID.randomUUID().toString();
        JsonNode co = checkout(token, key, addressId);
        long orderId = co.path("orderId").asLong();
        String gwOrder = co.path("razorpayOrderId").asText();
        assertThat(co.path("amountPaise").asLong()).isEqualTo(2 * 249_900 + 0); // above free-shipping threshold
        assertThat(stock(variantId)).containsExactly(5, 2);

        // Same Idempotency-Key -> same order, no second gateway order.
        JsonNode again = checkout(token, key, addressId);
        assertThat(again.path("orderId").asLong()).isEqualTo(orderId);
        verify(gateway, times(1)).createOrder(anyLong(), anyString(), anyMap());

        // Client callback only says "processing".
        when(gateway.verifyPaymentSignature(gwOrder, "pay_1", "sig")).thenReturn(true);
        mvc.perform(post("/api/v1/payments/verify").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"razorpayOrderId\":\"" + gwOrder + "\",\"razorpayPaymentId\":\"pay_1\",\"razorpaySignature\":\"sig\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PROCESSING"));

        // Webhook is the source of truth; duplicates are dropped.
        webhook("evt_1", "payment.captured", gwOrder, "pay_1", 499_800).andExpect(jsonPath("$.status").value("ok"));
        webhook("evt_1", "payment.captured", gwOrder, "pay_1", 499_800).andExpect(jsonPath("$.status").value("duplicate"));
        webhook("evt_2", "order.paid", gwOrder, "pay_1", 499_800).andExpect(status().isOk());

        assertThat(orderStatus(orderId)).isEqualTo("PAID");
        assertThat(stock(variantId)).containsExactly(3, 0);
        assertThat(count("SELECT COUNT(*) FROM outbox_events WHERE event_type = 'OrderPaid' AND aggregate_id = '" + orderId + "'"))
                .isEqualTo(1);

        // User cancels before packing: stock back, full refund queued.
        mvc.perform(post("/api/v1/orders/" + orderId + "/cancel").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Changed my mind\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(stock(variantId)).containsExactly(5, 0);
        assertThat(count("SELECT COUNT(*) FROM refunds WHERE order_id = " + orderId + " AND amount_paise = 499800 AND status = 'PENDING'"))
                .isEqualTo(1);
    }

    @Test
    void cannotOversell() throws Exception {
        long variantId = createVariant(149_900, 1);
        String a = jwt.issueAccessToken(createUser(Role.USER), Role.USER);
        String b = jwt.issueAccessToken(createUser(Role.USER), Role.USER);
        long addrA = createAddress(a, "Karnataka");
        long addrB = createAddress(b, "Karnataka");
        addToCart(a, variantId, 1).andExpect(status().isOk());
        addToCart(b, variantId, 1).andExpect(status().isOk());

        checkout(a, UUID.randomUUID().toString(), addrA);
        mvc.perform(post("/api/v1/checkout").header("Authorization", "Bearer " + b)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"addressId\":" + addrB + "}"))
                .andExpect(status().isConflict());
        assertThat(stock(variantId)).containsExactly(1, 1);
    }

    @Test
    void lateCaptureAfterExpiryIsAcceptedWhenStockRemains() throws Exception {
        long variantId = createVariant(99_900, 3);
        long userId = createUser(Role.USER);
        String token = jwt.issueAccessToken(userId, Role.USER);
        long addressId = createAddress(token, "Maharashtra");
        addToCart(token, variantId, 1).andExpect(status().isOk());
        JsonNode co = checkout(token, UUID.randomUUID().toString(), addressId);
        long orderId = co.path("orderId").asLong();

        jdbc.sql("UPDATE orders SET payment_expires_at = now() - interval '1 minute' WHERE id = :id").param("id", orderId).update();
        assertThat(orderService.expireIfUnpaid(orderId)).isTrue();
        assertThat(orderStatus(orderId)).isEqualTo("CANCELLED");
        assertThat(stock(variantId)).containsExactly(3, 0);

        webhook("evt_late", "payment.captured", co.path("razorpayOrderId").asText(), "pay_late", co.path("amountPaise").asLong())
                .andExpect(status().isOk());
        assertThat(orderStatus(orderId)).isEqualTo("PAID");
        assertThat(stock(variantId)).containsExactly(2, 0);
    }

    @Test
    void adminEndpointsRequireAdmin() throws Exception {
        String user = jwt.issueAccessToken(createUser(Role.USER), Role.USER);
        mvc.perform(get("/api/v1/admin/dashboard").header("Authorization", "Bearer " + user))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/dashboard").header("Authorization", "Bearer " + jwt.issueAccessToken(adminId, Role.ADMIN)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/orders")).andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private long createUser(Role role) {
        int n = SEQ.incrementAndGet();
        String phone = "9" + String.format("%09d", (System.nanoTime() + n) % 1_000_000_000L);
        return jdbc.sql("""
                INSERT INTO users (email, name, phone, role) VALUES (:e, :n, :p, :r) RETURNING id
                """).param("e", "user" + n + "-" + UUID.randomUUID() + "@example.com").param("n", "Test User")
                .param("p", phone).param("r", role.name()).query(Long.class).single();
    }

    private long createVariant(long pricePaise, int stock) {
        int n = SEQ.incrementAndGet();
        long categoryId = jdbc.sql("SELECT id FROM categories WHERE slug = 'jaipuri-razai'").query(Long.class).single();
        long productId = jdbc.sql("""
                INSERT INTO products (category_id, title, slug, status, hsn_code, gst_rate_bp)
                VALUES (:c, :t, :s, 'ACTIVE', '6304', 500) RETURNING id
                """).param("c", categoryId).param("t", "Razai " + n).param("s", "razai-" + n + "-" + UUID.randomUUID())
                .query(Long.class).single();
        long variantId = jdbc.sql("""
                INSERT INTO product_variants (product_id, sku, title, attributes, mrp_paise, price_paise, weight_grams,
                                              length_cm, breadth_cm, height_cm)
                VALUES (:p, :sku, 'Double / Indigo', '{"size":"Double"}'::jsonb, :mrp, :price, 1800, 40, 30, 15)
                RETURNING id
                """).param("p", productId).param("sku", "SKU-" + n + "-" + UUID.randomUUID().toString().substring(0, 6))
                .param("mrp", pricePaise + 50_000).param("price", pricePaise).query(Long.class).single();
        new TransactionTemplate(txManager).executeWithoutResult(s -> inventory.ensureRow(variantId));
        inventory.adjust(variantId, InventoryService.AdjustReason.PURCHASE, stock, "test stock", adminId);
        return variantId;
    }

    private long createAddress(String token, String state) throws Exception {
        String body = """
                {"name":"Asha Verma","phone":"9876543210","line1":"12 MG Road","city":"Bengaluru",
                 "state":"%s","pincode":"560001","isDefault":true}
                """.formatted(state);
        String res = mvc.perform(post("/api/v1/me/addresses").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(res).path("id").asLong();
    }

    private org.springframework.test.web.servlet.ResultActions addToCart(String token, long variantId, int qty)
            throws Exception {
        return mvc.perform(post("/api/v1/cart/items").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"variantId\":" + variantId + ",\"qty\":" + qty + "}"));
    }

    private JsonNode checkout(String token, String key, long addressId) throws Exception {
        String res = mvc.perform(post("/api/v1/checkout").header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"addressId\":" + addressId + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(res);
    }

    private org.springframework.test.web.servlet.ResultActions webhook(String eventId, String event, String gwOrder,
                                                                     String paymentId, long amount) throws Exception {
        String body = """
                {"event":"%s","payload":{"payment":{"entity":{"id":"%s","order_id":"%s","amount":%d,
                 "status":"captured","method":"upi"}}}}
                """.formatted(event, paymentId, gwOrder, amount);
        return mvc.perform(post("/api/v1/webhooks/razorpay")
                .header("X-Razorpay-Signature", "sig").header("X-Razorpay-Event-Id", eventId)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String orderStatus(long orderId) {
        return jdbc.sql("SELECT status FROM orders WHERE id = :id").param("id", orderId).query(String.class).single();
    }

    private Integer[] stock(long variantId) {
        return jdbc.sql("SELECT on_hand, reserved FROM inventory WHERE variant_id = :v").param("v", variantId)
                .query((rs, i) -> new Integer[] {rs.getInt(1), rs.getInt(2)}).single();
    }

    private long count(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }
}
