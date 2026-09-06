package com.singlepoint.user;

import com.singlepoint.common.util.PhoneNumbers;
import com.singlepoint.notification.DeviceTokenRepository;
import com.singlepoint.notification.NotificationRepository;
import com.singlepoint.offer.OfferFeedbackRepository;
import com.singlepoint.offer.OfferRedemptionRepository;
import com.singlepoint.payment.PaymentReceiptRepository;
import com.singlepoint.payment.TicketPaymentRepository;
import com.singlepoint.security.TenantScopedExecutor;
import com.singlepoint.ticket.TicketRepository;
import com.singlepoint.ticket.TicketStatusHistoryRepository;
import com.singlepoint.ticket.domain.Ticket;
import com.singlepoint.user.domain.AppUser;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** MVP-13 (C1): assembles a single JSON document of everything the platform holds about one user. */
@Service
public class AccountExportService {

    private final AppUserRepository users;
    private final UserService userService;
    private final TicketRepository tickets;
    private final TicketStatusHistoryRepository history;
    private final TicketPaymentRepository payments;
    private final PaymentReceiptRepository receipts;
    private final OfferRedemptionRepository redemptions;
    private final OfferFeedbackRepository feedback;
    private final NotificationRepository notifications;
    private final DeviceTokenRepository devices;
    private final TenantScopedExecutor tenantScoped;

    public AccountExportService(AppUserRepository users, UserService userService, TicketRepository tickets,
                               TicketStatusHistoryRepository history, TicketPaymentRepository payments,
                               PaymentReceiptRepository receipts, OfferRedemptionRepository redemptions,
                               OfferFeedbackRepository feedback, NotificationRepository notifications,
                               DeviceTokenRepository devices, TenantScopedExecutor tenantScoped) {
        this.users = users;
        this.userService = userService;
        this.tickets = tickets;
        this.history = history;
        this.payments = payments;
        this.receipts = receipts;
        this.redemptions = redemptions;
        this.feedback = feedback;
        this.notifications = notifications;
        this.devices = devices;
        this.tenantScoped = tenantScoped;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> export(UUID userId) {
        AppUser u = users.findById(userId).orElseThrow();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("exportedAt", java.time.Instant.now().toString());
        out.put("profile", Map.of(
                "userId", u.getId().toString(),
                "role", u.getRole().name(),
                "name", str(u.getName()),
                "email", str(u.getEmail()),
                "phoneMasked", PhoneNumbers.mask(u.getPhone()),
                "profileCompleted", u.isProfileCompleted(),
                "preferredTheme", str(u.getPreferredTheme()),
                "createdAt", String.valueOf(u.getCreatedAt())));

        out.put("memberships", userService.memberships(userId).stream().map(m -> orderedMap(
                "tenantId", m.getTenantId(),
                "status", m.getStatus().name(),
                "relation", m.getRelation().name(),
                "householdRole", m.getHouseholdRole().name(),
                "flatId", m.getFlatId(),
                "joinedAt", m.getJoinedAt())).toList());

        // ticket + payment + receipt tables are RLS-forced; read them across every community.
        out.put("ticketsRaised", tenantScoped.inWildcard(() ->
                tickets.findByRaisedByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 500))
                        .stream().map(this::ticketBlock).toList()));

        out.put("offerRedemptions", redemptions.findByUserIdOrderByRedeemedAtDesc(userId).stream().map(r -> orderedMap(
                "offerId", r.getOfferId(),
                "status", r.getStatus().name(),
                "verifiedBy", r.getVerifiedBy().name(),
                "redeemedAt", r.getRedeemedAt(),
                "confirmedAt", r.getConfirmedAt())).toList());

        out.put("offerFeedback", feedback.findByUserIdOrderByCreatedAtDesc(userId).stream().map(f -> orderedMap(
                "offerId", f.getOfferId(),
                "rating", f.getRating(),
                "comment", str(f.getComment()),
                "at", f.getCreatedAt())).toList());

        out.put("notifications", notifications
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 500)).stream().map(n -> orderedMap(
                        "template", n.getTemplate(),
                        "title", str(n.getTitle()),
                        "body", str(n.getBody()),
                        "channel", n.getChannel().name(),
                        "status", n.getStatus().name(),
                        "createdAt", n.getCreatedAt(),
                        "readAt", n.getReadAt())).toList());

        out.put("devices", devices.findByUserId(userId).stream().map(d -> orderedMap(
                "platform", d.getPlatform().name(),
                "provider", d.getProvider().name(),
                "lastSeenAt", d.getLastSeenAt())).toList());

        return out;
    }

    private Map<String, Object> ticketBlock(Ticket t) {
        Map<String, Object> m = orderedMap(
                "reference", t.getReferenceCode(),
                "status", String.valueOf(t.getStatus()),
                "categoryId", t.getCategoryId(),
                "tenantId", t.getTenantId(),
                "priority", String.valueOf(t.getPriority()),
                "description", str(t.getDescription()),
                "rating", t.getRating(),
                "createdAt", t.getCreatedAt(),
                "resolvedAt", t.getResolvedAt(),
                "closedAt", t.getClosedAt());
        m.put("statusHistory", history.findByTicketIdOrderByCreatedAtAsc(t.getId()).stream().map(h -> orderedMap(
                "from", String.valueOf(h.getFromStatus()),
                "to", String.valueOf(h.getToStatus()),
                "actorRole", str(h.getActorRole()),
                "remarks", str(h.getRemarks()),
                "at", h.getCreatedAt())).toList());
        m.put("payments", payments.findByTicketId(t.getId()).stream().map(p -> {
            Map<String, Object> pm = orderedMap(
                    "amount", p.getAmount(),
                    "currency", p.getCurrency(),
                    "mode", String.valueOf(p.getMode()),
                    "status", String.valueOf(p.getStatus()),
                    "chargedByRole", str(p.getChargedByRole()),
                    "createdAt", p.getCreatedAt(),
                    "paidAt", p.getPaidAt());
            receipts.findByTicketPaymentId(p.getId()).ifPresent(rc ->
                    pm.put("receipt", orderedMap("number", rc.getReceiptNumber(), "at", rc.getCreatedAt())));
            return pm;
        }).toList());
        return m;
    }

    private static String str(String s) {
        return s == null ? "" : s;
    }

    /** A null-tolerant ordered map (Map.of rejects nulls). */
    private static Map<String, Object> orderedMap(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            Object v = kv[i + 1];
            m.put((String) kv[i], v == null ? null : (v instanceof java.time.Instant ? v.toString()
                    : v instanceof UUID ? v.toString() : v));
        }
        return m;
    }
}
