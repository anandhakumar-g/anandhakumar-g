package com.singlepoint.provider.api;

import com.singlepoint.category.VendorCategoryRepository;
import com.singlepoint.category.domain.VendorCategory;
import com.singlepoint.common.error.AppException;
import com.singlepoint.common.error.ErrorCode;
import com.singlepoint.provider.ProviderService;
import com.singlepoint.provider.domain.ServiceProvider;
import com.singlepoint.security.AppPrincipal;
import com.singlepoint.tenant.TenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * MVP-6 (B): the resident-facing provider directory for Direct-to-Provider booking. Only
 * available when the community has {@code direct_service_enabled}. Carries no contact details —
 * the resident's phone/email reveal still happens through the ticket once engaged.
 */
@RestController
@RequestMapping("/api/v1/providers")
@PreAuthorize("hasRole('RESIDENT')")
@Tag(name = "Providers", description = "Resident-facing directory for direct booking")
public class ProviderDirectoryController {

    private final ProviderService providerService;
    private final TenantService tenantService;
    private final VendorCategoryRepository vendorCategories;

    public ProviderDirectoryController(ProviderService providerService, TenantService tenantService,
                                       VendorCategoryRepository vendorCategories) {
        this.providerService = providerService;
        this.tenantService = tenantService;
        this.vendorCategories = vendorCategories;
    }

    public record PublicProviderView(UUID id, String name, UUID vendorCategoryId, String vendorCategoryLabel,
                                     BigDecimal ratingAvg, int ratingCount, String tier,
                                     String availability, String availabilityNote, boolean assignable) { }

    @GetMapping
    @Operation(summary = "Verified providers I can book directly (my community's directory, "
            + "or the global verified list when I'm not in a community)")
    public ResponseEntity<List<PublicProviderView>> list(@AuthenticationPrincipal AppPrincipal principal) {
        List<ServiceProvider> providers;
        if (principal.getTenantId() == null) {
            // MVP-8: a community-less individual books from every verified provider.
            providers = providerService.verifiedGlobal(null);
        } else {
            if (!tenantService.require(principal.getTenantId()).isDirectServiceEnabled()) {
                throw new AppException(ErrorCode.FORBIDDEN, "Direct booking isn't enabled for your community");
            }
            providers = providerService.directoryForTenant(principal.getTenantId(), "rating");
        }
        Map<UUID, String> labels = vendorCategories.findAllById(
                providers.stream().map(ServiceProvider::getVendorCategoryId).collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(VendorCategory::getId, VendorCategory::getName));
        return ResponseEntity.ok(providers.stream()
                .filter(ServiceProvider::isAssignable)
                .map(p -> new PublicProviderView(p.getId(), p.getName(), p.getVendorCategoryId(),
                        labels.get(p.getVendorCategoryId()), p.getRatingAvg(), p.getRatingCount(),
                        p.getTier().name(), p.getAvailability().name(), p.getAvailabilityNote(), true))
                .toList());
    }
}
