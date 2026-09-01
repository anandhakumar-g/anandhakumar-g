package com.singlepoint.ticket;

import com.singlepoint.ticket.domain.TicketAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TicketAttachmentRepository extends JpaRepository<TicketAttachment, UUID> {

    List<TicketAttachment> findByTicketIdOrderByCreatedAtAsc(UUID ticketId);
}
