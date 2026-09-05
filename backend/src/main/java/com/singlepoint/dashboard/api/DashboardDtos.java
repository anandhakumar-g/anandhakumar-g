package com.singlepoint.dashboard.api;

import java.math.BigDecimal;
import java.util.Map;

public final class DashboardDtos {

    private DashboardDtos() { }

    /**
     * One flexible view, mirroring {@code MeResponse}'s style of a single shape whose fields
     * carry different meaning per role — only the caller's-role fields are populated, the
     * rest stay null.
     */
    public record DashboardView(
            String role,
            Map<String, Long> ticketsByStatus,

            // RESIDENT
            Long pendingApprovalCount,
            Long resolvedAwaitingCloseCount,
            Long pendingPaymentCount,
            Long activeOffersCount,

            // PROVIDER
            Long awaitingAcceptCount,
            BigDecimal ratingAvg,
            Integer ratingCount,

            // ADMIN
            Long unassignedCount,
            Long slaBreachedCount,
            Long pendingJoinRequestsCount,

            // SUPER_ADMIN
            Long totalOpenTickets,
            Long totalTickets,
            Long communityLessOpenTickets,
            Long providersPendingVerificationCount,
            Long offersPendingApprovalCount,
            Long communityCount) {
    }
}
