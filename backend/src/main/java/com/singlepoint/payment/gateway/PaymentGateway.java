package com.singlepoint.payment.gateway;

import java.math.BigDecimal;
import java.util.Map;

/** Abstraction over an online payment provider. Local uses {@link StubGateway}; cloud uses Razorpay. */
public interface PaymentGateway {

    String name();

    /** Create a hosted payment link the resident can pay through. */
    PaymentLink createPaymentLink(BigDecimal amount, String currency, String description, String refId);

    /** Verify a webhook's signature and extract the outcome. Throws if the signature is invalid. */
    WebhookResult verifyAndParse(Map<String, String> headers, String rawBody);

    record PaymentLink(String gatewayRef, String url) { }

    /** {@code event} is the gateway's event name (e.g. {@code subscription.charged}), or null for a plain payment-link callback. */
    record WebhookResult(String gatewayRef, String gatewayPaymentId, boolean paid, String event) { }
}
