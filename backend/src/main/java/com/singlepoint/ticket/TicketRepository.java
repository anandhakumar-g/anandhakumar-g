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

    /** MVP-7: a provider who owns a flat also sees tickets they raised for it. */
    @Query("select t from Ticket t where t.assignedProviderId = :providerId or t.raisedByUserId = :userId "
            + "order by t.createdAt desc")
    Page<Ticket> findByAssignedProviderIdOrRaisedByUserId(UUID providerId, UUID userId, Pageable pageable);

    List<Ticket> findByStatusAndResolvedAtBefore(TicketStatus status, Instant cutoff);

    long countByTenantIdAndRaisedByUserIdAndStatusNot(UUID tenantId, UUID raisedByUserId, TicketStatus status);

    List<Ticket> findByTenantIdAndRaisedByUserIdAndStatusNot(UUID tenantId, UUID raisedByUserId, TicketStatus status);

    @Query("select t from Ticket t where t.slaBreachedAt is null and t.slaDueAt is not null "
            + "and t.slaDueAt < :now and t.status not in :closedStates")
    List<Ticket> findSlaBreachCandidates(Instant now, java.util.Collection<TicketStatus> closedStates);

    @Query("select avg(t.rating), count(t.rating) from Ticket t "
            + "where t.assignedProviderId = :providerId and t.rating is not null")
    List<Object[]> ratingAggregate(UUID providerId);

    @Query(value = "select nextval('ticket_ref_seq')", nativeQuery = true)
    long nextReferenceSequence();

    long countByTenantIdAndStatus(UUID tenantId, TicketStatus status);

    long countByTenantId(UUID tenantId);

    long countByTenantIdAndStatusNot(UUID tenantId, TicketStatus status);

    @Query("select distinct t.raisedByUserId from Ticket t where t.categoryId = :categoryId")
    List<UUID> findDistinctRaiserIdsByCategoryId(UUID categoryId);

    boolean existsByRaisedByUserIdAndCategoryId(UUID raisedByUserId, UUID categoryId);

    long countByTenantIdAndCreatedAtAfter(UUID tenantId, Instant after);
}
