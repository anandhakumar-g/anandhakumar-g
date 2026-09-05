package com.singlepoint.broadcast;

import com.singlepoint.broadcast.api.BroadcastDtos;
import com.singlepoint.broadcast.domain.Broadcast;
import com.singlepoint.common.error.AppException;
import com.singlepoint.common.error.ErrorCode;
import com.singlepoint.notification.DomainEventPublisher;
import com.singlepoint.security.AppPrincipal;
import com.singlepoint.tenant.AdminTenantRepository;
import com.singlepoint.tenant.TenantRepository;
import com.singlepoint.user.AppUserRepository;
import com.singlepoint.user.UserTenantMembershipRepository;
import com.singlepoint.user.domain.MembershipStatus;
import com.singlepoint.user.domain.Role;
import com.singlepoint.user.domain.UserTenantMembership;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * MVP-9 (B): send one announcement. A community admin reaches every active resident of the
 * community they're acting in; the Super Admin reaches all admins, all users, or a named
 * community. One {@code notification_outbox} row is written — the existing OutboxDispatcher
 * fans it out (push + in-app), honouring {@code notification_preference.broadcast_enabled}.
 */
@Service
public class BroadcastService {

    private final BroadcastRepository broadcasts;
    private final UserTenantMembershipRepository memberships;
    private final AdminTenantRepository adminTenants;
    private final AppUserRepository users;
    private final TenantRepository tenants;
    private final DomainEventPublisher events;
    private final com.singlepoint.observability.AppMetrics metrics;

    private final int minIntervalSeconds;
    private final int dailyCap;

    public BroadcastService(BroadcastRepository broadcasts, UserTenantMembershipRepository memberships,
                            AdminTenantRepository adminTenants, AppUserRepository users, TenantRepository tenants,
                            DomainEventPublisher events, com.singlepoint.observability.AppMetrics metrics,
                            @Value("${sp.broadcast.min-interval-seconds:60}") int minIntervalSeconds,
                            @Value("${sp.broadcast.daily-cap:20}") int dailyCap) {
        this.broadcasts = broadcasts;
        this.memberships = memberships;
        this.adminTenants = adminTenants;
        this.users = users;
        this.tenants = tenants;
        this.events = events;
        this.metrics = metrics;
        this.minIntervalSeconds = minIntervalSeconds;
        this.dailyCap = dailyCap;
    }

    @Transactional
    public Broadcast send(AppPrincipal sender, Broadcast.Scope scope, UUID tenantId, String title, String body) {
        authorizeScope(sender, scope, tenantId);
        rateGuard(sender.getUserId());

        List<UUID> recipients = resolveRecipients(scope, tenantId).stream()
                .filter(id -> !id.equals(sender.getUserId()))
                .distinct()
                .toList();

        Broadcast b = new Broadcast();
        b.setScope(scope);
        b.setTenantId(scope == Broadcast.Scope.COMMUNITY ? tenantId : null);
        b.setSenderUserId(sender.getUserId());
        b.setSenderRole(sender.getRole().name());
        b.setTitle(title);
        b.setBody(body);
        b.setRecipientCount(recipients.size());
        b = broadcasts.save(b);

        events.publishBroadcast(b.getId(), b.getTenantId(), recipients, title, body,
                Map.of("scope", scope.name(), "broadcastId", b.getId().toString()));
        metrics.broadcastSent();
        return b;
    }

    @Transactional(readOnly = true)
    public BroadcastDtos.BroadcastView toView(Broadcast b) {
        String senderName = users.findById(b.getSenderUserId()).map(u -> u.getName()).orElse(null);
        String tenantName = b.getTenantId() == null ? null
                : tenants.findById(b.getTenantId()).map(t -> t.getName()).orElse(null);
        return BroadcastDtos.BroadcastView.of(b, senderName, tenantName);
    }

    private void authorizeScope(AppPrincipal sender, Broadcast.Scope scope, UUID tenantId) {
        boolean superAdmin = sender.getRole() == Role.SUPER_ADMIN;
        switch (scope) {
            case COMMUNITY -> {
                if (sender.getRole() != Role.ADMIN && !superAdmin) {
                    throw new AppException(ErrorCode.FORBIDDEN, "Only a community admin can announce to residents");
                }
                if (tenantId == null) {
                    throw new AppException(ErrorCode.VALIDATION_FAILED, "No community to announce to");
                }
                if (!superAdmin && !tenantId.equals(sender.getTenantId())) {
                    throw new AppException(ErrorCode.FORBIDDEN, "That is not your active community");
                }
                if (tenants.findById(tenantId).isEmpty()) {
                    throw AppException.notFound("Community");
                }
            }
            case ALL_ADMINS, ALL_USERS -> {
                if (!superAdmin) {
                    throw new AppException(ErrorCode.FORBIDDEN, "Only the Super Admin can send a platform-wide announcement");
                }
            }
        }
    }

    private void rateGuard(UUID senderUserId) {
        Instant now = Instant.now();
        if (broadcasts.countBySenderUserIdAndCreatedAtAfter(senderUserId, now.minusSeconds(minIntervalSeconds)) > 0) {
            throw new AppException(ErrorCode.RATE_LIMITED, "You just sent an announcement — give it a minute");
        }
        Instant dayStart = now.truncatedTo(ChronoUnit.DAYS);
        if (broadcasts.countBySenderUserIdAndCreatedAtAfter(senderUserId, dayStart) >= dailyCap) {
            throw new AppException(ErrorCode.RATE_LIMITED, "Daily announcement limit reached");
        }
    }

    private List<UUID> resolveRecipients(Broadcast.Scope scope, UUID tenantId) {
        return switch (scope) {
            case COMMUNITY -> {
                Set<UUID> ids = new LinkedHashSet<>();
                for (UserTenantMembership m : memberships.findByTenantIdAndStatus(tenantId, MembershipStatus.ACTIVE)) {
                    ids.add(m.getUserId());
                }
                yield List.copyOf(ids);
            }
            case ALL_ADMINS -> adminTenants.findActiveAdminUserIds();
            case ALL_USERS -> users.findAllIds();
        };
    }
}
