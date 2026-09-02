package com.singlepoint.provider.kyc.api;

import com.singlepoint.common.error.AppException;
import com.singlepoint.common.error.ErrorCode;
import com.singlepoint.provider.ServiceProviderRepository;
import com.singlepoint.provider.domain.ServiceProvider;
import com.singlepoint.provider.kyc.KycService;
import com.singlepoint.provider.kyc.ProviderKycDocument;
import com.singlepoint.security.AppPrincipal;
import com.singlepoint.storage.StorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/provider/kyc")
@PreAuthorize("hasRole('PROVIDER')")
@Tag(name = "Provider — KYC", description = "Provider uploads its own verification documents")
public class ProviderKycController {

    private final KycService kycService;
    private final ServiceProviderRepository providerRepository;

    public ProviderKycController(KycService kycService, ServiceProviderRepository providerRepository) {
        this.kycService = kycService;
        this.providerRepository = providerRepository;
    }

    private ServiceProvider self(AppPrincipal p) {
        return providerRepository.findByUserId(p.getUserId())
                .orElseThrow(() -> new AppException(ErrorCode.FORBIDDEN, "No provider profile for this account"));
    }

    @GetMapping
    @Operation(summary = "List my KYC documents and their review status")
    public ResponseEntity<List<KycDtos.DocView>> list(@AuthenticationPrincipal AppPrincipal p) {
        return ResponseEntity.ok(kycService.list(self(p).getId()).stream().map(KycDtos.DocView::of).toList());
    }

    @PostMapping(consumes = "multipart/form-data")
    @Operation(summary = "Upload a KYC document (GOV_ID / ADDRESS_PROOF / COMPANY_REG / OTHER)")
    public ResponseEntity<KycDtos.DocView> upload(@AuthenticationPrincipal AppPrincipal p,
                                                  @RequestParam("docType") String docType,
                                                  @RequestParam("file") MultipartFile file) {
        ServiceProvider sp = self(p);
        try {
            ProviderKycDocument d = kycService.upload(sp.getId(),
                    ProviderKycDocument.DocType.valueOf(docType.toUpperCase()),
                    file.getOriginalFilename(), file.getContentType(), file.getBytes(), p.getUserId());
            return ResponseEntity.status(HttpStatus.CREATED).body(KycDtos.DocView.of(d));
        } catch (IOException e) {
            throw new AppException(ErrorCode.STORAGE_ERROR, "Could not read upload", e);
        }
    }

    @GetMapping("/{docId}/file")
    @Operation(summary = "Download one of my KYC documents")
    public ResponseEntity<InputStreamResource> file(@AuthenticationPrincipal AppPrincipal p, @PathVariable UUID docId) {
        ProviderKycDocument d = kycService.require(docId);
        if (!d.getServiceProviderId().equals(self(p).getId())) throw AppException.notFound("Document");
        StorageService.StoredObject obj = kycService.open(d);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(obj.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + obj.filename() + "\"")
                .body(new InputStreamResource(obj.content()));
    }
}
