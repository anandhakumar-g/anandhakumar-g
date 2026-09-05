package com.singlepoint.export;

import com.singlepoint.common.util.PhoneNumbers;
import com.singlepoint.offer.OfferRepository;
import com.singlepoint.offer.domain.Offer;
import com.singlepoint.payment.TicketPaymentRepository;
import com.singlepoint.payment.domain.TicketPayment;
import com.singlepoint.tenant.TenantRepository;
import com.singlepoint.tenant.domain.Tenant;
import com.singlepoint.ticket.TicketRepository;
import com.singlepoint.ticket.domain.Ticket;
import com.singlepoint.ticket.domain.TicketStatus;
import com.singlepoint.user.AppUserRepository;
import com.singlepoint.user.MembershipService;
import com.singlepoint.user.domain.AppUser;
import com.singlepoint.user.domain.UserTenantMembership;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** MVP-12 (A): streams the platform's operational data as CSV. Filters: from / to / status. */
@Service
public class ExportService {

    private final TicketRepository tickets;
    private final TicketPaymentRepository payments;
    private final OfferRepository offers;
    private final TenantRepository tenants;
    private final AppUserRepository users;
    private final MembershipService memberships;

    public ExportService(TicketRepository tickets, TicketPaymentRepository payments, OfferRepository offers,
                         TenantRepository tenants, AppUserRepository users, MembershipService memberships) {
        this.tickets = tickets;
        this.payments = payments;
        this.offers = offers;
        this.tenants = tenants;
        this.users = users;
        this.memberships = memberships;
    }

    @Transactional(readOnly = true)
    public void tickets(CsvWriter w, UUID tenantId, Instant from, Instant to, String status) throws IOException {
        w.row("reference", "status", "requestMode", "priority", "categoryId", "raisedByUserId",
                "assignedProviderId", "createdAt", "resolvedAt", "closedAt", "slaBreached",
                "rating", "reopenedCount", "description");
        List<Ticket> rows = tenantId != null
                ? tickets.findByTenantIdOrderByCreatedAtAsc(tenantId)
                : tickets.findAllByOrderByCreatedAtAsc();
        for (Ticket t : rows) {
            if (!inWindow(t.getCreatedAt(), from, to) || !statusEquals(t.getStatus(), status)) continue;
            w.row(t.getReferenceCode(), t.getStatus(), t.getRequestMode(), t.getPriority(), t.getCategoryId(),
                    t.getRaisedByUserId(), t.getAssignedProviderId(), t.getCreatedAt(), t.getResolvedAt(),
                    t.getClosedAt(), t.getSlaBreachedAt() != null, t.getRating(), t.getReopenedCount(),
                    t.getDescription());
        }
        w.flush();
    }

    @Transactional(readOnly = true)
    public void payments(CsvWriter w, UUID tenantId, Instant from, Instant to, String status) throws IOException {
        w.row("ticketReference", "amount", "currency", "mode", "status", "chargedByRole",
                "createdAt", "paidAt", "note");
        List<TicketPayment> rows = payments.findByTenantIdOrderByCreatedAtAsc(tenantId);
        Map<UUID, String> refByTicket = tickets.findAllById(
                        rows.stream().map(TicketPayment::getTicketId).toList()).stream()
                .collect(Collectors.toMap(Ticket::getId, Ticket::getReferenceCode, (a, b) -> a));
        for (TicketPayment p : rows) {
            if (!inWindow(p.getCreatedAt(), from, to) || !statusEquals(p.getStatus(), status)) continue;
            w.row(refByTicket.get(p.getTicketId()), p.getAmount(), p.getCurrency(), p.getMode(), p.getStatus(),
                    p.getChargedByRole(), p.getCreatedAt(), p.getPaidAt(), p.getNote());
        }
        w.flush();
    }

    @Transactional(readOnly = true)
    public void members(CsvWriter w, UUID tenantId) throws IOException {
        w.row("userId", "name", "phoneMasked", "flatId", "householdRole", "status", "joinedAt");
        List<UserTenantMembership> rows = memberships.activeMembers(tenantId);
        Map<UUID, AppUser> byId = users.findAllById(rows.stream().map(UserTenantMembership::getUserId).toList())
                .stream().collect(Collectors.toMap(AppUser::getId, Function.identity(), (a, b) -> a));
        for (UserTenantMembership m : rows) {
            AppUser u = byId.get(m.getUserId());
            w.row(m.getUserId(), u != null ? u.getName() : null,
                    u != null ? PhoneNumbers.mask(u.getPhone()) : null,
                    m.getFlatId(), m.getHouseholdRole(), m.getStatus(), m.getJoinedAt());
        }
        w.flush();
    }

    @Transactional(readOnly = true)
    public void offers(CsvWriter w, Instant from, Instant to, String status) throws IOException {
        w.row("title", "status", "discountType", "discountValue", "couponCode", "validFrom", "validTo",
                "vendorCategoryId", "tenantId", "createdByRole", "validatedAt", "createdAt");
        for (Offer o : offers.findAllByOrderByCreatedAtAsc()) {
            if (!inWindow(o.getCreatedAt(), from, to) || !statusEquals(o.getStatus(), status)) continue;
            w.row(o.getTitle(), o.getStatus(), o.getDiscountType(), o.getDiscountValue(), o.getCouponCode(),
                    o.getValidFrom(), o.getValidTo(), o.getVendorCategoryId(), o.getTenantId(),
                    o.getCreatedByRole(), o.getValidatedAt(), o.getCreatedAt());
        }
        w.flush();
    }

    @Transactional(readOnly = true)
    public void communities(CsvWriter w) throws IOException {
        w.row("id", "name", "city", "locality", "status", "openTickets", "totalTickets", "createdAt");
        for (Tenant t : tenants.findAll()) {
            w.row(t.getId(), t.getName(), t.getCity(), t.getLocality(), t.getStatus(),
                    tickets.countByTenantIdAndStatusNot(t.getId(), TicketStatus.CLOSED),
                    tickets.countByTenantId(t.getId()), t.getCreatedAt());
        }
        w.flush();
    }

    private static boolean inWindow(Instant at, Instant from, Instant to) {
        if (at == null) return from == null && to == null ? true : false;
        if (from != null && at.isBefore(from)) return false;
        if (to != null && !at.isBefore(to)) return false;
        return true;
    }

    private static boolean statusEquals(Enum<?> actual, String want) {
        return want == null || want.isBlank() || actual != null && actual.name().equalsIgnoreCase(want.trim());
    }
}
