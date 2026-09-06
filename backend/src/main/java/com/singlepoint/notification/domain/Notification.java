package com.singlepoint.notification.domain;

import com.singlepoint.common.domain.CreatedOnlyEntity;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A rendered notification delivered (or attempted) to one user on one channel. */
@Entity
@Table(name = "notification")
@Getter
@Setter
public class Notification extends CreatedOnlyEntity {

    public enum Channel { PUSH, WHATSAPP, SMS }
    public enum Status { QUEUED, SENT, FAILED, SKIPPED }

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 12)
    private Channel channel = Channel.PUSH;

    @Column(name = "template", nullable = false, length = 60)
    private String template;

    @Column(name = "title", length = 160)
    private String title;

    @Column(name = "body")
    private String body;

    /** JSON string. */
    @Column(name = "data")
    private String data;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status = Status.QUEUED;

    @Column(name = "error")
    private String error;

    @Column(name = "sent_at")
    private Instant sentAt;

    /** MVP-13 (B1): set when the user opens it in the in-app inbox. */
    @Column(name = "read_at")
    private Instant readAt;
}
