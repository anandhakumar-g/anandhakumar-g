package com.singlepoint.offer;

import com.singlepoint.security.TenantScopedExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Flips ACTIVE offers past their validity window to EXPIRED. */
@Component
public class OfferExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(OfferExpiryJob.class);

    private final OfferService offerService;
    private final TenantScopedExecutor tenantScoped;

    public OfferExpiryJob(OfferService offerService, TenantScopedExecutor tenantScoped) {
        this.offerService = offerService;
        this.tenantScoped = tenantScoped;
    }

    @Scheduled(cron = "${sp.offer.expiry.cron:0 5 * * * *}")
    public void run() {
        int n = tenantScoped.inWildcard(offerService::expireLapsedOffers);
        if (n > 0) log.info("Expired {} lapsed offer(s)", n);
    }
}
