package com.singlepoint.entitlement;

import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Feature-gate seam. Everything resolves to "entitled" under the free launch plan (blueprint §8);
 * MVP-4 swaps the body for real plan/subscription checks without touching call sites.
 */
@Service
public class EntitlementService {

    public boolean isEntitled(UUID tenantId, String feature) {
        return true;
    }

    public void requireEntitled(UUID tenantId, String feature) {
        // no-op until MVP-4
    }

    public long remainingQuota(UUID tenantId, String feature) {
        return Long.MAX_VALUE;
    }
}
