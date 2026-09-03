package com.singlepoint.user.api;

import com.singlepoint.auth.AuthService;
import com.singlepoint.auth.api.AuthDtos;
import com.singlepoint.common.error.AppException;
import com.singlepoint.common.error.ErrorCode;
import com.singlepoint.common.util.PhoneNumbers;
import com.singlepoint.notification.DeviceTokenRepository;
import com.singlepoint.notification.domain.DeviceToken;
import com.singlepoint.security.AppPrincipal;
import com.singlepoint.tenant.AdminTenantRepository;
import com.singlepoint.tenant.TenantRepository;
import com.singlepoint.tenant.domain.AdminTenant;
import com.singlepoint.tenant.domain.Tenant;
import com.singlepoint.tenant.domain.TenantStatus;
import com.singlepoint.user.MembershipService;
import com.singlepoint.user.UserService;
import com.singlepoint.user.domain.AppUser;
import com.singlepoint.user.domain.MembershipStatus;
import com.singlepoint.user.domain.Role;
import com.singlepoint.user.domain.UserTenantMembership;
import org.springframework.security.access.prepost.PreAuthorize;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "Me", description = "Signed-in user profile, theme, memberships, devices")
public class MeController {

    private final UserService userService;
    private final MembershipService membershipService;
    private final TenantRepository tenantRepository;
    private final AdminTenantRepository adminTenantRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final AuthService authService;

    public MeController(UserService userService, MembershipService membershipService,
                        TenantRepository tenantRepository, AdminTenantRepository adminTenantRepository,
                        DeviceTokenRepository deviceTokenRepository, AuthService authService) {
        this.userService = userService;
        this.membershipService = membershipService;
        this.tenantRepository = tenantRepository;
        this.adminTenantRepository = adminTenantRepository;
        this.deviceTokenRepository = deviceTokenRepository;
        this.authService = authService;
    }

    @GetMapping
    @Operation(summary = "Profile, active tenant branding and all memberships")
    @Transactional(readOnly = true)
    public ResponseEntity<MeDtos.MeResponse> me(@AuthenticationPrincipal AppPrincipal principal) {
        AppUser u = userService.require(principal.getUserId());
        List<UserTenantMembership> memberships = userService.memberships(u.getId());
        // An admin has no user_tenant_membership rows — its communities live in admin_tenant.
        List<AdminTenant> adminLinks = u.getRole() == Role.ADMIN
                ? adminTenantRepository.findByAdminUserIdAndActiveTrueOrderByCreatedAtAsc(u.getId())
                : List.of();

        List<UUID> tenantIds = new java.util.ArrayList<>(
                memberships.stream().map(UserTenantMembership::getTenantId).toList());
        adminLinks.forEach(l -> tenantIds.add(l.getTenantId()));
        Map<UUID, Tenant> tenants = tenantRepository.findAllById(tenantIds).stream()
                .collect(Collectors.toMap(Tenant::getId, t -> t, (a, b) -> a));

        // Single source of truth for the JWT tenant claim (see AuthService.resolveActiveTenant).
        UUID activeTenant = authService.resolveActiveTenant(u);
        MeDtos.TenantBranding branding = null;
        boolean directServiceEnabled = false;
        if (activeTenant != null) {
            Tenant t = tenants.computeIfAbsent(activeTenant, id -> tenantRepository.findById(id).orElse(null));
            if (t != null) {
                branding = new MeDtos.TenantBranding(t.getId(), t.getName(),
                        t.getBrandLogoUrl() != null ? t.getBrandLogoUrl() : t.getLogoUrl(),
                        t.getDefaultTheme(), t.getBrandPrimaryColor());
                directServiceEnabled = t.isDirectServiceEnabled();
            }
        }

        List<MeDtos.MembershipView> views = new java.util.ArrayList<>(memberships.stream().map(m -> {
            Tenant t = tenants.get(m.getTenantId());
            return new MeDtos.MembershipView(m.getTenantId(), t != null ? t.getName() : null,
                    m.getStatus().name(), m.getRelation().name(), m.getFlatId(), m.getRequestedFlatLabel(),
                    m.getHouseholdRole().name());
        }).toList());
        // Synthesise a membership row per administered community so the mobile switcher renders.
        for (AdminTenant l : adminLinks) {
            Tenant t = tenants.get(l.getTenantId());
            views.add(new MeDtos.MembershipView(l.getTenantId(), t != null ? t.getName() : null,
                    "ACTIVE", "ADMIN", null, null, "ADMIN"));
        }
        if (u.getRole() == Role.SUPER_ADMIN && activeTenant != null) {
            Tenant t = tenants.computeIfAbsent(activeTenant, id -> tenantRepository.findById(id).orElse(null));
            views.add(new MeDtos.MembershipView(activeTenant, t != null ? t.getName() : null,
                    "ACTIVE", "ADMIN", null, null, "ADMIN"));
        }

        return ResponseEntity.ok(new MeDtos.MeResponse(u.getId(), u.getRole().name(), u.getName(),
                PhoneNumbers.mask(u.getPhone()), u.getEmail(), u.isProfileCompleted(), u.getPreferredTheme(),
                activeTenant, branding, views, u.getAwayUntil(), directServiceEnabled));
    }

