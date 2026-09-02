package com.singlepoint.ticket.domain;

import com.singlepoint.common.domain.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ticket")
@Getter
@Setter
public class Ticket extends BaseEntity {

    public enum RequestMode { COMMUNITY_TICKET, DIRECT_SERVICE }
    public enum Priority { LOW, NORMAL, HIGH, URGENT }

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "reference_code", nullable = false, length = 20)
    private String referenceCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_mode", nullable = false, length = 20)
    private RequestMode requestMode = RequestMode.COMMUNITY_TICKET;

    @Column(name = "raised_by_user_id", nullable = false)
    private UUID raisedByUserId;

    @Column(name = "flat_id")
    private UUID flatId;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "subcategory_id")
    private UUID subcategoryId;

    @Column(name = "description", nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", length = 12)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private TicketStatus status = TicketStatus.NEW;

    @Column(name = "assigned_provider_id")
    private UUID assignedProviderId;

    @Column(name = "allocation_approved_by_resident", nullable = false)
    private boolean allocationApprovedByResident = false;

    @Column(name = "preferred_time_window", length = 120)
    private String preferredTimeWindow;

    @Column(name = "sla_due_at")
    private Instant slaDueAt;

    @Column(name = "sla_breached_at")
    private Instant slaBreachedAt;

    @Column(name = "service_address_text", nullable = false)
    private String serviceAddressText;

    @Column(name = "service_geo_lat", precision = 9, scale = 6)
    private BigDecimal serviceGeoLat;

    @Column(name = "service_geo_lng", precision = 9, scale = 6)
    private BigDecimal serviceGeoLng;

    @Column(name = "service_landmark", length = 200)
    private String serviceLandmark;

    @Column(name = "hold_reason")
    private String holdReason;

    @Column(name = "resolution_notes")
    private String resolutionNotes;

    @Column(name = "rating")
    private Integer rating;

    @Column(name = "rating_comment")
    private String ratingComment;

    @Column(name = "reopened_count", nullable = false)
    private int reopenedCount = 0;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "closed_at")
    private Instant closedAt;
}
