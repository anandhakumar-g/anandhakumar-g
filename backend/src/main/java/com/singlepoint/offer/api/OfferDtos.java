package com.singlepoint.offer.api;

import com.singlepoint.offer.OfferService;
import com.singlepoint.offer.domain.Offer;
import com.singlepoint.offer.domain.OfferRedemption;
import com.singlepoint.offer.domain.OfferTarget;
import com.singlepoint.storage.StorageService;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class OfferDtos {

    private OfferDtos() { }

    public record CreateOrUpdateRequest(
            @NotBlank String vendorCategoryId,
            @NotBlank String title,
            String description,
            @NotBlank String discountType,          // FLAT | PERCENTAGE
            @NotNull BigDecimal discountValue,
            String couponCode,
            @NotNull Instant validFrom,
            @NotNull Instant validTo,
            Integer redemptionLimitPerUser,
            Integer redemptionLimitTotal,
            String terms) {

        public OfferService.OfferCommand toCommand() {
            return new OfferService.OfferCommand(UUID.fromString(vendorCategoryId), title, description,
                    discountType, discountValue, couponCode, validFrom, validTo,
                    redemptionLimitPerUser, redemptionLimitTotal, terms);
        }
    }

    public record TargetRequest(@NotBlank String targetType, List<String> tenantIds,
                                String enquiryCategoryId, String segmentFilter) {
        public OfferService.TargetCommand toCommand() {
            return new OfferService.TargetCommand(targetType, tenantIds, enquiryCategoryId, segmentFilter);
        }
    }

    public record SubmitRequest(@NotNull TargetRequest target) { }

    public record ApproveRequest(TargetRequest target) { }   // target optional — omit to keep the author's

    public record RejectRequest(@NotBlank String reason) { }

    public record RedeemRequest(String code) { }

    public record TargetView(String targetType, List<String> tenantIds, String enquiryCategoryId,
                             String segmentFilter, String summary) {
        static TargetView of(OfferTarget t, String summary) {
            if (t == null) return null;
            return new TargetView(t.getTargetType().name(),
                    t.tenantIdList().stream().map(UUID::toString).toList(),
                    t.getEnquiryCategoryId() != null ? t.getEnquiryCategoryId().toString() : null,
                    t.getSegmentFilter(), summary);
        }
    }

    public record OfferView(UUID id, String title, String description, String status,
                            String vendorCategoryId, String discountType, BigDecimal discountValue,
                            String couponCode, String imageUrl, Instant validFrom, Instant validTo,
                            int redemptionLimitPerUser, Integer redemptionLimitTotal, String terms,
                            String createdByRole, String rejectReason, TargetView target,
                            Instant submittedAt, Instant validatedAt, Instant createdAt) {

        public static OfferView of(Offer o, OfferTarget target, String targetSummary, StorageService storage) {
            return new OfferView(o.getId(), o.getTitle(), o.getDescription(), o.getStatus().name(),
                    o.getVendorCategoryId().toString(), o.getDiscountType().name(), o.getDiscountValue(),
                    o.getCouponCode(),
                    o.getImageKey() != null ? storage.publicUrl(o.getImageKey()) : null,
                    o.getValidFrom(), o.getValidTo(), o.getRedemptionLimitPerUser(), o.getRedemptionLimitTotal(),
                    o.getTerms(), o.getCreatedByRole(), o.getRejectReason(),
                    TargetView.of(target, targetSummary), o.getSubmittedAt(), o.getValidatedAt(), o.getCreatedAt());
        }
    }

    public record RedemptionView(UUID id, UUID offerId, String status, String verifiedBy,
                                 Instant redeemedAt, Instant confirmedAt, String couponCode) {
        static RedemptionView of(OfferRedemption r, String couponCode) {
            return new RedemptionView(r.getId(), r.getOfferId(), r.getStatus().name(), r.getVerifiedBy().name(),
                    r.getRedeemedAt(), r.getConfirmedAt(), couponCode);
        }
    }
}
