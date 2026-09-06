package com.singlepoint.broadcast;

import com.singlepoint.security.TenantScopedExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** MVP-13 (B3): every minute, fan out scheduled broadcasts whose time has come. */
@Component
public class BroadcastDispatchJob {

    private static final Logger log = LoggerFactory.getLogger(BroadcastDispatchJob.class);

    private final BroadcastService broadcastService;
    private final TenantScopedExecutor tenantScoped;

    public BroadcastDispatchJob(BroadcastService broadcastService, TenantScopedExecutor tenantScoped) {
        this.broadcastService = broadcastService;
        this.tenantScoped = tenantScoped;
    }

    @Scheduled(fixedDelayString = "${sp.broadcast.dispatch-delay-ms:60000}")
    public void run() {
        runNow();
    }

    /** Exposed for tests. */
    public int runNow() {
        int n = tenantScoped.inWildcard(broadcastService::dispatchDue);
        if (n > 0) log.info("Dispatched {} scheduled broadcast(s)", n);
        return n;
    }
}
