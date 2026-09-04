package com.singlepoint.offer.api;

import com.singlepoint.offer.OfferService;
import com.singlepoint.offer.OfferTargetingService;
import com.singlepoint.offer.domain.Offer;
import com.singlepoint.security.AppPrincipal;
import com.singlepoint.storage.StorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/superadmin/offers")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Super Admin — Offer approval", description = "Validate every offer + assign its audience")
public class SuperAdminOfferController {

    private final OfferService offerService;
    private final OfferTargetingService targetingService;
    private final StorageService storage;

    public SuperAdminOfferController(OfferService offerService, OfferTargetingService targetingService,
                                     StorageService storage) {
        this.offerService = offerService;
        this.targetingService = targetingService;
        this.storage = storage;
    }

    private OfferDtos.OfferView view(Offer o) {
        var t = offerService.targetOf(o.getId());
        return OfferDtos.OfferView.of(o, t, t != null ? targetingService.describe(t) : null, storage,
                offerService.aggregatesFor(o));
    }

    @GetMapping
    @Operation(summary = "Offers by status (defaults to PENDING_APPROVAL queue)")
    public ResponseEntity<List<OfferDtos.OfferView>> list(@RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        Offer.Status s = status != null ? Offer.Status.valueOf(status.toUpperCase()) : null;
        return ResponseEntity.ok(offerService.listForSuperAdmin(s, PageRequest.of(page, Math.min(size, 100)))
                .getContent().stream().map(this::view).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<OfferDtos.OfferView> get(@PathVariable UUID id) {
        return ResponseEntity.ok(view(offerService.require(id)));
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve as submitted, or override the target audience, then publish")
    public ResponseEntity<OfferDtos.OfferView> approve(@AuthenticationPrincipal AppPrincipal p,
            @PathVariable UUID id, @RequestBody(required = false) OfferDtos.ApproveRequest body) {
        var override = (body != null && body.target() != null) ? body.target().toCommand() : null;
        return ResponseEntity.ok(view(offerService.approve(p, id, override)));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<OfferDtos.OfferView> reject(@AuthenticationPrincipal AppPrincipal p,
            @PathVariable UUID id, @Valid @RequestBody OfferDtos.RejectRequest body) {
        return ResponseEntity.ok(view(offerService.reject(p, id, body.reason())));
    }
}
