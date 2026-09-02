package com.singlepoint.billing;

import com.singlepoint.security.TenantScopedExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily: rolls ACTIVE/COMPED subscriptions past their period end into the next period
 * (COMPED renews free; paid drops to PAST_DUE with a fresh DUE invoice + grace window),
 * and expires PAST_DUE subscriptions whose grace window has elapsed.
 */
@Component
public class BillingRenewalJob {

    private static final Logger log = LoggerFactory.getLogger(BillingRenewalJob.class);

    private final BillingService billing;
    private final TenantScopedExecutor tenantScoped;

    public BillingRenewalJob(BillingService billing, TenantScopedExecutor tenantScoped) {
        this.billing = billing;
        this.tenantScoped = tenantScoped;
    }

    @Scheduled(cron = "${sp.billing.renewal.cron:0 40 2 * * *}")
    public void run() {
        int n = tenantScoped.inWildcard(billing::runRenewal);
        if (n > 0) log.info("Billing renewal touched {} subscription(s)", n);
    }
}
