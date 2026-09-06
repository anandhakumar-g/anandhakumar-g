package com.singlepoint.payment.gateway;

/**
 * A handler the payment webhook consults when a callback's gateway reference does not match a
 * ticket payment (e.g. a subscription invoice). Implemented outside the payment package to
 * keep the dependency direction one-way.
 */
public interface WebhookFallback {

    /**
     * @param event the gateway's event name (e.g. {@code subscription.charged} / {@code .halted}
     *              / {@code .cancelled}), or null for a plain payment-link callback.
     * @return true if this fallback recognised the reference (and applied it).
     */
    boolean tryHandle(String gatewayRef, boolean paid, String event);
}
