package com.singlepoint.provider.api;

import com.singlepoint.common.error.AppException;
import com.singlepoint.common.error.ErrorCode;
import com.singlepoint.common.util.PhoneNumbers;
import com.singlepoint.provider.ProviderService;
import com.singlepoint.provider.domain.ServiceProvider;
import com.singlepoint.provider.domain.VerificationStatus;
import com.singlepoint.security.AppPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/providers")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin — Providers", description = "Community provider directory + manual verification")
public class AdminProviderController {

    private final ProviderService providerService;

    public AdminProviderController(ProviderService providerService) {
        this.providerService = providerService;
    }

    private UUID tenant(AppPrincipal p) {
        if (p.getTenantId() == null) throw new AppException(ErrorCode.FORBIDDEN, "No active community");
        return p.getTenantId();
    }

    public record CreateProviderRequest(@NotBlank String name, @NotBlank String vendorCategoryId,
                                        boolean company, @NotBlank String contactPhone,
                                        String contactEmail, String serviceArea) { }
    public record VerifyRequest(@NotBlank String status) { }
    public record UpdateProviderRequest(String name, String vendorCategoryId, String contactPhone,
                                        String contactEmail, String serviceArea) { }
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
    @Operation(summary = "This community's providers. ?includeInactive=true also lists deactivated enrolments")
    public ResponseEntity<List<ProviderView>> directory(@AuthenticationPrincipal AppPrincipal p,
                                                        @RequestParam(required = false) String sort,
                                                        @RequestParam(defaultValue = "false") boolean includeInactive) {
        var providers = includeInactive
                ? providerService.allForTenant(tenant(p))
                : providerService.directoryForTenant(tenant(p), sort);
        return ResponseEntity.ok(providers.stream().map(ProviderView::of).toList());
    }

    @PostMapping
    public ResponseEntity<ProviderView> create(@AuthenticationPrincipal AppPrincipal p,
                                               @Valid @RequestBody CreateProviderRequest body) {
        ServiceProvider sp = providerService.createForTenant(tenant(p), body.name(),
                UUID.fromString(body.vendorCategoryId()), body.company(), body.contactPhone(),
                body.contactEmail(), body.serviceArea());
        return ResponseEntity.status(HttpStatus.CREATED).body(ProviderView.of(sp));
    }

    @PutMapping("/{providerId}")
    @Operation(summary = "Edit a provider enrolled in this community")
    public ResponseEntity<ProviderView> update(@AuthenticationPrincipal AppPrincipal p,
                                               @PathVariable UUID providerId,
                                               @RequestBody UpdateProviderRequest body) {
        return ResponseEntity.ok(ProviderView.of(providerService.updateForTenant(
                tenant(p), providerId, body.name(), body.contactPhone(), body.contactEmail(),
                body.serviceArea(),
                body.vendorCategoryId() != null ? UUID.fromString(body.vendorCategoryId()) : null)));
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

    @PostMapping("/{providerId}/verify")
    @Operation(summary = "Set verification status (VERIFIED / REJECTED / SUSPENDED / PENDING_VERIFICATION)")
    public ResponseEntity<ProviderView> verify(@AuthenticationPrincipal AppPrincipal p,
                                               @PathVariable UUID providerId,
                                               @Valid @RequestBody VerifyRequest body) {
        // ensure the provider is enrolled in this admin's tenant before touching global verification
        if (!providerService.isEnrolledAndActive(tenant(p), providerId)) {
            throw AppException.notFound("Service provider");
        }
        VerificationStatus status = VerificationStatus.valueOf(body.status().toUpperCase());
        return ResponseEntity.ok(ProviderView.of(
                providerService.setVerification(providerId, status, p.getUserId())));
    }
}
