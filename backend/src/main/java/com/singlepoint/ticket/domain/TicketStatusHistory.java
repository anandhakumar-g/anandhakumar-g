package com.singlepoint.ticket.domain;

import com.singlepoint.common.domain.CreatedOnlyEntity;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Table;
import java.util.UUID;

/** Append-only audit of every ticket status transition (DB trigger blocks UPDATE/DELETE). */
@Entity
@Table(name = "ticket_status_history")
@Getter
@Setter
public class TicketStatusHistory extends CreatedOnlyEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 16)
    private TicketStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 16)
    private TicketStatus toStatus;

    @Column(name = "changed_by_user_id")
    private UUID changedByUserId;

    @Column(name = "actor_role", length = 20)
    private String actorRole;

    @Column(name = "remarks")
    private String remarks;
}
