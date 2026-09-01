package com.singlepoint.flat;

import com.singlepoint.flat.domain.Flat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FlatRepository extends JpaRepository<Flat, UUID> {

    List<Flat> findByTenantIdOrderByBlockAscFlatNumberAsc(UUID tenantId);

    Optional<Flat> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Flat> findByTenantIdAndBlockAndFlatNumber(UUID tenantId, String block, String flatNumber);
}
