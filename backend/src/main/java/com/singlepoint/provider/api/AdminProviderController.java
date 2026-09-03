package com.singlepoint.provider.api;

import com.singlepoint.common.error.AppException;
import com.singlepoint.common.error.ErrorCode;
import com.singlepoint.common.util.PhoneNumbers;
import com.singlepoint.provider.ProviderService;
import com.singlepoint.provider.domain.ServiceProvider;
import com.singlepoint.security.AppPrincipal;
import com.singlepoint.tenant.TenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * MVP-7: a community admin manages which verified providers are enrolled in their community.
 * Creating and verifying providers is Super-Admin territory (see {@code SuperAdminProviderController}).
 */
@RestController
@RequestMapping("/api/v1/admin/providers")
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
@Tag(name = "Admin — Providers", description = "Enrol verified providers into this community")
public class AdminProviderController {

    private final ProviderService providerService;
    private final TenantService tenantService;

    public AdminProviderController(ProviderService providerService, TenantService tenantService) {
        this.providerService = providerService;
        this.tenantService = tenantService;
    }

    private UUID tenant(AppPrincipal p) {
        if (p.getTenantId() == null) throw new AppException(ErrorCode.FORBIDDEN, "No active community");
        return p.getTenantId();
    }

    private void requireOnboardingAllowed(UUID tenantId) {
        if (!tenantService.require(tenantId).isProviderOnboardingAllowed()) {
            throw new AppException(ErrorCode.FORBIDDEN,
                    "The platform has not enabled provider enrolment for this community");
        }
    }

    public record ProviderView(UUID id, String name, String vendorCategoryId, boolean company,
                               String contactPhoneMasked, String verificationStatus, String tier,
                               boolean active, boolean assignable, String availability, String availabilityNote,
                               java.math.BigDecimal ratingAvg, int ratingCount) {
        static ProviderView of(ServiceProvider p) {
            return new ProviderView(p.getId(), p.getName(), p.getVendorCategoryId().toString(), p.isCompany(),
                    PhoneNumbers.mask(p.getContactPhone()), p.getVerificationStatus().name(),
                    p.getTier().name(), p.isActive(), p.isAssignable(),
                    p.getAvailability().name(), p.getAvailabilityNote(),
                    p.getRatingAvg(), p.getRatingCount());
        }
    }

    @GetMapping
    @Operation(summary = "This community's enrolled providers. ?includeInactive=true also lists deactivated enrolments")
    public ResponseEntity<List<ProviderView>> directory(@AuthenticationPrincipal AppPrincipal p,
                                                        @RequestParam(required = false) String sort,
                                                        @RequestParam(defaultValue = "false") boolean includeInactive) {
        var providers = includeInactive
                ? providerService.allForTenant(tenant(p))
                : providerService.directoryForTenant(tenant(p), sort);
        return ResponseEntity.ok(providers.stream().map(ProviderView::of).toList());
    }

    @GetMapping("/catalog")
    @Operation(summary = "The global verified-provider directory to enrol from (?vendorCategoryId= to filter)")
    public ResponseEntity<List<ProviderView>> catalog(@AuthenticationPrincipal AppPrincipal p,
                                                      @RequestParam(required = false) String vendorCategoryId) {
        requireOnboardingAllowed(tenant(p));
        UUID vcat = vendorCategoryId != null ? UUID.fromString(vendorCategoryId) : null;
        return ResponseEntity.ok(providerService.verifiedGlobal(vcat).stream().map(ProviderView::of).toList());
    }

    @PostMapping("/{providerId}/enrol")
    @Operation(summary = "Enrol a verified provider into this community")
    public ResponseEntity<ProviderView> enrol(@AuthenticationPrincipal AppPrincipal p,
                                              @PathVariable UUID providerId) {
        requireOnboardingAllowed(tenant(p));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ProviderView.of(providerService.enrol(tenant(p), providerId)));
    }

    @PostMapping("/{providerId}/deactivate")
    @Operation(summary = "Remove a provider from this community's directory (keeps them elsewhere)")
    public ResponseEntity<ProviderView> deactivate(@AuthenticationPrincipal AppPrincipal p,
                                                   @PathVariable UUID providerId) {
        return ResponseEntity.ok(ProviderView.of(
                providerService.setEnrolmentActive(tenant(p), providerId, false)));
    }

    @PostMapping("/{providerId}/reactivate")
    public ResponseEntity<ProviderView> reactivate(@AuthenticationPrincipal AppPrincipal p,
                                                   @PathVariable UUID providerId) {
        return ResponseEntity.ok(ProviderView.of(
                providerService.setEnrolmentActive(tenant(p), providerId, true)));
    }
}
