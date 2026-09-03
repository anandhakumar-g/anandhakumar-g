package com.singlepoint.flat.api;

import com.singlepoint.common.error.AppException;
import com.singlepoint.common.error.ErrorCode;
import com.singlepoint.flat.FlatService;
import com.singlepoint.flat.InviteCodeService;
import com.singlepoint.flat.domain.Flat;
import com.singlepoint.flat.domain.InviteCode;
import com.singlepoint.location.LocationRepository;
import com.singlepoint.location.domain.Location;
import com.singlepoint.security.AppPrincipal;
import com.singlepoint.user.domain.MembershipRelation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
@Tag(name = "Admin — Flats & Invites", description = "Community flat register and invite codes")
public class AdminFlatController {

    private final FlatService flatService;
    private final InviteCodeService inviteCodeService;
    private final LocationRepository locationRepository;

    public AdminFlatController(FlatService flatService, InviteCodeService inviteCodeService,
                              LocationRepository locationRepository) {
        this.flatService = flatService;
        this.inviteCodeService = inviteCodeService;
        this.locationRepository = locationRepository;
    }

    private UUID tenant(AppPrincipal p) {
        if (p.getTenantId() == null) throw new AppException(ErrorCode.FORBIDDEN, "No active community");
        return p.getTenantId();
    }

    public record FlatView(UUID id, UUID locationId, String locationLabel, String block, String flatNumber,
                           String label, String occupancyType, String addressText,
                           BigDecimal geoLat, BigDecimal geoLng,
                           UUID currentOccupantUserId, UUID ownerUserId) {
        static FlatView of(Flat f, String locationLabel) {
            return new FlatView(f.getId(), f.getLocationId(), locationLabel, f.getBlock(), f.getFlatNumber(),
                    f.label(), f.getOccupancyType().name(), f.getAddressText(), f.getGeoLat(), f.getGeoLng(),
                    f.getCurrentOccupantUserId(), f.getOwnerUserId());
        }
    }

    private FlatView view(Flat f) {
        String label = f.getLocationId() == null ? null
                : locationRepository.findById(f.getLocationId()).map(Location::getLabel).orElse(null);
        return FlatView.of(f, label);
    }

    public record CreateFlatRequest(String locationId, String block, @NotBlank String flatNumber,
                                    String addressText, BigDecimal geoLat, BigDecimal geoLng) { }
    public record UpdateFlatRequest(String locationId, String addressText, BigDecimal geoLat, BigDecimal geoLng) { }
    public record CreateInviteRequest(String flatId, String relation, Integer validDays, Integer maxUses) { }
    public record InviteView(UUID id, String code, String relation, String status, UUID flatId,
                             int maxUses, int useCount, Instant expiresAt, Instant createdAt) {
        static InviteView of(InviteCode c) {
            return new InviteView(c.getId(), c.getCode(), c.getRelation().name(), c.getStatus().name(),
                    c.getFlatId(), c.getMaxUses(), c.getUseCount(), c.getExpiresAt(), c.getCreatedAt());
        }
    }

    @GetMapping("/flats")
    public ResponseEntity<List<FlatView>> flats(@AuthenticationPrincipal AppPrincipal p) {
        return ResponseEntity.ok(flatService.listForTenant(tenant(p)).stream().map(this::view).toList());
    }

    @PostMapping("/flats")
    public ResponseEntity<FlatView> createFlat(@AuthenticationPrincipal AppPrincipal p,
                                               @Valid @RequestBody CreateFlatRequest body) {
        Flat f = flatService.create(tenant(p),
                body.locationId() != null ? UUID.fromString(body.locationId()) : null,
                body.block(), body.flatNumber(), body.addressText(), body.geoLat(), body.geoLng());
        return ResponseEntity.status(HttpStatus.CREATED).body(view(f));
    }

    @PutMapping("/flats/{flatId}")
    public ResponseEntity<FlatView> updateFlat(@AuthenticationPrincipal AppPrincipal p,
                                               @PathVariable UUID flatId,
                                               @Valid @RequestBody UpdateFlatRequest body) {
        Flat f = flatService.update(tenant(p), flatId,
                body.locationId() != null ? UUID.fromString(body.locationId()) : null,
                body.addressText(), body.geoLat(), body.geoLng());
        return ResponseEntity.ok(view(f));
    }

    @GetMapping("/invite-codes")
    public ResponseEntity<List<InviteView>> invites(@AuthenticationPrincipal AppPrincipal p) {
        return ResponseEntity.ok(inviteCodeService.listForTenant(tenant(p)).stream().map(InviteView::of).toList());
    }

    @PostMapping("/invite-codes")
    public ResponseEntity<InviteView> createInvite(@AuthenticationPrincipal AppPrincipal p,
                                                   @RequestBody(required = false) CreateInviteRequest body) {
        CreateInviteRequest b = body != null ? body : new CreateInviteRequest(null, null, null, null);
        MembershipRelation rel = b.relation() != null
                ? MembershipRelation.valueOf(b.relation().toUpperCase()) : MembershipRelation.OCCUPANT;
        InviteCode c = inviteCodeService.create(tenant(p), p.getUserId(),
                b.flatId() != null ? UUID.fromString(b.flatId()) : null, rel, b.validDays(), b.maxUses());
        return ResponseEntity.status(HttpStatus.CREATED).body(InviteView.of(c));
    }

    @DeleteMapping("/invite-codes/{codeId}")
    public ResponseEntity<InviteView> revokeInvite(@AuthenticationPrincipal AppPrincipal p,
                                                   @PathVariable UUID codeId) {
        return ResponseEntity.ok(InviteView.of(inviteCodeService.revoke(tenant(p), codeId)));
    }
}
