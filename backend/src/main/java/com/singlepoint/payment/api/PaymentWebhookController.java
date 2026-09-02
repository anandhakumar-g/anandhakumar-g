package com.singlepoint.payment.api;

import com.singlepoint.payment.PaymentService;
import com.singlepoint.security.TenantScopedExecutor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Unauthenticated gateway callback (allow-listed in SecurityConfig). Trust comes from the
 * gateway signature verified inside {@link PaymentService#handleWebhook}.
 */
@RestController
@RequestMapping("/api/v1/payments/webhook")
@Tag(name = "Payments — webhook", description = "Gateway payment callbacks")
public class PaymentWebhookController {

    private final PaymentService paymentService;
    private final TenantScopedExecutor tenantScoped;

    public PaymentWebhookController(PaymentService paymentService, TenantScopedExecutor tenantScoped) {
        this.paymentService = paymentService;
        this.tenantScoped = tenantScoped;
    }

    @PostMapping("/{gateway}")
    @Operation(summary = "Receive a payment outcome from the gateway")
    public ResponseEntity<Void> receive(@PathVariable String gateway, HttpEntity<byte[]> request) {
        String rawBody = request.getBody() == null ? "" : new String(request.getBody(), StandardCharsets.UTF_8);
        Map<String, String> headers = new LinkedHashMap<>();
        request.getHeaders().forEach((k, v) -> headers.put(k.toLowerCase(), String.join(",", v)));
        tenantScoped.inWildcard(() -> paymentService.handleWebhook(gateway, headers, rawBody));
        return ResponseEntity.ok().build();
    }
}
