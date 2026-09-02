package com.singlepoint.user.api;

import com.singlepoint.auth.AuthService;
import com.singlepoint.auth.api.AuthDtos;
import com.singlepoint.common.error.AppException;
import com.singlepoint.common.error.ErrorCode;
import com.singlepoint.common.util.PhoneNumbers;
import com.singlepoint.notification.DeviceTokenRepository;
import com.singlepoint.notification.domain.DeviceToken;
import com.singlepoint.security.AppPrincipal;
import com.singlepoint.tenant.TenantRepository;
import com.singlepoint.tenant.domain.Tenant;
import com.singlepoint.user.MembershipService;
import com.singlepoint.user.UserService;
import com.singlepoint.user.domain.AppUser;
import com.singlepoint.user.domain.MembershipStatus;
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
    private final DeviceTokenRepository deviceTokenRepository;
    private final AuthService authService;

    public MeController(UserService userService, MembershipService membershipService,
                        TenantRepository tenantRepository, DeviceTokenRepository deviceTokenRepository,
                        AuthService authService) {
        this.userService = userService;
        this.membershipService = membershipService;
        this.tenantRepository = tenantRepository;
        this.deviceTokenRepository = deviceTokenRepository;
        this.authService = authService;
    }

    @GetMapping
    @Operation(summary = "Profile, active tenant branding and all memberships")
    @Transactional(readOnly = true)
    public ResponseEntity<MeDtos.MeResponse> me(@AuthenticationPrincipal AppPrincipal principal) {
        AppUser u = userService.require(principal.getUserId());
        List<UserTenantMembership> memberships = userService.memberships(u.getId());
        Map<UUID, Tenant> tenants = tenantRepository.findAllById(
                        memberships.stream().map(UserTenantMembership::getTenantId).collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(Tenant::getId, t -> t));

        // Residents get their active tenant from an ACTIVE membership; admins/providers from
        // their assigned tenant. Matches AuthService's JWT tenant claim.
        UUID activeTenant = switch (u.getRole()) {
            case SUPER_ADMIN -> null;
            case ADMIN, PROVIDER -> u.getCurrentTenantId();
            case RESIDENT -> userService.resolveActiveTenant(u);
        };
        MeDtos.TenantBranding branding = null;
        if (activeTenant != null) {
            Tenant t = tenants.computeIfAbsent(activeTenant, id -> tenantRepository.findById(id).orElse(null));
            if (t != null) {
                branding = new MeDtos.TenantBranding(t.getId(), t.getName(),
                        t.getBrandLogoUrl() != null ? t.getBrandLogoUrl() : t.getLogoUrl(),
                        t.getDefaultTheme(), t.getBrandPrimaryColor());
            }
        }

        List<MeDtos.MembershipView> views = memberships.stream().map(m -> {
            Tenant t = tenants.get(m.getTenantId());
            return new MeDtos.MembershipView(m.getTenantId(), t != null ? t.getName() : null,
                    m.getStatus().name(), m.getRelation().name(), m.getFlatId(), m.getRequestedFlatLabel());
        }).collect(Collectors.toList());

        return ResponseEntity.ok(new MeDtos.MeResponse(u.getId(), u.getRole().name(), u.getName(),
                PhoneNumbers.mask(u.getPhone()), u.getEmail(), u.isProfileCompleted(), u.getPreferredTheme(),
                activeTenant, branding, views, u.getAwayUntil()));
    }

    @PostMapping("/active-community")
    @PreAuthorize("hasRole('RESIDENT')")
    @Operation(summary = "Switch the active community; returns a session scoped to it")
    @Transactional
    public ResponseEntity<AuthDtos.SessionResponse> switchCommunity(
            @AuthenticationPrincipal AppPrincipal principal, @Valid @RequestBody MeDtos.ActiveCommunityRequest body) {
        UUID tenantId = UUID.fromString(body.tenantId());
        boolean member = userService.memberships(principal.getUserId()).stream()
                .anyMatch(m -> m.getStatus() == MembershipStatus.ACTIVE && m.getTenantId().equals(tenantId));
        if (!member) {
            throw new AppException(ErrorCode.FORBIDDEN, "You don't have an active membership in that community");
        }
        userService.setCurrentTenant(principal.getUserId(), tenantId);
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
