package com.singlepoint.provider.kyc;

import com.singlepoint.common.error.AppException;
import com.singlepoint.common.error.ErrorCode;
import com.singlepoint.provider.ServiceProviderRepository;
import com.singlepoint.provider.domain.ServiceProvider;
import com.singlepoint.storage.StorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class KycService {

    private static final Set<String> ALLOWED_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp", "image/heic", "application/pdf");

    private final ProviderKycDocumentRepository docRepository;
    private final ServiceProviderRepository providerRepository;
    private final StorageService storage;
    private final long maxBytes;

    public KycService(ProviderKycDocumentRepository docRepository,
                      ServiceProviderRepository providerRepository,
                      StorageService storage,
                      @Value("${sp.storage.max-file-bytes:26214400}") long maxBytes) {
        this.docRepository = docRepository;
        this.providerRepository = providerRepository;
        this.storage = storage;
        this.maxBytes = maxBytes;
    }

    @Transactional
    public ProviderKycDocument upload(UUID providerId, ProviderKycDocument.DocType type,
                                      String filename, String contentType, byte[] bytes, UUID uploaderUserId) {
        if (bytes.length == 0) throw new AppException(ErrorCode.VALIDATION_FAILED, "file is empty");
        if (bytes.length > maxBytes) throw new AppException(ErrorCode.VALIDATION_FAILED, "file is too large");
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "only JPG, PNG, WEBP, HEIC or PDF are accepted");
        }
        String key = storage.putPrivate("kyc/" + providerId, filename, contentType, bytes);
        ProviderKycDocument d = new ProviderKycDocument();
        d.setServiceProviderId(providerId);
        d.setDocType(type);
        d.setStorageKey(key);
        d.setContentType(contentType);
        d.setSizeBytes(bytes.length);
        d.setOriginalFilename(filename);
        d.setUploadedByUserId(uploaderUserId);
        return docRepository.save(d);
    }

    @Transactional(readOnly = true)
    public List<ProviderKycDocument> list(UUID providerId) {
        return docRepository.findByServiceProviderIdOrderByCreatedAtAsc(providerId);
    }

    @Transactional(readOnly = true)
    public ProviderKycDocument require(UUID docId) {
        return docRepository.findById(docId).orElseThrow(() -> AppException.notFound("Document"));
    }

    public StorageService.StoredObject open(ProviderKycDocument doc) {
        return storage.getPrivate(doc.getStorageKey());
    }

    @Transactional
    public ProviderKycDocument review(UUID docId, ProviderKycDocument.Status status, String note, UUID reviewerUserId) {
        if (status == ProviderKycDocument.Status.PENDING) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "review status must be ACCEPTED or REJECTED");
        }
        ProviderKycDocument d = require(docId);
        d.setStatus(status);
        d.setReviewNote(note);
        d.setReviewedByUserId(reviewerUserId);
        d.setReviewedAt(Instant.now());
        return docRepository.save(d);
    }

    /** Gate for verification: required doc types must all have an ACCEPTED document. */
    @Transactional(readOnly = true)
    public void assertVerifiable(ServiceProvider provider) {
        Set<ProviderKycDocument.DocType> accepted = docRepository
                .findByServiceProviderIdAndStatus(provider.getId(), ProviderKycDocument.Status.ACCEPTED)
                .stream().map(ProviderKycDocument::getDocType).collect(Collectors.toSet());

        boolean ok = accepted.contains(ProviderKycDocument.DocType.GOV_ID)
                && accepted.contains(ProviderKycDocument.DocType.ADDRESS_PROOF)
                && (!provider.isCompany() || accepted.contains(ProviderKycDocument.DocType.COMPANY_REG));
        if (!ok) {
            throw new AppException(ErrorCode.KYC_INCOMPLETE,
                    "Accept the provider's ID and address proof"
                    + (provider.isCompany() ? " and company registration" : "") + " before verifying");
        }
    }
}
