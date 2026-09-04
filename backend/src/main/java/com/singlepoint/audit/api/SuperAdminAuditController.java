package com.singlepoint.audit.api;

import com.singlepoint.audit.AuditLog;
import com.singlepoint.audit.AuditLogRepository;
import com.singlepoint.common.dto.PageResponse;
import com.singlepoint.common.util.PhoneNumbers;
import com.singlepoint.tenant.TenantRepository;
import com.singlepoint.tenant.domain.Tenant;
import com.singlepoint.user.AppUserRepository;
import com.singlepoint.user.domain.AppUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * MVP-9 (A): read-only window onto {@code audit_log}. The rows are written by
 * {@link com.singlepoint.audit.AuditAspect} on every state-changing admin/super-admin/provider
 * call and on {@code @AuditRead} PII reads — this controller adds no capture.
 */
@RestController
@RequestMapping("/api/v1/superadmin/audit-logs")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Super Admin — Audit", description = "Platform audit trail (read-only)")
public class SuperAdminAuditController {

    private static final int MAX_SIZE = 200;

    private final AuditLogRepository auditLogs;
    private final AppUserRepository users;
    private final TenantRepository tenants;

    public SuperAdminAuditController(AuditLogRepository auditLogs, AppUserRepository users, TenantRepository tenants) {
        this.auditLogs = auditLogs;
        this.users = users;
        this.tenants = tenants;
    }

    @GetMapping
    @Operation(summary = "Search the audit trail (every filter optional; newest first)")
    @Transactional(readOnly = true)
    public ResponseEntity<PageResponse<AuditDtos.AuditLogView>> search(
            @RequestParam(required = false) UUID actorUserId,
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Boolean success,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        Page<AuditLog> result = auditLogs.search(actorUserId, tenantId, blankToNull(action), blankToNull(entityType),
                success, from, to, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_SIZE)));
        // (param names fromTs/toTs in the repo; from/to here are the public query keys)

        Set<UUID> userIds = result.getContent().stream()
                .map(AuditLog::getActorUserId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Set<UUID> tenantIds = result.getContent().stream()
                .map(AuditLog::getTenantId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());

        Map<UUID, AppUser> userById = users.findAllById(userIds).stream()
                .collect(Collectors.toMap(AppUser::getId, Function.identity()));
        Map<UUID, Tenant> tenantById = tenants.findAllById(tenantIds).stream()
                .collect(Collectors.toMap(Tenant::getId, Function.identity()));

        return ResponseEntity.ok(PageResponse.of(result, a -> {
            AppUser actor = a.getActorUserId() == null ? null : userById.get(a.getActorUserId());
            Tenant tenant = a.getTenantId() == null ? null : tenantById.get(a.getTenantId());
            return AuditDtos.AuditLogView.of(a,
                    actor != null ? actor.getName() : null,
                    actor != null ? PhoneNumbers.mask(actor.getPhone()) : null,
                    tenant != null ? tenant.getName() : null);
        }));
    }

    @GetMapping("/actions")
    @Operation(summary = "Distinct action values, for the filter UI")
    @Transactional(readOnly = true)
    public ResponseEntity<List<String>> actions() {
        return ResponseEntity.ok(auditLogs.distinctActions());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
