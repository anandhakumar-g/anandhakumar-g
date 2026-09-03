package com.singlepoint.tenant.api;

import com.singlepoint.common.error.AppException;
import com.singlepoint.common.error.ErrorCode;
import com.singlepoint.common.util.PhoneNumbers;
import com.singlepoint.crypto.CryptoService;
import com.singlepoint.flat.InviteCodeService;
import com.singlepoint.flat.domain.InviteCode;
import com.singlepoint.security.AppPrincipal;
import com.singlepoint.security.TenantScopedExecutor;
import com.singlepoint.tenant.AdminTenantRepository;
import com.singlepoint.tenant.TenantService;
import com.singlepoint.tenant.domain.AdminTenant;
import com.singlepoint.tenant.domain.Tenant;
import com.singlepoint.ticket.TicketRepository;
import com.singlepoint.ticket.domain.TicketStatus;
import com.singlepoint.user.AppUserRepository;
import com.singlepoint.user.domain.AppUser;
import com.singlepoint.user.domain.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/superadmin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Super Admin", description = "Platform-level tenant onboarding (no ticket content access)")
public class SuperAdminController {

    private final TenantService tenantService;
    private final AppUserRepository userRepository;
    private final AdminTenantRepository adminTenantRepository;
    private final TicketRepository ticketRepository;
    private final CryptoService crypto;
    private final InviteCodeService inviteCodeService;
    private final TenantScopedExecutor tenantScoped;
    private final com.singlepoint.entitlement.EntitlementService entitlements;

    public SuperAdminController(TenantService tenantService, AppUserRepository userRepository,
                               AdminTenantRepository adminTenantRepository, TicketRepository ticketRepository,
                               CryptoService crypto, InviteCodeService inviteCodeService,
                               TenantScopedExecutor tenantScoped,
                               com.singlepoint.entitlement.EntitlementService entitlements) {
        this.tenantService = tenantService;
        this.userRepository = userRepository;
        this.adminTenantRepository = adminTenantRepository;
        this.ticketRepository = ticketRepository;
        this.crypto = crypto;
        this.inviteCodeService = inviteCodeService;
        this.tenantScoped = tenantScoped;
        this.entitlements = entitlements;
    }

    @PostMapping("/tenants")
    @Operation(summary = "Onboard a new community")
    public ResponseEntity<TenantDtos.TenantCard> createTenant(@Valid @RequestBody TenantDtos.CreateTenantRequest body) {
        Tenant t = tenantService.create(body.name(), body.city(), body.locality(), body.address(), body.pincode(),
                body.logoUrl(), body.defaultTheme(), body.brandPrimaryColor(), body.reopenWindowHours());
        return ResponseEntity.status(HttpStatus.CREATED).body(TenantDtos.TenantCard.from(t));
    }

    @PutMapping("/tenants/{tenantId}")
    @Operation(summary = "Update a community's settings (sparse — only the fields sent are changed)")
    public ResponseEntity<TenantDtos.TenantSettingsView> updateTenant(
            @PathVariable String tenantId, @Valid @RequestBody TenantDtos.UpdateTenantRequest body) {
        Tenant t = tenantService.update(java.util.UUID.fromString(tenantId),
                body.name(), body.city(), body.locality(), body.address(), body.pincode(),
                body.logoUrl(), body.defaultTheme(), body.brandPrimaryColor(),
                body.reopenWindowHours(), body.requireAllocationApproval(), body.categoryAdmin(),
                body.directServiceEnabled(), body.providerOnboardingAllowed());
        return ResponseEntity.ok(TenantDtos.TenantSettingsView.from(t));
    }

    // ---- admins ----------------------------------------------------------------

