package com.singlepoint.dashboard;

import com.singlepoint.common.error.AppException;
import com.singlepoint.common.error.ErrorCode;
import com.singlepoint.dashboard.api.DashboardDtos.DashboardView;
import com.singlepoint.offer.OfferRepository;
import com.singlepoint.offer.OfferService;
import com.singlepoint.offer.domain.Offer;
import com.singlepoint.payment.TicketPaymentRepository;
import com.singlepoint.payment.domain.TicketPayment;
import com.singlepoint.provider.ServiceProviderRepository;
import com.singlepoint.provider.domain.ServiceProvider;
import com.singlepoint.provider.domain.VerificationStatus;
import com.singlepoint.security.AppPrincipal;
import com.singlepoint.tenant.TenantRepository;
import com.singlepoint.ticket.TicketRepository;
import com.singlepoint.ticket.domain.TicketStatus;
import com.singlepoint.user.UserTenantMembershipRepository;
import com.singlepoint.user.domain.MembershipStatus;
import com.singlepoint.user.domain.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** MVP-10 (A): one role-branching summary — tickets by status + the action items that need this caller. */
@Service
public class DashboardService {

    private static final List<TicketPayment.Status> SETTLED = List.of(
            TicketPayment.Status.PAID_ONLINE, TicketPayment.Status.PAID_CASH,
            TicketPayment.Status.WAIVED, TicketPayment.Status.FAILED);
    private static final List<TicketStatus> UNASSIGNABLE_OPEN =
            List.of(TicketStatus.NEW, TicketStatus.ACKNOWLEDGED);

    private final TicketRepository tickets;
    private final TicketPaymentRepository payments;
    private final OfferRepository offers;
    private final OfferService offerService;
    private final ServiceProviderRepository providers;
    private final UserTenantMembershipRepository memberships;
    private final TenantRepository tenants;

    public DashboardService(TicketRepository tickets, TicketPaymentRepository payments, OfferRepository offers,
                            OfferService offerService, ServiceProviderRepository providers,
                            UserTenantMembershipRepository memberships, TenantRepository tenants) {
        this.tickets = tickets;
        this.payments = payments;
        this.offers = offers;
        this.offerService = offerService;
        this.providers = providers;
        this.memberships = memberships;
        this.tenants = tenants;
    }

    @Transactional(readOnly = true)
    public DashboardView forPrincipal(AppPrincipal principal) {
        return switch (principal.getRole()) {
            case RESIDENT -> residentView(principal);
            case PROVIDER -> providerView(principal);
            case ADMIN -> adminView(principal);
            case SUPER_ADMIN -> superAdminView(principal);
        };
    }

    private DashboardView residentView(AppPrincipal principal) {
        Map<String, Long> byStatus = bucket(tickets.countByStatusForRaiser(principal.getUserId()));
        long pendingApproval = byStatus.getOrDefault(TicketStatus.PENDING_RESIDENT_APPROVAL.name(), 0L);
        long resolvedAwaitingClose = byStatus.getOrDefault(TicketStatus.RESOLVED.name(), 0L);
        long pendingPayment = payments.findUnsettledForRaiserAcrossTenants(principal.getUserId(), SETTLED).size();
        long activeOffers = offerService.feedForResident(principal).size();
        return new DashboardView(Role.RESIDENT.name(), byStatus,
                pendingApproval, resolvedAwaitingClose, pendingPayment, activeOffers,
                null, null, null,
                null, null, null,
                null, null, null, null, null, null);
    }

    private DashboardView providerView(AppPrincipal principal) {
        ServiceProvider provider = providers.findByUserId(principal.getUserId()).orElse(null);
        if (provider == null) {
            return new DashboardView(Role.PROVIDER.name(), Map.of(), null, null, null, null,
                    0L, null, null, null, null, null, null, null, null, null, null, null);
        }
        Map<String, Long> byStatus = bucket(tickets.countByStatusForProvider(provider.getId()));
        long awaitingAccept = byStatus.getOrDefault(TicketStatus.ASSIGNED.name(), 0L);
        return new DashboardView(Role.PROVIDER.name(), byStatus, null, null, null, null,
                awaitingAccept, provider.getRatingAvg(), provider.getRatingCount(),
                null, null, null,
                null, null, null, null, null, null);
    }

    private DashboardView adminView(AppPrincipal principal) {
        UUID tenantId = principal.getTenantId();
        if (tenantId == null) throw new AppException(ErrorCode.FORBIDDEN, "No active community");
        Map<String, Long> byStatus = bucket(tickets.countByStatusForTenant(tenantId));
        long unassigned = tickets.countByTenantIdAndStatusInAndAssignedProviderIdIsNull(tenantId, UNASSIGNABLE_OPEN);
        long slaBreached = tickets.countSlaBreachedForTenant(tenantId, TicketStatus.CLOSED);
        long pendingJoinRequests = memberships.findByTenantIdAndStatus(tenantId, MembershipStatus.PENDING_APPROVAL).size();
        return new DashboardView(Role.ADMIN.name(), byStatus, null, null, null, null,
                null, null, null,
                unassigned, slaBreached, pendingJoinRequests,
                null, null, null, null, null, null);
    }

    private DashboardView superAdminView(AppPrincipal principal) {
        long totalOpen = tickets.countByStatusNot(TicketStatus.CLOSED);
        long total = tickets.count();
        long communityLessOpen = tickets.countByTenantIdIsNullAndStatusNot(TicketStatus.CLOSED);
        long providersPending = providers.countByVerificationStatus(VerificationStatus.PENDING_VERIFICATION);
        long offersPending = offers.countByStatus(Offer.Status.PENDING_APPROVAL);
        long communityCount = tenants.count();
        return new DashboardView(Role.SUPER_ADMIN.name(), Map.of(), null, null, null, null,
                null, null, null,
                null, null, null,
                totalOpen, total, communityLessOpen, providersPending, offersPending, communityCount);
    }

    private static Map<String, Long> bucket(List<Object[]> rows) {
        Map<String, Long> out = new HashMap<>();
        for (Object[] row : rows) {
            TicketStatus status = (TicketStatus) row[0];
            Long count = (Long) row[1];
            out.put(status.name(), count);
        }
        return out;
    }
}
