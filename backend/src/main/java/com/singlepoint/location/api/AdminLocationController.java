package com.singlepoint.location.api;

import com.singlepoint.common.error.AppException;
import com.singlepoint.common.error.ErrorCode;
import com.singlepoint.location.LocationService;
import com.singlepoint.location.domain.Location;
import com.singlepoint.security.AppPrincipal;
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
import java.util.List;
import java.util.UUID;

/** MVP-7: a community admin (or a Super Admin acting as one) manages the community's locations. */
@RestController
@RequestMapping("/api/v1/admin/locations")
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
@Tag(name = "Admin — Locations", description = "Physical places within a community; flats hang off a location")
public class AdminLocationController {

    private final LocationService locationService;

    public AdminLocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    private UUID tenant(AppPrincipal p) {
        if (p.getTenantId() == null) throw new AppException(ErrorCode.FORBIDDEN, "No active community");
        return p.getTenantId();
    }

    public record LocationView(UUID id, String label, String address, BigDecimal geoLat, BigDecimal geoLng,
                               String pincode, boolean active) {
        static LocationView of(Location l) {
            return new LocationView(l.getId(), l.getLabel(), l.getAddress(), l.getGeoLat(), l.getGeoLng(),
                    l.getPincode(), l.isActive());
        }
    }

    public record CreateLocationRequest(@NotBlank String label, String address,
                                        BigDecimal geoLat, BigDecimal geoLng, String pincode) { }
    public record UpdateLocationRequest(String label, String address,
                                        BigDecimal geoLat, BigDecimal geoLng, String pincode) { }

    @GetMapping
    public ResponseEntity<List<LocationView>> list(@AuthenticationPrincipal AppPrincipal p,
                                                   @RequestParam(defaultValue = "false") boolean includeInactive) {
        return ResponseEntity.ok(locationService.listForTenant(tenant(p), includeInactive)
                .stream().map(LocationView::of).toList());
    }

    @PostMapping
    public ResponseEntity<LocationView> create(@AuthenticationPrincipal AppPrincipal p,
                                               @Valid @RequestBody CreateLocationRequest body) {
        Location l = locationService.create(tenant(p), body.label(), body.address(),
                body.geoLat(), body.geoLng(), body.pincode());
        return ResponseEntity.status(HttpStatus.CREATED).body(LocationView.of(l));
    }

    @PutMapping("/{locationId}")
    public ResponseEntity<LocationView> update(@AuthenticationPrincipal AppPrincipal p,
                                               @PathVariable UUID locationId,
                                               @RequestBody UpdateLocationRequest body) {
        Location l = locationService.update(tenant(p), locationId, body.label(), body.address(),
                body.geoLat(), body.geoLng(), body.pincode());
        return ResponseEntity.ok(LocationView.of(l));
    }

    @PostMapping("/{locationId}/deactivate")
    public ResponseEntity<LocationView> deactivate(@AuthenticationPrincipal AppPrincipal p,
                                                   @PathVariable UUID locationId) {
        return ResponseEntity.ok(LocationView.of(locationService.setActive(tenant(p), locationId, false)));
    }

    @PostMapping("/{locationId}/reactivate")
    public ResponseEntity<LocationView> reactivate(@AuthenticationPrincipal AppPrincipal p,
                                                   @PathVariable UUID locationId) {
        return ResponseEntity.ok(LocationView.of(locationService.setActive(tenant(p), locationId, true)));
    }
}
