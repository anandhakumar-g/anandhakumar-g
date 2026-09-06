package com.singlepoint.broadcast;

import com.singlepoint.broadcast.domain.Broadcast;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

public interface BroadcastRepository extends JpaRepository<Broadcast, UUID> {

    Page<Broadcast> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    Page<Broadcast> findByScopeInOrderByCreatedAtDesc(Collection<Broadcast.Scope> scopes, Pageable pageable);

    long countBySenderUserIdAndCreatedAtAfter(UUID senderUserId, Instant after);

    java.util.List<Broadcast> findByStatusAndScheduledForLessThanEqual(Broadcast.Status status, Instant cutoff);
}