    @PostMapping("/active-community")
    @PreAuthorize("hasAnyRole('RESIDENT','ADMIN','SUPER_ADMIN')")
    @Operation(summary = "Switch the active community; returns a session scoped to it. "
            + "A Super Admin uses this to act as admin for an admin-less community.")
    @Transactional
    public ResponseEntity<AuthDtos.SessionResponse> switchCommunity(
            @AuthenticationPrincipal AppPrincipal principal, @Valid @RequestBody MeDtos.ActiveCommunityRequest body) {
        UUID tenantId = UUID.fromString(body.tenantId());
        boolean allowed = switch (principal.getRole()) {
            case RESIDENT -> userService.memberships(principal.getUserId()).stream()
                    .anyMatch(m -> m.getStatus() == MembershipStatus.ACTIVE && m.getTenantId().equals(tenantId));
            case ADMIN -> adminTenantRepository
                    .findByAdminUserIdAndTenantIdAndActiveTrue(principal.getUserId(), tenantId).isPresent();
            case SUPER_ADMIN -> tenantRepository.findById(tenantId)
                    .map(t -> t.getStatus() == TenantStatus.ACTIVE).orElse(false);
            default -> false;
        };
        if (!allowed) {
            throw new AppException(ErrorCode.FORBIDDEN, "You can't act for that community");
        }
        userService.setCurrentTenant(principal.getUserId(), tenantId);
        return ResponseEntity.ok(AuthDtos.SessionResponse.from(
                authService.refreshSessionFor(principal.getUserId())));
    }

    @PostMapping("/stop-acting")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Stop acting as admin; returns a platform-wide (cross-tenant) session")
    @Transactional
    public ResponseEntity<AuthDtos.SessionResponse> stopActing(@AuthenticationPrincipal AppPrincipal principal) {
        userService.setCurrentTenant(principal.getUserId(), null);
        return ResponseEntity.ok(AuthDtos.SessionResponse.from(
                authService.refreshSessionFor(principal.getUserId())));
    }

    @PostMapping("/memberships/{tenantId}/leave")
    @PreAuthorize("hasRole('RESIDENT')")
    @Operation(summary = "Leave a community; returns a session scoped to the next one (or onboarding)")
    @Transactional
    public ResponseEntity<AuthDtos.SessionResponse> leaveCommunity(
            @AuthenticationPrincipal AppPrincipal principal, @PathVariable UUID tenantId) {
        membershipService.leave(principal.getUserId(), tenantId);
        AppUser u = userService.require(principal.getUserId());
        if (tenantId.equals(u.getCurrentTenantId())) {
            UUID next = userService.activeMembershipsOrdered(u.getId()).stream()
                    .map(UserTenantMembership::getTenantId).findFirst().orElse(null);
            userService.setCurrentTenant(u.getId(), next);
        }
        return ResponseEntity.ok(AuthDtos.SessionResponse.from(
                authService.refreshSessionFor(principal.getUserId())));
    }

    @PutMapping("/away-until")
    @Operation(summary = "Mark yourself away until a date (or clear it with a null value)")
    public ResponseEntity<Void> setAwayUntil(@AuthenticationPrincipal AppPrincipal principal,
                                             @RequestBody MeDtos.AwayRequest body) {
        userService.setAwayUntil(principal.getUserId(), body.awayUntil());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/theme")
    @Operation(summary = "Set the user's personal theme preference (overrides tenant default)")
    public ResponseEntity<Void> setTheme(@AuthenticationPrincipal AppPrincipal principal,
                                         @Valid @RequestBody MeDtos.ThemeRequest body) {
        userService.setPreferredTheme(principal.getUserId(), body.theme());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/devices")
    @Operation(summary = "Register / refresh a push device token")
    @Transactional
    public ResponseEntity<Void> registerDevice(@AuthenticationPrincipal AppPrincipal principal,
                                               @Valid @RequestBody MeDtos.DeviceRequest body) {
        DeviceToken dt = deviceTokenRepository.findByToken(body.token()).orElseGet(DeviceToken::new);
        dt.setUserId(principal.getUserId());
        dt.setToken(body.token());
        dt.setPlatform(DeviceToken.Platform.valueOf(body.platform().toUpperCase()));
        dt.setProvider(body.provider() != null
                ? DeviceToken.Provider.valueOf(body.provider().toUpperCase()) : DeviceToken.Provider.EXPO);
        dt.setLastSeenAt(Instant.now());
        deviceTokenRepository.save(dt);
        return ResponseEntity.noContent().build();
    }
}
