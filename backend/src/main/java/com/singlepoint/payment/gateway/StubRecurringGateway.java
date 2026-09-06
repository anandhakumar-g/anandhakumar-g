package com.singlepoint.payment.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Local / test recurring gateway. {@link #createSubscription} returns a synthetic id and an
 * {@code authUrl} pointing at the dev checkout page, which on "pay" posts a signed synthetic
 * {@code subscription.charged} webhook — so the whole recurring loop runs with no real Razorpay.
 */
@Component
@Profile("!cloud")
public class StubRecurringGateway implements RecurringGateway {

    private final String payBase;

    public StubRecurringGateway(
            @Value("${sp.storage.local.public-base-url:http://localhost:18080/api/v1/files}") String publicBase) {
        this.payBase = publicBase.replaceFirst("/api/v1/files/?$", "") + "/dev/pay/sub/";
    }

    @Override public String name() { return "stub"; }

    @Override
    public Mandate createSubscription(String customerRef, String planCode, long amountMinor, String currency) {
        String sub = "stub_sub_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String cust = "stub_cust_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return new Mandate(sub, cust, payBase + sub);
    }

    @Override
    public void cancelSubscription(String gatewaySubscriptionId) {
        // no-op — nothing is really scheduled
    }
}
