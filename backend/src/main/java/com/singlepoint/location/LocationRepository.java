package com.singlepoint.location;

import com.singlepoint.location.domain.Location;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LocationRepository extends JpaRepository<Location, UUID> {

    List<Location> findByTenantIdOrderByLabelAsc(UUID tenantId);

    List<Location> findByTenantIdAndActiveTrueOrderByLabelAsc(UUID tenantId);

    Optional<Location> findByIdAndTenantId(UUID id, UUID tenantId);
}
