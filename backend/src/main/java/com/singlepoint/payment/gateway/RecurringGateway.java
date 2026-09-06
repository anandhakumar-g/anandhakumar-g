package com.singlepoint.payment.gateway;

/**
 * MVP-13 (A2): the recurring / mandate surface of an online payment provider, kept separate
 * from {@link PaymentGateway}'s one-off payment links. Local uses {@code StubRecurringGateway};
 * cloud uses {@code RazorpaySubscriptionGateway}. Exactly one bean is active per profile.
 */
public interface RecurringGateway {

    String name();

    /**
     * Create a gateway subscription that will auto-charge {@code amountMinor} every period.
     * The returned {@code authUrl} is where the payer confirms the mandate (Razorpay's
     * subscription short-url; a dev page on the stub).
     */
    Mandate createSubscription(String customerRef, String planCode, long amountMinor, String currency);

    /** Best-effort — a no-op if the gateway subscription is already gone. */
    void cancelSubscription(String gatewaySubscriptionId);

    record Mandate(String gatewaySubscriptionId, String gatewayCustomerId, String authUrl) { }
}
