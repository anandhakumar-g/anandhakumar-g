package com.singlepoint.flat;

import com.singlepoint.flat.domain.InviteCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InviteCodeRepository extends JpaRepository<InviteCode, UUID> {

    Optional<InviteCode> findByCode(String code);

    List<InviteCode> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
