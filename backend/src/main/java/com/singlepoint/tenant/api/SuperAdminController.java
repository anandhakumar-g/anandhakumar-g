package com.singlepoint.tenant.api;

import com.singlepoint.common.error.AppException;
import com.singlepoint.common.error.ErrorCode;
import com.singlepoint.common.util.PhoneNumbers;
import com.singlepoint.crypto.CryptoService;
import com.singlepoint.security.AppPrincipal;
import com.singlepoint.tenant.TenantService;
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

@RestController
@RequestMapping("/api/v1/superadmin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Super Admin", description = "Platform-level tenant onboarding (no ticket content access)")
public class SuperAdminController {

    private final TenantService tenantService;
    private final AppUserRepository userRepository;
    private final TicketRepository ticketRepository;
    private final CryptoService crypto;
    private final com.singlepoint.entitlement.EntitlementService entitlements;

    public SuperAdminController(TenantService tenantService, AppUserRepository userRepository,
                               TicketRepository ticketRepository, CryptoService crypto,
                               com.singlepoint.entitlement.EntitlementService entitlements) {
        this.tenantService = tenantService;
        this.userRepository = userRepository;
        this.ticketRepository = ticketRepository;
        this.crypto = crypto;
        this.entitlements = entitlements;
    }

    @PostMapping("/tenants")
    @Operation(summary = "Onboard a new community")
    public ResponseEntity<TenantDtos.TenantCard> createTenant(@Valid @RequestBody TenantDtos.CreateTenantRequest body) {
        Tenant t = tenantService.create(body.name(), body.city(), body.locality(), body.address(), body.pincode(),
                body.logoUrl(), body.defaultTheme(), body.brandPrimaryColor(), body.reopenWindowHours());
        return ResponseEntity.status(HttpStatus.CREATED).body(TenantDtos.TenantCard.from(t));
    }

    @PostMapping("/tenants/{tenantId}/admins")
    @Operation(summary = "Provision an admin account for a community")
    @Transactional
    public ResponseEntity<AdminCreated> createAdmin(@PathVariable String tenantId,
                                                    @Valid @RequestBody TenantDtos.CreateAdminRequest body) {
        Tenant tenant = tenantService.require(java.util.UUID.fromString(tenantId));
        entitlements.requireWithinQuota(com.singlepoint.billing.domain.SubjectType.TENANT, tenant.getId(),
                "ADMIN_SEATS", userRepository.countByRoleAndCurrentTenantId(Role.ADMIN, tenant.getId()));
        String phone = PhoneNumbers.normalize(body.phone());
        String phoneHash = crypto.lookupHash(phone);
        if (userRepository.findByPhoneHash(phoneHash).isPresent()) {
            throw new AppException(ErrorCode.CONFLICT, "A user with this phone number already exists");
        }
        AppUser admin = new AppUser();
        admin.setRole(Role.ADMIN);
        admin.setName(body.name());
        admin.setPhone(phone);
        admin.setPhoneHash(phoneHash);
        admin.setCurrentTenantId(tenant.getId());
        admin.setProfileCompleted(true);
        userRepository.save(admin);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AdminCreated(admin.getId().toString(), tenant.getId().toString(),
                        PhoneNumbers.mask(phone)));
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
}
