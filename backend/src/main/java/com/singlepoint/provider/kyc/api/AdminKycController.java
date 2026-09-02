package com.singlepoint.provider.kyc.api;

import com.singlepoint.common.error.AppException;
import com.singlepoint.common.error.ErrorCode;
import com.singlepoint.provider.TenantServiceProviderRepository;
import com.singlepoint.provider.kyc.KycService;
import com.singlepoint.provider.kyc.ProviderKycDocument;
import com.singlepoint.security.AppPrincipal;
import com.singlepoint.storage.StorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/providers/{providerId}/kyc")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin — KYC review", description = "Review a provider's verification documents")
public class AdminKycController {

    private final KycService kycService;
    private final TenantServiceProviderRepository tenantProviderRepository;

    public AdminKycController(KycService kycService, TenantServiceProviderRepository tenantProviderRepository) {
        this.kycService = kycService;
        this.tenantProviderRepository = tenantProviderRepository;
    }

    private void assertEnrolled(AppPrincipal p, UUID providerId) {
        if (p.getTenantId() == null
                || !tenantProviderRepository.existsByTenantIdAndServiceProviderId(p.getTenantId(), providerId)) {
            throw AppException.notFound("Service provider");
        }
    }

    @GetMapping
    @Operation(summary = "List a provider's KYC documents")
    public ResponseEntity<List<KycDtos.DocView>> list(@AuthenticationPrincipal AppPrincipal p,
                                                      @PathVariable UUID providerId) {
        assertEnrolled(p, providerId);
        return ResponseEntity.ok(kycService.list(providerId).stream().map(KycDtos.DocView::of).toList());
    }

    @GetMapping("/{docId}/file")
    @Operation(summary = "Download a KYC document")
    @com.singlepoint.audit.AuditRead(entity = "kyc_document")
    public ResponseEntity<InputStreamResource> file(@AuthenticationPrincipal AppPrincipal p,
                                                    @PathVariable UUID providerId, @PathVariable UUID docId) {
        assertEnrolled(p, providerId);
        ProviderKycDocument d = kycService.require(docId);
        if (!d.getServiceProviderId().equals(providerId)) throw AppException.notFound("Document");
        StorageService.StoredObject obj = kycService.open(d);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(obj.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + obj.filename() + "\"")
                .body(new InputStreamResource(obj.content()));
    }

    @PostMapping("/{docId}/review")
    @Operation(summary = "Accept or reject a KYC document")
    public ResponseEntity<KycDtos.DocView> review(@AuthenticationPrincipal AppPrincipal p,
                                                  @PathVariable UUID providerId, @PathVariable UUID docId,
                                                  @Valid @RequestBody KycDtos.ReviewRequest body) {
        assertEnrolled(p, providerId);
        ProviderKycDocument d = kycService.require(docId);
        if (!d.getServiceProviderId().equals(providerId)) throw AppException.notFound("Document");
        ProviderKycDocument.Status status = ProviderKycDocument.Status.valueOf(body.status().toUpperCase());
        return ResponseEntity.ok(KycDtos.DocView.of(kycService.review(docId, status, body.note(), p.getUserId())));
    }
}
