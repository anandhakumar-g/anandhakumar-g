package com.singlepoint.location;

import com.singlepoint.common.error.AppException;
import com.singlepoint.common.error.ErrorCode;
import com.singlepoint.flat.FlatRepository;
import com.singlepoint.location.domain.Location;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class LocationService {

    private final LocationRepository locationRepository;
    private final FlatRepository flatRepository;

    public LocationService(LocationRepository locationRepository, FlatRepository flatRepository) {
        this.locationRepository = locationRepository;
        this.flatRepository = flatRepository;
    }

    @Transactional(readOnly = true)
    public List<Location> listForTenant(UUID tenantId, boolean includeInactive) {
        return includeInactive
                ? locationRepository.findByTenantIdOrderByLabelAsc(tenantId)
                : locationRepository.findByTenantIdAndActiveTrueOrderByLabelAsc(tenantId);
    }

    @Transactional(readOnly = true)
    public Location require(UUID tenantId, UUID locationId) {
        return locationRepository.findByIdAndTenantId(locationId, tenantId)
                .orElseThrow(() -> AppException.notFound("Location"));
    }

    @Transactional
    public Location create(UUID tenantId, String label, String address,
                           BigDecimal geoLat, BigDecimal geoLng, String pincode) {
        if (label == null || label.isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "A location label is required");
        }
        Location l = new Location();
        l.setTenantId(tenantId);
        l.setLabel(label.trim());
        l.setAddress(address);
        l.setGeoLat(geoLat);
        l.setGeoLng(geoLng);
        l.setPincode(pincode);
        return locationRepository.save(l);
    }

    @Transactional
    public Location update(UUID tenantId, UUID locationId, String label, String address,
                           BigDecimal geoLat, BigDecimal geoLng, String pincode) {
        Location l = require(tenantId, locationId);
        if (label != null && !label.isBlank()) l.setLabel(label.trim());
        if (address != null) l.setAddress(address.isBlank() ? null : address);
        if (geoLat != null) l.setGeoLat(geoLat);
        if (geoLng != null) l.setGeoLng(geoLng);
        if (pincode != null) l.setPincode(pincode.isBlank() ? null : pincode);
        return locationRepository.save(l);
    }

    @Transactional
    public Location setActive(UUID tenantId, UUID locationId, boolean active) {
        Location l = require(tenantId, locationId);
        if (!active) {
            long flats = flatRepository.findByTenantIdOrderByBlockAscFlatNumberAsc(tenantId).stream()
                    .filter(f -> locationId.equals(f.getLocationId())).count();
            if (flats > 0) {
                throw new AppException(ErrorCode.CONFLICT,
                        "Move or remove this location's flats before deactivating it");
            }
        }
        l.setActive(active);
        return locationRepository.save(l);
    }
}
