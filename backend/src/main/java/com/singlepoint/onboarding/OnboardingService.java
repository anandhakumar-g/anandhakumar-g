package com.singlepoint.onboarding;

import com.singlepoint.common.error.AppException;
import com.singlepoint.common.error.ErrorCode;
import com.singlepoint.common.util.PhoneNumbers;
import com.singlepoint.location.LocationService;
import com.singlepoint.notification.DomainEventPublisher;
import com.singlepoint.onboarding.api.OnboardingDtos.CommunityRequestView;
import com.singlepoint.onboarding.api.OnboardingDtos.CreateCommunityRequest;
import com.singlepoint.security.AppPrincipal;
import com.singlepoint.security.TenantScopedExecutor;
import com.singlepoint.tenant.AdminTenantRepository;
import com.singlepoint.tenant.TenantRepository;
import com.singlepoint.tenant.TenantService;
import com.singlepoint.tenant.domain.AdminTenant;
import com.singlepoint.tenant.domain.Tenant;
import com.singlepoint.tenant.domain.TenantStatus;
import com.singlepoint.user.AppUserRepository;
import com.singlepoint.user.domain.AppUser;
import com.singlepoint.user.domain.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** MVP-11 (A): a resident self-onboards a community; a Super Admin approves or rejects it. */
@Service
public class OnboardingService {

    private final TenantService tenantService;
    private final TenantRepository tenants;
    private final AppUserRepository users;
    private final AdminTenantRepository adminTenants;
    private final LocationService locationService;
    private final TenantScopedExecutor tenantScoped;
    private final DomainEventPublisher events;
    private final com.singlepoint.observability.AppMetrics metrics;

    public OnboardingService(TenantService tenantService, TenantRepository tenants, AppUserRepository users,
                             AdminTenantRepository adminTenants, LocationService locationService,
                             TenantScopedExecutor tenantScoped, DomainEventPublisher events,
                             com.singlepoint.observability.AppMetrics metrics) {
        this.tenantService = tenantService;
        this.tenants = tenants;
        this.users = users;
        this.adminTenants = adminTenants;
        this.locationService = locationService;
        this.tenantScoped = tenantScoped;
        this.events = events;
        this.metrics = metrics;
    }

    @Transactional
    public Tenant submit(AppPrincipal principal, CreateCommunityRequest req) {
        if (principal.getRole() == Role.ADMIN || principal.getRole() == Role.SUPER_ADMIN) {
            throw new AppException(ErrorCode.CONFLICT, "You already manage a community");
        }
        if (tenants.existsByRequestedByUserIdAndStatus(principal.getUserId(), TenantStatus.PENDING_REVIEW)) {
            throw new AppException(ErrorCode.CONFLICT, "You already have a community request under review");
        }
        return tenantService.createPending(req.name().trim(), trimToNull(req.city()), trimToNull(req.locality()),
                trimToNull(req.address()), trimToNull(req.pincode()), principal.getUserId());
    }

    @Transactional(readOnly = true)
    public Optional<Tenant> myLatestRequest(UUID userId) {
        return tenants.findFirstByRequestedByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public Page<CommunityRequestView> queue(TenantStatus status, Pageable pageable) {
        Page<Tenant> page = tenants.findByStatusOrderByCreatedAtDesc(status, pageable);
        Map<UUID, AppUser> byId = users.findAllById(page.getContent().stream()
                        .map(Tenant::getRequestedByUserId).filter(java.util.Objects::nonNull).toList())
                .stream().collect(Collectors.toMap(AppUser::getId, Function.identity()));
        return page.map(t -> {
            AppUser u = t.getRequestedByUserId() == null ? null : byId.get(t.getRequestedByUserId());
            return new CommunityRequestView(t.getId(), t.getName(), t.getCity(), t.getLocality(),
                    t.getStatus().name(), t.getCreatedAt(), t.getRequestedByUserId(),
                    u != null ? u.getName() : null, u != null ? PhoneNumbers.mask(u.getPhone()) : null);
        });
    }

    @Transactional
    public Tenant approve(UUID tenantId) {
        Tenant t = requirePending(tenantId);
        tenantService.setStatus(tenantId, TenantStatus.ACTIVE);

        UUID requesterId = t.getRequestedByUserId();
        if (requesterId != null) {
            AppUser u = users.findById(requesterId).orElseThrow(() -> AppException.notFound("Requester"));
            u.setRole(Role.ADMIN);
            u.setCurrentTenantId(tenantId);
            users.save(u);

            AdminTenant link = adminTenants.findByAdminUserIdAndTenantId(requesterId, tenantId)
                    .orElseGet(AdminTenant::new);
            link.setAdminUserId(requesterId);
            link.setTenantId(tenantId);
            link.setActive(true);
            adminTenants.save(link);
        }

        tenantScoped.inTenant(tenantId, () -> locationService.create(tenantId, "Main", null, null, null, null));

        if (requesterId != null) {
            events.publish("COMMUNITY_APPROVED", "tenant", tenantId, tenantId, List.of(requesterId),
                    "Your community is live", "\"" + t.getName() + "\" is approved — you're now its admin.",
                    Map.of("tenantId", tenantId.toString()));
        }
        metrics.communitySelfOnboarded();
        return t;
    }

    @Transactional
    public Tenant reject(UUID tenantId, String reason) {
        Tenant t = requirePending(tenantId);
        tenantService.setStatus(tenantId, TenantStatus.ARCHIVED);
        if (t.getRequestedByUserId() != null) {
            String tail = reason == null || reason.isBlank() ? "" : " Reason: " + reason.trim();
            events.publish("COMMUNITY_REJECTED", "tenant", tenantId, null, List.of(t.getRequestedByUserId()),
                    "Community request declined", "\"" + t.getName() + "\" wasn't approved." + tail,
                    Map.of("tenantId", tenantId.toString()));
        }
        return t;
    }

    private Tenant requirePending(UUID tenantId) {
        Tenant t = tenantService.require(tenantId);
        if (t.getStatus() != TenantStatus.PENDING_REVIEW) {
            throw new AppException(ErrorCode.CONFLICT, "This community is not awaiting review");
        }
        return t;
    }

    private static String trimToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
