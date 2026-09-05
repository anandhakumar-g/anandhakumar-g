package com.singlepoint.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * MVP-12 (C): a handful of high-signal domain counters, exported through the Prometheus
 * registry alongside Boot's auto-instrumented HTTP / Hikari / JVM meters. Each maps to a
 * {@code sp_*_total} series on {@code /actuator/prometheus}.
 */
@Component
public class AppMetrics {

    private final Counter ticketsRaised;
    private final Counter offersRedeemed;
    private final Counter broadcastsSent;
    private final Counter communitiesSelfOnboarded;

    public AppMetrics(MeterRegistry registry) {
        this.ticketsRaised = Counter.builder("sp.tickets.raised")
                .description("Tickets created by residents or flat owners").register(registry);
        this.offersRedeemed = Counter.builder("sp.offers.redeemed")
                .description("Offer redemptions recorded").register(registry);
        this.broadcastsSent = Counter.builder("sp.broadcasts.sent")
                .description("Broadcast messages dispatched").register(registry);
        this.communitiesSelfOnboarded = Counter.builder("sp.communities.self_onboarded")
                .description("Self-onboarded communities approved by a Super Admin").register(registry);
    }

    public void ticketRaised() {
        ticketsRaised.increment();
    }

    public void offerRedeemed() {
        offersRedeemed.increment();
    }

    public void broadcastSent() {
        broadcastsSent.increment();
    }

    public void communitySelfOnboarded() {
        communitiesSelfOnboarded.increment();
    }
}
