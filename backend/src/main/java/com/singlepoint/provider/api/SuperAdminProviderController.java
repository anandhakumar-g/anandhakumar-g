package com.singlepoint.provider.api;

import com.singlepoint.common.util.PhoneNumbers;
import com.singlepoint.provider.ProviderService;
import com.singlepoint.provider.domain.ProviderTier;
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

/**
 * MVP-7: the Super Admin owns provider onboarding — create the global record, review KYC
 * (see {@code SuperAdminKycController}), verify, and set the directory tier.
 */
@RestController
@RequestMapping("/api/v1/superadmin/providers")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Super Admin — Providers", description = "Global provider onboarding, verification and tier")
public class SuperAdminProviderController {

    private final ProviderService providerService;

    public SuperAdminProviderController(ProviderService providerService) {
        this.providerService = providerService;
    }

    public record CreateProviderRequest(@NotBlank String name, @NotBlank String vendorCategoryId,
                                        boolean company, @NotBlank String contactPhone,
                                        String contactEmail, String serviceArea) { }
    public record UpdateProviderRequest(String name, String vendorCategoryId, String contactPhone,
                                        String contactEmail, String serviceArea) { }
    public record VerifyRequest(@NotBlank String status) { }
    public record TierRequest(@NotBlank String tier) { }

    public record ProviderView(UUID id, String name, String vendorCategoryId, boolean company,
                               String contactPhoneMasked, String verificationStatus, String tier,
                               boolean active, boolean assignable,
                               java.math.BigDecimal ratingAvg, int ratingCount) {
        static ProviderView of(ServiceProvider p) {
            return new ProviderView(p.getId(), p.getName(), p.getVendorCategoryId().toString(), p.isCompany(),
                    PhoneNumbers.mask(p.getContactPhone()), p.getVerificationStatus().name(),
                    p.getTier().name(), p.isActive(), p.isAssignable(),
                    p.getRatingAvg(), p.getRatingCount());
        }
    }

    @GetMapping
    @Operation(summary = "Every provider on the platform")
    public ResponseEntity<List<ProviderView>> list() {
        return ResponseEntity.ok(providerService.allGlobal().stream().map(ProviderView::of).toList());
    }

    @PostMapping
    @Operation(summary = "Create a global provider record (PENDING_VERIFICATION)")
    public ResponseEntity<ProviderView> create(@Valid @RequestBody CreateProviderRequest body) {
        ServiceProvider sp = providerService.createGlobal(body.name(), UUID.fromString(body.vendorCategoryId()),
                body.company(), body.contactPhone(), body.contactEmail(), body.serviceArea());
        return ResponseEntity.status(HttpStatus.CREATED).body(ProviderView.of(sp));
    }

    @PutMapping("/{providerId}")
    @Operation(summary = "Edit a global provider record")
    public ResponseEntity<ProviderView> update(@PathVariable UUID providerId,
                                               @RequestBody UpdateProviderRequest body) {
        return ResponseEntity.ok(ProviderView.of(providerService.updateGlobal(providerId,
                body.name(), body.contactPhone(), body.contactEmail(), body.serviceArea(),
                body.vendorCategoryId() != null ? UUID.fromString(body.vendorCategoryId()) : null)));
    }

    @PostMapping("/{providerId}/verify")
    @Operation(summary = "Set verification status (VERIFIED requires the KYC document gate)")
    public ResponseEntity<ProviderView> verify(@AuthenticationPrincipal AppPrincipal p,
                                               @PathVariable UUID providerId, @Valid @RequestBody VerifyRequest body) {
        VerificationStatus status = VerificationStatus.valueOf(body.status().toUpperCase());
        return ResponseEntity.ok(ProviderView.of(
                providerService.setVerification(providerId, status, p.getUserId())));
    }

    @PostMapping("/{providerId}/tier")
    @Operation(summary = "Set a provider's directory tier (STANDARD / FEATURED)")
    public ResponseEntity<Void> setTier(@PathVariable UUID providerId, @Valid @RequestBody TierRequest body) {
        providerService.setTier(providerId, ProviderTier.valueOf(body.tier().toUpperCase()));
        return ResponseEntity.noContent().build();
    }
}
