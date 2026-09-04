package com.singlepoint.audit.api;

import com.singlepoint.audit.AuditLog;

import java.time.Instant;
import java.util.UUID;

public final class AuditDtos {

    private AuditDtos() { }

    /** One audit row, with actor / community names hydrated by the controller. Never carries a raw phone. */
    public record AuditLogView(UUID id, Instant at,
                               UUID actorUserId, String actorName, String actorPhoneMasked, String actorRole,
                               UUID tenantId, String tenantName,
                               String action, String entityType, UUID entityId,
                               String httpMethod, String endpoint, String requestId,
                               boolean success, String errorCode, Integer durationMs, String detail) {

        public static AuditLogView of(AuditLog a, String actorName, String actorPhoneMasked, String tenantName) {
            return new AuditLogView(a.getId(), a.getCreatedAt(),
                    a.getActorUserId(), actorName, actorPhoneMasked, a.getActorRole(),
                    a.getTenantId(), tenantName,
                    a.getAction(), a.getEntityType(), a.getEntityId(),
                    a.getHttpMethod(), a.getEndpoint(), a.getRequestId(),
                    a.isSuccess(), a.getErrorCode(), a.getDurationMs(), a.getDetail());
        }
    }
}
