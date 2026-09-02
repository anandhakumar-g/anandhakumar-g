package com.singlepoint.provider.kyc.api;

import com.singlepoint.provider.kyc.ProviderKycDocument;

import javax.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;

public final class KycDtos {

    private KycDtos() { }

    public record DocView(UUID id, String docType, String status, String contentType, long sizeBytes,
                          String originalFilename, String reviewNote, Instant reviewedAt, Instant uploadedAt) {
        public static DocView of(ProviderKycDocument d) {
            return new DocView(d.getId(), d.getDocType().name(), d.getStatus().name(), d.getContentType(),
                    d.getSizeBytes(), d.getOriginalFilename(), d.getReviewNote(), d.getReviewedAt(), d.getCreatedAt());
        }
    }

    public record ReviewRequest(@NotBlank String status, String note) { }
}
