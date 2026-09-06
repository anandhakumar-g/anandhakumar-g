package com.singlepoint.payment.gateway;

import com.singlepoint.common.error.AppException;
import com.singlepoint.common.error.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/**
 * Local / test online-payment gateway. The "payment link" is a dev page served by
 * DevPaymentController; that page self-posts a signed synthetic webhook back to the app.
 */
@Component
@Profile("!cloud")
public class StubGateway implements PaymentGateway {

    private final String linkBase;
    private final byte[] secret;

    public StubGateway(@Value("${sp.storage.local.public-base-url:http://localhost:18080/api/v1/files}") String publicBase,
                       @Value("${sp.payment.stub-secret:local-stub-secret}") String secret) {
        // reuse the app origin for the dev pay page
        this.linkBase = publicBase.replaceFirst("/api/v1/files/?$", "") + "/dev/pay/";
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    @Override public String name() { return "stub"; }

    @Override
    public PaymentLink createPaymentLink(BigDecimal amount, String currency, String description, String refId) {
        String ref = "stub_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        return new PaymentLink(ref, linkBase + ref);
    }

    @Override
    public WebhookResult verifyAndParse(Map<String, String> headers, String rawBody) {
        String sig = headers.get("x-stub-signature");
        if (sig == null || !sign(rawBody).equals(sig)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "invalid webhook signature");
        }
        // payment link:  {"ref":"stub_xxx","paymentId":"pay_xxx","paid":true}
        // subscription:  {"subscriptionRef":"stub_sub_xxx","event":"subscription.charged","paid":true}
        boolean paid = rawBody.contains("\"paid\":true") || rawBody.contains("\"paid\": true");
        String subRef = extract(rawBody, "subscriptionRef");
        if (subRef != null) {
            return new WebhookResult(subRef, null, paid, extract(rawBody, "event"));
        }
        return new WebhookResult(extract(rawBody, "ref"), extract(rawBody, "paymentId"), paid, null);
    }

    /** Exposed so DevPaymentController can produce a valid synthetic webhook. */
    public String sign(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] h = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(h.length * 2);
            for (byte b : h) sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            return sb.toString();
        } catch (Exception e) {
            throw new AppException(ErrorCode.INTERNAL, "stub signing failed", e);
        }
    }

    private static String extract(String json, String key) {
        String needle = "\"" + key + "\"";
        int i = json.indexOf(needle);
        if (i < 0) return null;
        int c = json.indexOf(':', i) + 1;
        while (c < json.length() && (json.charAt(c) == ' ' || json.charAt(c) == '"')) c++;
        int end = c;
        while (end < json.length() && "\",}".indexOf(json.charAt(end)) < 0) end++;
        return json.substring(c, end);
    }
}
