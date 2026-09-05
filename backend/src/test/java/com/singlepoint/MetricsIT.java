package com.singlepoint;

import com.singlepoint.support.IntegrationTestBase;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MVP-12 (C): the Prometheus registry is on the classpath, carries the {@code application}
 * common tag, and exposes our {@code sp_*} domain counters after a domain action. Asserts
 * against the registry's own {@code scrape()} output — the exact bytes {@code
 * /actuator/prometheus} serves — so it doesn't depend on the management port / security.
 */
class MetricsIT extends IntegrationTestBase {

    @Autowired(required = false)
    private PrometheusMeterRegistry prometheus;

    @Test
    void prometheusRegistryIsWiredAndTagged() {
        assertNotNull(prometheus, "micrometer-registry-prometheus should be on the classpath");
        String scrape = prometheus.scrape();
        assertTrue(scrape.contains("application=\"single-point\""),
                "the management.metrics.tags.application common tag should be applied");
        assertTrue(scrape.contains("jvm_"), "Boot should auto-instrument JVM meters");
    }

    @Test
    void raisingATicketIncrementsTheDomainCounter() {
        Marketplace mk = marketplace("+919000000601", "+919000000701");
        String resident = joinResident(mk.tenantId(), mk.adminToken(), "+919888000061", "Ivy");
        String cat = firstCategoryId(resident, "Electrical");
        double before = prometheus.get("sp.tickets.raised").counter().count();

        post("/api/v1/tickets", resident, Map.of(
                "categoryId", cat, "description", "meter me", "serviceAddressText", "A-1"));

        double after = prometheus.get("sp.tickets.raised").counter().count();
        assertTrue(after >= before + 1.0, "sp.tickets.raised should count the new ticket");
        assertTrue(prometheus.scrape().contains("sp_tickets_raised_total"),
                "the counter should render on the scrape endpoint");
    }
}
