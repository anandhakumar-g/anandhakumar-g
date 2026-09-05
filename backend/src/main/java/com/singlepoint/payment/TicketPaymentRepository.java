package com.singlepoint.payment;

import com.singlepoint.payment.domain.TicketPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketPaymentRepository extends JpaRepository<TicketPayment, UUID> {

    List<TicketPayment> findByTicketId(UUID ticketId);

    Optional<TicketPayment> findByGatewayRef(String gatewayRef);

    /**
     * MVP-7: service charges on tickets a user raised in a community whose status is not in
     * {@code settledStatuses} — the "pending bills" gate for admin-initiated removal.
     */
    @Query("select p from TicketPayment p, com.singlepoint.ticket.domain.Ticket t "
            + "where p.ticketId = t.id and t.tenantId = :tenantId and t.raisedByUserId = :userId "
            + "and p.status not in :settledStatuses")
    List<TicketPayment> findUnsettledForRaiser(UUID tenantId, UUID userId,
                                               Collection<TicketPayment.Status> settledStatuses);

    /**
     * MVP-10 (A): the resident dashboard's pending-payment count — same as
     * {@link #findUnsettledForRaiser} minus the tenant filter, so it also covers a
     * community-less resident's payments (MVP-10 (C)).
     */
    @Query("select p from TicketPayment p, com.singlepoint.ticket.domain.Ticket t "
            + "where p.ticketId = t.id and t.raisedByUserId = :userId "
            + "and p.status not in :settledStatuses")
    List<TicketPayment> findUnsettledForRaiserAcrossTenants(UUID userId,
                                                            Collection<TicketPayment.Status> settledStatuses);
}
