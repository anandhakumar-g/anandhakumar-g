package com.singlepoint.ticket.domain;

import com.singlepoint.common.domain.CreatedOnlyEntity;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "ticket_attachment")
@Getter
@Setter
public class TicketAttachment extends CreatedOnlyEntity {

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "original_filename", length = 255)
    private String originalFilename;

    @Column(name = "uploaded_by_user_id", nullable = false)
    private UUID uploadedByUserId;
}