    @PostMapping("/tenants/{tenantId}/admins")
    @Operation(summary = "Provision (or attach an existing) admin account for a community")
    @Transactional
    public ResponseEntity<AdminCreated> createAdmin(@PathVariable String tenantId,
                                                    @AuthenticationPrincipal AppPrincipal principal,
                                                    @Valid @RequestBody TenantDtos.CreateAdminRequest body) {
        Tenant tenant = tenantService.require(UUID.fromString(tenantId));
        requireSeatAvailable(tenant.getId());
        String phone = PhoneNumbers.normalize(body.phone());
        String phoneHash = crypto.lookupHash(phone);
        AppUser admin = userRepository.findByPhoneHash(phoneHash).orElse(null);
        if (admin != null && admin.getRole() != Role.ADMIN) {
            throw new AppException(ErrorCode.CONFLICT, "A user with this phone number already exists");
        }
        if (admin == null) {
            admin = new AppUser();
            admin.setRole(Role.ADMIN);
            admin.setName(body.name());
            admin.setPhone(phone);
            admin.setPhoneHash(phoneHash);
            admin.setCurrentTenantId(tenant.getId());
            admin.setProfileCompleted(true);
            userRepository.save(admin);
        } else if (admin.getCurrentTenantId() == null) {
            admin.setCurrentTenantId(tenant.getId());
            userRepository.save(admin);
        }
        attach(admin.getId(), tenant.getId(), principal.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AdminCreated(admin.getId().toString(), tenant.getId().toString(),
                        PhoneNumbers.mask(phone)));
    }

