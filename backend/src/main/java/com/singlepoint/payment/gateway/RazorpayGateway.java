package com.singlepoint.payment.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.singlepoint.common.error.AppException;
import com.singlepoint.common.error.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Razorpay Payment Links + webhook verification. Active only on the {@code cloud} profile.
 * Uses plain REST (no SDK dependency). Live HTTP calls are the only part not exercised locally.
 */
@Component
@Profile("cloud")
public class RazorpayGateway implements PaymentGateway {

    private static final String API = "https://api.razorpay.com/v1/payment_links";

    private final RestTemplate rest = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String keyId;
    private final String keySecret;
    private final byte[] webhookSecret;

    public RazorpayGateway(@Value("${sp.payment.razorpay.key-id}") String keyId,
                           @Value("${sp.payment.razorpay.key-secret}") String keySecret,
                           @Value("${sp.payment.razorpay.webhook-secret}") String webhookSecret) {
        this.keyId = keyId;
        this.keySecret = keySecret;
        this.webhookSecret = webhookSecret.getBytes(StandardCharsets.UTF_8);
    }

    @Override public String name() { return "razorpay"; }

    @Override
    public PaymentLink createPaymentLink(BigDecimal amount, String currency, String description, String refId) {
        long paise = amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
        Map<String, Object> body = Map.of(
                "amount", paise, "currency", currency, "description", description,
                "reference_id", refId, "reminder_enable", true,
                "notes", Map.of("refId", refId));
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set(HttpHeaders.AUTHORIZATION, "Basic " + Base64.getEncoder()
                .encodeToString((keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8)));
        try {
            ResponseEntity<String> r = rest.postForEntity(API,
                    new HttpEntity<>(mapper.writeValueAsString(body), h), String.class);
            JsonNode n = mapper.readTree(r.getBody());
            return new PaymentLink(n.get("id").asText(), n.get("short_url").asText());
        } catch (Exception e) {
            throw new AppException(ErrorCode.INTERNAL, "Could not create a payment link", e);
        }
    }

    @Override
    public WebhookResult verifyAndParse(Map<String, String> headers, String rawBody) {
        String sig = headers.get("x-razorpay-signature");
        if (sig == null || !hmacHex(rawBody).equals(sig)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "invalid webhook signature");
        }
        try {
            JsonNode n = mapper.readTree(rawBody);
            String event = n.path("event").asText("");
            JsonNode payload = n.path("payload");
            String plinkId = payload.path("payment_link").path("entity").path("id").asText(null);
            String paymentId = payload.path("payment").path("entity").path("id").asText(null);
            boolean paid = event.equals("payment_link.paid")
                    || event.equals("payment.captured")
                    || "paid".equals(payload.path("payment_link").path("entity").path("status").asText());
            return new WebhookResult(plinkId, paymentId, paid);
        } catch (Exception e) {
            throw new AppException(ErrorCode.BAD_REQUEST, "unparseable webhook body");
        }
    }

    private String hmacHex(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret, "HmacSHA256"));
            byte[] d = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            return sb.toString();
        } catch (Exception e) {
            throw new AppException(ErrorCode.INTERNAL, "hmac failed", e);
        }
    }
}
