package com.singlepoint.flat;

import com.singlepoint.common.error.AppException;
import com.singlepoint.flat.domain.Flat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class FlatService {

    private final FlatRepository flatRepository;

    public FlatService(FlatRepository flatRepository) {
        this.flatRepository = flatRepository;
    }

    @Transactional(readOnly = true)
    public List<Flat> listForTenant(UUID tenantId) {
        return flatRepository.findByTenantIdOrderByBlockAscFlatNumberAsc(tenantId);
    }

    @Transactional
    public Flat create(UUID tenantId, String block, String flatNumber, String addressText,
                       BigDecimal geoLat, BigDecimal geoLng) {
        flatRepository.findByTenantIdAndBlockAndFlatNumber(tenantId, block == null ? "" : block, flatNumber)
                .ifPresent(f -> { throw new AppException(
                        com.singlepoint.common.error.ErrorCode.CONFLICT, "That flat already exists"); });
        Flat f = new Flat();
        f.setTenantId(tenantId);
        f.setBlock(block);
        f.setFlatNumber(flatNumber);
        f.setAddressText(addressText);
        f.setGeoLat(geoLat);
        f.setGeoLng(geoLng);
        return flatRepository.save(f);
    }

    @Transactional
    public Flat update(UUID tenantId, UUID flatId, String addressText, BigDecimal geoLat, BigDecimal geoLng) {
        Flat f = flatRepository.findByIdAndTenantId(flatId, tenantId)
                .orElseThrow(() -> AppException.notFound("Flat"));
        if (addressText != null) f.setAddressText(addressText);
        if (geoLat != null) f.setGeoLat(geoLat);
        if (geoLng != null) f.setGeoLng(geoLng);
        return flatRepository.save(f);
    }
}
