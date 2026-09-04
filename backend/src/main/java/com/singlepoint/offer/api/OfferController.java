package com.singlepoint.offer.api;

import com.singlepoint.common.error.AppException;
import com.singlepoint.common.error.ErrorCode;
import com.singlepoint.offer.OfferService;
import com.singlepoint.offer.OfferTargetingService;
import com.singlepoint.offer.domain.Offer;
import com.singlepoint.security.AppPrincipal;
import com.singlepoint.storage.StorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/offers")
@Tag(name = "Offers", description = "Vendor/admin authoring, resident deals feed, redemption")
public class OfferController {

    private final OfferService offerService;
    private final OfferTargetingService targetingService;
    private final StorageService storage;

    public OfferController(OfferService offerService, OfferTargetingService targetingService, StorageService storage) {
        this.offerService = offerService;
        this.targetingService = targetingService;
        this.storage = storage;
    }

    private OfferDtos.OfferView view(Offer o) {
        var t = offerService.targetOf(o.getId());
        return OfferDtos.OfferView.of(o, t, t != null ? targetingService.describe(t) : null, storage,
                offerService.aggregatesFor(o));
    }

    // ---- resident deals feed ----

    @GetMapping
    @PreAuthorize("hasRole('RESIDENT')")
    @Operation(summary = "Deals targeted to me (active + in validity)")
    public ResponseEntity<List<OfferDtos.OfferView>> feed(@AuthenticationPrincipal AppPrincipal p) {
        return ResponseEntity.ok(offerService.feedForResident(p).stream().map(this::view).toList());
    }

    @PostMapping("/{id}/redeem")
    @PreAuthorize("hasRole('RESIDENT')")
    @Operation(summary = "Redeem an offer (reveals the coupon; provider confirms usage later)")
    public ResponseEntity<OfferDtos.RedemptionView> redeem(@AuthenticationPrincipal AppPrincipal p,
            @PathVariable UUID id, @RequestBody(required = false) OfferDtos.RedeemRequest body) {
        var r = offerService.redeem(p, id, body != null ? body.code() : null);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(OfferDtos.RedemptionView.of(r, offerService.require(id).getCouponCode()));
    }

    @PostMapping("/redemptions/{redemptionId}/confirm")
    @PreAuthorize("hasAnyRole('PROVIDER','ADMIN')")
    @Operation(summary = "Provider/admin confirms a coupon was used")
    public ResponseEntity<OfferDtos.RedemptionView> confirm(@AuthenticationPrincipal AppPrincipal p,
            @PathVariable UUID redemptionId) {
        var r = offerService.confirmRedemption(p, redemptionId);
        return ResponseEntity.ok(OfferDtos.RedemptionView.of(r, null));
    }

    // ---- feedback (MVP-8) ----

    @PostMapping("/{id}/feedback")
    @PreAuthorize("hasRole('RESIDENT')")
    @Operation(summary = "Rate + comment on an offer (one per user; re-posting updates it)")
    public ResponseEntity<OfferDtos.OfferFeedbackView> leaveFeedback(@AuthenticationPrincipal AppPrincipal p,
            @PathVariable UUID id, @Valid @RequestBody OfferDtos.FeedbackRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(OfferDtos.OfferFeedbackView.of(
                offerService.leaveFeedback(p, id, body.rating(), body.comment())));
    }

    @GetMapping("/{id}/feedback")
    @PreAuthorize("hasAnyRole('PROVIDER','ADMIN','SUPER_ADMIN')")
    @Operation(summary = "Feedback on an offer (author or Super Admin)")
    public ResponseEntity<OfferDtos.OfferFeedbackListView> feedback(@AuthenticationPrincipal AppPrincipal p,
            @PathVariable UUID id) {
        var items = offerService.feedbackFor(p, id).stream().map(OfferDtos.OfferFeedbackView::of).toList();
        var agg = offerService.aggregatesFor(offerService.require(id));
        return ResponseEntity.ok(new OfferDtos.OfferFeedbackListView(agg.ratingAvg(), agg.ratingCount(), items));
    }

    // ---- authoring (vendor + admin) ----

    @GetMapping("/mine")
    @PreAuthorize("hasAnyRole('PROVIDER','ADMIN')")
    public ResponseEntity<List<OfferDtos.OfferView>> mine(@AuthenticationPrincipal AppPrincipal p,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(offerService.listMine(p, PageRequest.of(page, Math.min(size, 100)))
                .getContent().stream().map(this::view).toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('PROVIDER','ADMIN')")
    public ResponseEntity<OfferDtos.OfferView> get(@AuthenticationPrincipal AppPrincipal p, @PathVariable UUID id) {
        Offer o = offerService.require(id);
        boolean ok = o.getCreatedByUserId().equals(p.getUserId())
                || (p.getTenantId() != null && p.getTenantId().equals(o.getTenantId()));
        if (!ok) throw AppException.notFound("Offer");
        return ResponseEntity.ok(view(o));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('PROVIDER','ADMIN')")
    public ResponseEntity<OfferDtos.OfferView> create(@AuthenticationPrincipal AppPrincipal p,
            @Valid @RequestBody OfferDtos.CreateOrUpdateRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(view(offerService.createDraft(p, body.toCommand())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('PROVIDER','ADMIN')")
    public ResponseEntity<OfferDtos.OfferView> update(@AuthenticationPrincipal AppPrincipal p,
            @PathVariable UUID id, @Valid @RequestBody OfferDtos.CreateOrUpdateRequest body) {
        return ResponseEntity.ok(view(offerService.update(p, id, body.toCommand())));
    }

    @PostMapping(value = "/{id}/image", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('PROVIDER','ADMIN')")
    public ResponseEntity<OfferDtos.OfferView> image(@AuthenticationPrincipal AppPrincipal p,
            @PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) throw new AppException(ErrorCode.VALIDATION_FAILED, "file is empty");
        try {
            String key = storage.put("offers/" + id, file.getOriginalFilename(), file.getContentType(), file.getBytes());
            return ResponseEntity.ok(view(offerService.setImage(p, id, key)));
        } catch (IOException e) {
            throw new AppException(ErrorCode.STORAGE_ERROR, "Could not read upload", e);
        }
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('PROVIDER','ADMIN')")
    @Operation(summary = "Submit an offer for Super Admin approval")
    public ResponseEntity<OfferDtos.OfferView> submit(@AuthenticationPrincipal AppPrincipal p,
            @PathVariable UUID id, @Valid @RequestBody OfferDtos.SubmitRequest body) {
        return ResponseEntity.ok(view(offerService.submit(p, id, body.target().toCommand())));
    }
}
