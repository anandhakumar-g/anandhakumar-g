package com.singlepoint.user;

import com.singlepoint.billing.PaymentMethodRepository;
import com.singlepoint.common.error.AppException;
import com.singlepoint.common.error.ErrorCode;
import com.singlepoint.crypto.CryptoService;
import com.singlepoint.notification.DeviceTokenRepository;
import com.singlepoint.payment.TicketPaymentRepository;
import com.singlepoint.payment.domain.TicketPayment;
import com.singlepoint.security.TenantScopedExecutor;
import com.singlepoint.tenant.AdminTenantRepository;
import com.singlepoint.ticket.TicketRepository;
import com.singlepoint.ticket.domain.TicketStatus;
import com.singlepoint.user.domain.AppUser;
import com.singlepoint.user.domain.MembershipStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** MVP-13 (C2): close the caller's account — soft-delete, anonymize, EXIT every community. */
@Service
public class AccountDeletionService {

    private static final List<TicketPayment.Status> SETTLED = List.of(
            TicketPayment.Status.PAID_ONLINE, TicketPayment.Status.PAID_CASH,
            TicketPayment.Status.WAIVED, TicketPayment.Status.FAILED);

    private final AppUserRepository users;
    private final UserService userService;
    private final MembershipService membershipService;
    private final TicketRepository tickets;
    private final TicketPaymentRepository ticketPayments;
    private final AdminTenantRepository adminTenants;
    private final DeviceTokenRepository deviceTokens;
    private final PaymentMethodRepository paymentMethods;
    private final CryptoService crypto;
    private final TenantScopedExecutor tenantScoped;

    public AccountDeletionService(AppUserRepository users, UserService userService,
                                  MembershipService membershipService, TicketRepository tickets,
                                  TicketPaymentRepository ticketPayments, AdminTenantRepository adminTenants,
                                  DeviceTokenRepository deviceTokens, PaymentMethodRepository paymentMethods,
                                  CryptoService crypto, TenantScopedExecutor tenantScoped) {
        this.users = users;
        this.userService = userService;
        this.membershipService = membershipService;
        this.tickets = tickets;
        this.ticketPayments = ticketPayments;
        this.adminTenants = adminTenants;
        this.deviceTokens = deviceTokens;
        this.paymentMethods = paymentMethods;
        this.crypto = crypto;
        this.tenantScoped = tenantScoped;
    }

    @Transactional
    public void deleteMe(UUID userId) {
        AppUser u = users.findById(userId).orElseThrow();
        if (u.getDeletedAt() != null) return; // idempotent

        // 1. gates — anything unfinished blocks, across every community + community-less.
        tenantScoped.inWildcard(() -> {
            long open = tickets.findByRaisedByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 300))
                    .stream().filter(t -> t.getStatus() != TicketStatus.CLOSED).count();
            if (open > 0) {
                throw new AppException(ErrorCode.CONFLICT,
                        "You have " + open + " open request(s) — close them before deleting your account");
            }
            if (!ticketPayments.findUnsettledForRaiserAcrossTenants(userId, SETTLED).isEmpty()) {
                throw new AppException(ErrorCode.CONFLICT, "You have an unsettled bill — clear it first");
            }
            return null;
        });

        // 2. an admin who is the last one standing must hand the community over first.
        adminTenants.findByAdminUserIdAndActiveTrueOrderByCreatedAtAsc(userId).forEach(link -> {
            boolean sole = adminTenants.findByTenantIdAndActiveTrue(link.getTenantId())
                    .stream().allMatch(l -> l.getAdminUserId().equals(userId));
            if (sole) {
                throw new AppException(ErrorCode.CONFLICT,
                        "You are the only admin of a community — add another admin before deleting your account");
            }
        });

        // 3. EXIT every community (reuses the membership + flat-unlink path).
        Set<UUID> tenantIds = userService.memberships(userId).stream()
                .filter(m -> m.getStatus() == MembershipStatus.ACTIVE)
                .map(m -> m.getTenantId())
                .collect(Collectors.toSet());
        for (UUID tid : tenantIds) {
            tenantScoped.inTenant(tid, () -> membershipService.removeFromCommunity(tid, userId, userId));
        }

        // 4. scrub + tombstone.
        String rand = "deleted-" + UUID.randomUUID();
        u.setName("Deleted user");
        u.setEmail(null);
        u.setEmailHash(null);
        u.setPhone(rand);
        u.setPhoneHash(crypto.lookupHash(rand));
        u.setCurrentTenantId(null);
        u.setAwayUntil(null);
        u.setDeletedAt(Instant.now());
        users.save(u);

        deviceTokens.deleteByUserId(userId);
        paymentMethods.deleteByUserId(userId);
    }
}
