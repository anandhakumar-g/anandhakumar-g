package com.singlepoint.audit;

import com.singlepoint.common.domain.CreatedOnlyEntity;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.util.UUID;

/** Append-only record of security/admin-sensitive API calls (DB trigger blocks UPDATE/DELETE). */
@Entity
@Table(name = "audit_log")
@Getter
@Setter
public class AuditLog extends CreatedOnlyEntity {

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "actor_role", length = 20)
    private String actorRole;

    @Column(name = "action", nullable = false, length = 80)
    private String action;

    @Column(name = "entity_type", length = 40)
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(name = "http_method", length = 10)
    private String httpMethod;

    @Column(name = "endpoint", length = 200)
    private String endpoint;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "success", nullable = false)
    private boolean success = true;

    @Column(name = "error_code", length = 40)
    private String errorCode;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "detail")
    private String detail;
}
