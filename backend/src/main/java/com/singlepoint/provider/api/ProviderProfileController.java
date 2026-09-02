package com.singlepoint.provider.api;

import com.singlepoint.common.util.PhoneNumbers;
import com.singlepoint.provider.ProviderService;
import com.singlepoint.provider.domain.ServiceProvider;
import com.singlepoint.security.AppPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** MVP-5 (C): a provider manages its own contact details, service area and availability. */
@RestController
@RequestMapping("/api/v1/provider/profile")
@PreAuthorize("hasRole('PROVIDER')")
@Tag(name = "Provider — Profile", description = "Provider self-service (name/phone stay admin-only)")
public class ProviderProfileController {

    private final ProviderService providerService;

    public ProviderProfileController(ProviderService providerService) {
        this.providerService = providerService;
    }

    public record ProfileView(UUID id, String name, String contactPhoneMasked, String contactEmail,
                              String serviceArea, String availability, String availabilityNote,
                              String verificationStatus, String tier) {
        static ProfileView of(ServiceProvider p) {
            return new ProfileView(p.getId(), p.getName(), PhoneNumbers.mask(p.getContactPhone()),
                    p.getContactEmail(), p.getServiceArea(), p.getAvailability().name(),
                    p.getAvailabilityNote(), p.getVerificationStatus().name(), p.getTier().name());
        }
    }

    public record ProfileUpdateRequest(String contactEmail, String serviceArea,
                                       String availability, String availabilityNote) { }

    @GetMapping
    public ResponseEntity<ProfileView> get(@AuthenticationPrincipal AppPrincipal p) {
        return ResponseEntity.ok(ProfileView.of(providerService.requireOwn(p.getUserId())));
    }

    @PutMapping
    @Operation(summary = "Update contact email, service area, availability")
    public ResponseEntity<ProfileView> update(@AuthenticationPrincipal AppPrincipal p,
                                              @RequestBody ProfileUpdateRequest body) {
        return ResponseEntity.ok(ProfileView.of(providerService.updateOwnProfile(
                p.getUserId(), body.contactEmail(), body.serviceArea(),
                body.availability(), body.availabilityNote())));
    }
}
