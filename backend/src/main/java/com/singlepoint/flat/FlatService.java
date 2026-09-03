package com.singlepoint.flat;

import com.singlepoint.common.error.AppException;
import com.singlepoint.flat.domain.Flat;
import com.singlepoint.location.LocationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class FlatService {

    private final FlatRepository flatRepository;
    private final LocationRepository locationRepository;

    public FlatService(FlatRepository flatRepository, LocationRepository locationRepository) {
        this.flatRepository = flatRepository;
        this.locationRepository = locationRepository;
    }

    @Transactional(readOnly = true)
    public List<Flat> listForTenant(UUID tenantId) {
        return flatRepository.findByTenantIdOrderByBlockAscFlatNumberAsc(tenantId);
    }

    @Transactional
    public Flat create(UUID tenantId, UUID locationId, String block, String flatNumber, String addressText,
                       BigDecimal geoLat, BigDecimal geoLng) {
        if (locationId == null) {
            throw new AppException(com.singlepoint.common.error.ErrorCode.VALIDATION_FAILED,
                    "A location is required for a flat");
        }
        locationRepository.findByIdAndTenantId(locationId, tenantId)
                .orElseThrow(() -> AppException.notFound("Location"));
        flatRepository.findByTenantIdAndBlockAndFlatNumber(tenantId, block == null ? "" : block, flatNumber)
                .ifPresent(f -> { throw new AppException(
                        com.singlepoint.common.error.ErrorCode.CONFLICT, "That flat already exists"); });
        Flat f = new Flat();
        f.setTenantId(tenantId);
        f.setLocationId(locationId);
        f.setBlock(block);
        f.setFlatNumber(flatNumber);
        f.setAddressText(addressText);
        f.setGeoLat(geoLat);
        f.setGeoLng(geoLng);
        return flatRepository.save(f);
    }

    @Transactional
    public Flat update(UUID tenantId, UUID flatId, UUID locationId, String addressText,
                       BigDecimal geoLat, BigDecimal geoLng) {
        Flat f = flatRepository.findByIdAndTenantId(flatId, tenantId)
                .orElseThrow(() -> AppException.notFound("Flat"));
        if (locationId != null) {
            locationRepository.findByIdAndTenantId(locationId, tenantId)
                    .orElseThrow(() -> AppException.notFound("Location"));
            f.setLocationId(locationId);
        }
        if (addressText != null) f.setAddressText(addressText);
        if (geoLat != null) f.setGeoLat(geoLat);
        if (geoLng != null) f.setGeoLng(geoLng);
        return flatRepository.save(f);
    }
}
