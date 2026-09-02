package com.singlepoint.entitlement;

import com.singlepoint.billing.BillingService;
import com.singlepoint.billing.domain.SubjectType;
import com.singlepoint.billing.domain.Subscription;
import com.singlepoint.billing.domain.SubscriptionPlan;
import com.singlepoint.common.error.AppException;
import com.singlepoint.common.error.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Resolves feature limits from a subject's effective subscription plan (its assigned plan, or
 * the FREE default) and enforces them. Sentinels in a plan's entitlement map:
 * {@code -1} unlimited, {@code 0} disabled, positive = hard cap per calendar month.
 */
@Service
public class EntitlementService {

    private final BillingService billing;

    public EntitlementService(BillingService billing) {
        this.billing = billing;
    }

    /** @return -1 unlimited, 0 disabled, positive cap. */
    public long limit(SubjectType type, UUID subjectId, String feature) {
        SubscriptionPlan plan = billing.effectivePlan(type, subjectId);
        return plan.limitFor(feature);
    }

    public boolean isEntitled(SubjectType type, UUID subjectId, String feature) {
        return limit(type, subjectId, feature) != 0;
    }

    /** True once a paid plan has lapsed past its grace window (subscription is EXPIRED). */
    public boolean isLapsed(SubjectType type, UUID subjectId) {
        return billing.activeSubscription(type, subjectId)
                .map(s -> s.getStatus() == Subscription.Status.EXPIRED)
                .orElse(false);
    }

    /** Reads still work when a plan lapses; only writes are blocked past the grace window. */
    public void assertWritable(SubjectType type, UUID subjectId) {
        if (isLapsed(type, subjectId)) {
            throw new AppException(ErrorCode.SUBSCRIPTION_LAPSED,
                    "This " + type.name().toLowerCase() + "'s plan has lapsed — renew to make changes");
        }
    }

    public void requireWithinQuota(SubjectType type, UUID subjectId, String feature, long currentUsage) {
        assertWritable(type, subjectId);
        long lim = limit(type, subjectId, feature);
        if (lim == 0) {
            throw new AppException(ErrorCode.QUOTA_EXCEEDED, "Your plan does not include " + humanise(feature));
        }
        if (lim > 0 && currentUsage >= lim) {
            throw new AppException(ErrorCode.QUOTA_EXCEEDED,
                    "You've reached your plan limit for " + humanise(feature) + " (" + lim + "). Upgrade to add more.");
        }
    }

    // ---- back-compat tenant-scoped shims (kept from the MVP-1 seam) --------

    public boolean isEntitled(UUID tenantId, String feature) {
        return isEntitled(SubjectType.TENANT, tenantId, feature);
    }

    public void requireEntitled(UUID tenantId, String feature) {
        if (!isEntitled(tenantId, feature)) {
            throw new AppException(ErrorCode.QUOTA_EXCEEDED, "Your plan does not include " + humanise(feature));
        }
    }

    public long remainingQuota(UUID tenantId, String feature) {
        long lim = limit(SubjectType.TENANT, tenantId, feature);
        return lim < 0 ? Long.MAX_VALUE : lim;
    }

    private static String humanise(String feature) {
        return feature.toLowerCase().replace('_', ' ');
    }
}
