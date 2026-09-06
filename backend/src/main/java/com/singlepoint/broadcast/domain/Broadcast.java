package com.singlepoint.broadcast.domain;

import com.singlepoint.common.domain.CreatedOnlyEntity;
import com.singlepoint.crypto.EncryptedStringConverter;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Table;
import java.util.UUID;

/** MVP-9 (B): one announcement send. Append-only; the body is encrypted at rest. */
@Entity
@Table(name = "broadcast")
@Getter
@Setter
public class Broadcast extends CreatedOnlyEntity {

    public enum Scope { COMMUNITY, ALL_ADMINS, ALL_USERS }

    /** MVP-13 (B3): PENDING = scheduled, waiting for BroadcastDispatchJob; SENT = fanned out; CANCELLED = withdrawn. */
    public enum Status { PENDING, SENT, CANCELLED }

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 16)
    private Scope scope;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private Status status = Status.SENT;

    @Column(name = "scheduled_for")
    private java.time.Instant scheduledFor;

    /** Set for {@link Scope#COMMUNITY}; null for the platform-wide scopes. */
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "sender_user_id", nullable = false)
    private UUID senderUserId;

    @Column(name = "sender_role", nullable = false, length = 20)
    private String senderRole;

    @Column(name = "title", nullable = false, length = 160)
    private String title;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "body_enc", nullable = false)
    private String body;

    @Column(name = "recipient_count", nullable = false)
    private int recipientCount;
}