    @PostMapping("/tenants/{tenantId}/admins/{adminUserId}")
    @Operation(summary = "Attach an existing admin to another community")
    @Transactional
    public ResponseEntity<Void> attachAdmin(@PathVariable UUID tenantId, @PathVariable UUID adminUserId,
                                            @AuthenticationPrincipal AppPrincipal principal) {
        Tenant tenant = tenantService.require(tenantId);
        AppUser admin = userRepository.findById(adminUserId).orElseThrow(() -> AppException.notFound("User"));
        if (admin.getRole() != Role.ADMIN) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "That user is not an admin");
        }
        requireSeatAvailable(tenant.getId());
        attach(adminUserId, tenant.getId(), principal.getUserId());
        if (admin.getCurrentTenantId() == null) {
            admin.setCurrentTenantId(tenant.getId());
            userRepository.save(admin);
        }
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/tenants/{tenantId}/admins/{adminUserId}")
    @Operation(summary = "Detach an admin from a community")
    @Transactional
    public ResponseEntity<Void> detachAdmin(@PathVariable UUID tenantId, @PathVariable UUID adminUserId) {
        AdminTenant link = adminTenantRepository
                .findByAdminUserIdAndTenantIdAndActiveTrue(adminUserId, tenantId)
                .orElseThrow(() -> AppException.notFound("Admin assignment"));
        link.setActive(false);
        adminTenantRepository.save(link);
        AppUser admin = userRepository.findById(adminUserId).orElse(null);
        if (admin != null && tenantId.equals(admin.getCurrentTenantId())) {
            UUID next = adminTenantRepository.findByAdminUserIdAndActiveTrueOrderByCreatedAtAsc(adminUserId)
                    .stream().map(AdminTenant::getTenantId).findFirst().orElse(null);
            admin.setCurrentTenantId(next);
            userRepository.save(admin);
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/tenants/{tenantId}/admins")
    @Operation(summary = "Admins assigned to a community")
    @Transactional(readOnly = true)
    public ResponseEntity<List<AdminAssignment>> admins(@PathVariable UUID tenantId) {
        List<AdminTenant> links = adminTenantRepository.findByTenantIdAndActiveTrue(tenantId);
        Map<UUID, AppUser> us = userRepository.findAllById(
                        links.stream().map(AdminTenant::getAdminUserId).toList())
                .stream().collect(Collectors.toMap(AppUser::getId, x -> x, (a, b) -> a));
        List<AdminAssignment> out = links.stream().map(l -> {
            AppUser u = us.get(l.getAdminUserId());
            return new AdminAssignment(l.getAdminUserId(), u != null ? u.getName() : null,
                    u != null ? PhoneNumbers.mask(u.getPhone()) : null, l.isActive());
        }).toList();
        return ResponseEntity.ok(out);
    }

    private void requireSeatAvailable(UUID tenantId) {
        entitlements.requireWithinQuota(com.singlepoint.billing.domain.SubjectType.TENANT, tenantId,
                "ADMIN_SEATS", adminTenantRepository.countByTenantIdAndActiveTrue(tenantId));
    }

    private void attach(UUID adminUserId, UUID tenantId, UUID addedBy) {
        AdminTenant link = adminTenantRepository.findByAdminUserIdAndTenantId(adminUserId, tenantId)
                .orElseGet(AdminTenant::new);
        link.setAdminUserId(adminUserId);
        link.setTenantId(tenantId);
        link.setAddedByUserId(addedBy);
        link.setActive(true);
        adminTenantRepository.save(link);
    }

    // ---- invite codes --------------------------------------------------------

    @PostMapping("/tenants/{tenantId}/invite-codes")
    @Operation(summary = "Issue an invite code for a community (Super Admin acting for an admin-less community)")
    public ResponseEntity<TenantDtos.InviteCodeView> createInvite(
            @PathVariable UUID tenantId, @AuthenticationPrincipal AppPrincipal principal,
            @RequestBody(required = false) TenantDtos.SuperInviteRequest body) {
        tenantService.require(tenantId);
        TenantDtos.SuperInviteRequest b = body != null ? body
                : new TenantDtos.SuperInviteRequest(null, null, null);
        InviteCode c = tenantScoped.inTenant(tenantId, () -> inviteCodeService.create(tenantId, principal.getUserId(),
                b.flatId() != null ? UUID.fromString(b.flatId()) : null, null,
                b.validDays(), b.maxUses(), InviteCode.Kind.ADMIN));
        return ResponseEntity.status(HttpStatus.CREATED).body(TenantDtos.InviteCodeView.of(c));
    }

    @GetMapping("/tenants/{tenantId}/invite-codes")
    @Operation(summary = "Invite codes for a community")
    public ResponseEntity<List<TenantDtos.InviteCodeView>> invites(@PathVariable UUID tenantId) {
        tenantService.require(tenantId);
        List<TenantDtos.InviteCodeView> out = tenantScoped.inTenant(tenantId,
                () -> inviteCodeService.listForTenant(tenantId).stream()
                        .map(TenantDtos.InviteCodeView::of).toList());
        return ResponseEntity.ok(out);
    }

    @DeleteMapping("/tenants/{tenantId}/invite-codes/{codeId}")
    @Operation(summary = "Revoke a community invite code")
    public ResponseEntity<Void> revokeInvite(@PathVariable UUID tenantId, @PathVariable UUID codeId) {
        tenantScoped.inTenant(tenantId, () -> inviteCodeService.revoke(tenantId, codeId));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/tenants")
    @Operation(summary = "Cross-tenant health counts (aggregates only, never ticket content)")
    @Transactional(readOnly = true)
    public ResponseEntity<List<TenantDtos.TenantHealth>> tenantHealth(
            @AuthenticationPrincipal AppPrincipal principal) {
        List<TenantDtos.TenantHealth> out = tenantService.search(null,
                        org.springframework.data.domain.PageRequest.of(0, 50)).getContent().stream()
                .map(t -> new TenantDtos.TenantHealth(t.getId(), t.getName(), t.getCity(),
                        ticketRepository.countByTenantIdAndStatusNot(t.getId(), TicketStatus.CLOSED),
                        ticketRepository.countByTenantId(t.getId())))
                .toList();
        return ResponseEntity.ok(out);
    }

    public record AdminCreated(String userId, String tenantId, String phoneMasked) { }

    public record AdminAssignment(UUID userId, String name, String phoneMasked, boolean active) { }
}
