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
    public record ProviderView(UUID id, String name, String vendorCategoryId, boolean company,
                               String contactPhoneMasked, String verificationStatus, String tier,
                               boolean active, boolean assignable) {
        static ProviderView of(ServiceProvider p) {
            return new ProviderView(p.getId(), p.getName(), p.getVendorCategoryId().toString(), p.isCompany(),
                    PhoneNumbers.mask(p.getContactPhone()), p.getVerificationStatus().name(),
                    p.getTier().name(), p.isActive(), p.isAssignable());
        }
    }

    @GetMapping
    public ResponseEntity<List<ProviderView>> directory(@AuthenticationPrincipal AppPrincipal p) {
        return ResponseEntity.ok(providerService.directoryForTenant(tenant(p)).stream()
                .map(ProviderView::of).toList());
    }

    @PostMapping
    public ResponseEntity<ProviderView> create(@AuthenticationPrincipal AppPrincipal p,
                                               @Valid @RequestBody CreateProviderRequest body) {
        ServiceProvider sp = providerService.createForTenant(tenant(p), body.name(),
                UUID.fromString(body.vendorCategoryId()), body.company(), body.contactPhone(),
                body.contactEmail(), body.serviceArea());
        return ResponseEntity.status(HttpStatus.CREATED).body(ProviderView.of(sp));
    }

    @PostMapping("/{providerId}/verify")
    @Operation(summary = "Set verification status (VERIFIED / REJECTED / SUSPENDED / PENDING_VERIFICATION)")
    public ResponseEntity<ProviderView> verify(@AuthenticationPrincipal AppPrincipal p,
                                               @PathVariable UUID providerId,
                                               @Valid @RequestBody VerifyRequest body) {
        // ensure the provider is enrolled in this admin's tenant before touching global verification
        boolean enrolled = providerService.directoryForTenant(tenant(p)).stream()
                .anyMatch(sp -> sp.getId().equals(providerId));
        if (!enrolled) throw AppException.notFound("Service provider");
        VerificationStatus status = VerificationStatus.valueOf(body.status().toUpperCase());
        return ResponseEntity.ok(ProviderView.of(
                providerService.setVerification(providerId, status, p.getUserId())));
    }
}
