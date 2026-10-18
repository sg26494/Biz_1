package com.store.payment.controller;

import com.store.common.error.ApiException;
import com.store.payment.gateway.PaymentGateway;
import com.store.payment.service.RazorpayWebhookService;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public, signature-verified. Reads the raw body (the signature is over the exact bytes). */
@RestController
@RequestMapping("/api/v1/webhooks")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final PaymentGateway gateway;
    private final RazorpayWebhookService razorpay;

    public WebhookController(PaymentGateway gateway, RazorpayWebhookService razorpay) {
        this.gateway = gateway;
        this.razorpay = razorpay;
    }

    @PostMapping("/razorpay")
    public ResponseEntity<Map<String, Object>> razorpay(
            @RequestBody byte[] body,
            @RequestHeader(name = "X-Razorpay-Signature", required = false) String signature,
            @RequestHeader(name = "X-Razorpay-Event-Id", required = false) String eventId) {
        if (signature == null || !gateway.verifyWebhookSignature(body, signature)) {
            log.warn("Rejected Razorpay webhook with bad signature");
            throw ApiException.badRequest("SIGNATURE_INVALID", "Invalid signature");
        }
        Optional<Long> stored = razorpay.store(body, eventId);
        if (stored.isEmpty()) {
            return ResponseEntity.ok(Map.of("status", "duplicate"));
        }
        razorpay.process(stored.get());
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
