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

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * MVP-13 (A2): Razorpay Subscriptions (mandate-backed recurring charges). Active only on the
 * {@code cloud} profile; plain REST, no SDK — same shape as {@link RazorpayGateway}. The live
 * calls to {@code /v1/plans} and {@code /v1/subscriptions} are the only part not exercised
 * locally; {@link StubRecurringGateway} covers the state machine in tests, and
 * {@link RazorpayGateway#verifyAndParse} already recognises the {@code subscription.*} webhooks.
 */
@Component
@Profile("cloud")
public class RazorpaySubscriptionGateway implements RecurringGateway {

    private static final String PLANS = "https://api.razorpay.com/v1/plans";
    private static final String SUBSCRIPTIONS = "https://api.razorpay.com/v1/subscriptions";

    private final RestTemplate rest = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String auth;
    private final int totalCount;

    public RazorpaySubscriptionGateway(@Value("${sp.payment.razorpay.key-id}") String keyId,
                                       @Value("${sp.payment.razorpay.key-secret}") String keySecret,
                                       @Value("${sp.payment.razorpay.subscription-total-count:120}") int totalCount) {
        this.auth = "Basic " + Base64.getEncoder()
                .encodeToString((keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8));
        this.totalCount = totalCount;
    }

    @Override public String name() { return "razorpay"; }

    @Override
    public Mandate createSubscription(String customerRef, String planCode, long amountMinor, String currency) {
        try {
            String planId = ensurePlan(planCode, amountMinor, currency);
            Map<String, Object> body = Map.of(
                    "plan_id", planId,
                    "total_count", totalCount,
                    "customer_notify", 1,
                    "notes", Map.of("planCode", planCode, "customerRef", customerRef));
            JsonNode n = mapper.readTree(exchange(SUBSCRIPTIONS, HttpMethod.POST, body).getBody());
            return new Mandate(n.get("id").asText(),
                    n.path("customer_id").asText(null),
                    n.path("short_url").asText(null));
        } catch (Exception e) {
            throw new AppException(ErrorCode.INTERNAL, "Could not create a Razorpay subscription", e);
        }
    }

    @Override
    public void cancelSubscription(String gatewaySubscriptionId) {
        try {
            exchange(SUBSCRIPTIONS + "/" + gatewaySubscriptionId + "/cancel", HttpMethod.POST,
                    Map.of("cancel_at_cycle_end", 0));
        } catch (Exception ignored) {
            // best effort
        }
    }

    /** Razorpay plan ids are opaque; a real deployment would cache planCode -> planId. */
    private String ensurePlan(String planCode, long amountMinor, String currency) throws Exception {
        Map<String, Object> body = Map.of(
                "period", "monthly", "interval", 1,
                "item", Map.of("name", planCode, "amount", amountMinor, "currency", currency));
        JsonNode n = mapper.readTree(exchange(PLANS, HttpMethod.POST, body).getBody());
        return n.get("id").asText();
    }

    private ResponseEntity<String> exchange(String url, HttpMethod method, Object body) throws Exception {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set(HttpHeaders.AUTHORIZATION, auth);
        return rest.exchange(url, method, new HttpEntity<>(mapper.writeValueAsString(body), h), String.class);
    }
}
