package com.singlepoint.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    /**
     * MVP-9 (A): the Super Admin audit viewer. Every filter is optional; newest first.
     * Native so each nullable bind can be {@code CAST(... )}-anchored — a bare {@code :p is null}
     * over a JPQL nullable param makes PostgreSQL throw "could not determine data type of parameter".
     */
    String FILTER = """
              (cast(:actorUserId as uuid) is null or a.actor_user_id = cast(:actorUserId as uuid))
          and (cast(:tenantId    as uuid) is null or a.tenant_id     = cast(:tenantId as uuid))
          and (cast(:action      as text) is null or lower(a.action) like lower('%' || :action || '%'))
          and (cast(:entityType  as text) is null or a.entity_type   = :entityType)
          and (cast(:success as boolean)  is null or a.success       = cast(:success as boolean))
          and (cast(:fromTs as timestamptz) is null or a.created_at  >= cast(:fromTs as timestamptz))
          and (cast(:toTs   as timestamptz) is null or a.created_at   < cast(:toTs as timestamptz))
        """;

    @Query(value = "select * from audit_log a where " + FILTER + " order by a.created_at desc",
           countQuery = "select count(*) from audit_log a where " + FILTER,
           nativeQuery = true)
    Page<AuditLog> search(@Param("actorUserId") UUID actorUserId,
                          @Param("tenantId") UUID tenantId,
                          @Param("action") String action,
                          @Param("entityType") String entityType,
                          @Param("success") Boolean success,
                          @Param("fromTs") Instant fromTs,
                          @Param("toTs") Instant toTs,
                          Pageable pageable);

    @Query("select distinct a.action from AuditLog a order by a.action asc")
    List<String> distinctActions();
}
