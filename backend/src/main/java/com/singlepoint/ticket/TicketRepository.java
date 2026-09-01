package com.singlepoint.ticket;

import com.singlepoint.ticket.domain.Ticket;
import com.singlepoint.ticket.domain.TicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    Optional<Ticket> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<Ticket> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    Page<Ticket> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, TicketStatus status, Pageable pageable);

    Page<Ticket> findByRaisedByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<Ticket> findByAssignedProviderIdOrderByCreatedAtDesc(UUID providerId, Pageable pageable);

    List<Ticket> findByStatusAndResolvedAtBefore(TicketStatus status, Instant cutoff);

    @Query(value = "select nextval('ticket_ref_seq')", nativeQuery = true)
    long nextReferenceSequence();

    long countByTenantIdAndStatus(UUID tenantId, TicketStatus status);

    long countByTenantId(UUID tenantId);

    long countByTenantIdAndStatusNot(UUID tenantId, TicketStatus status);
}
