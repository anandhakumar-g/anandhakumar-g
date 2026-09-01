package com.singlepoint.audit;

import com.singlepoint.security.AppPrincipal;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class AuditWriter {

    private final AuditLogRepository repository;

    public AuditWriter(AuditLogRepository repository) {
        this.repository = repository;
    }

    /** Own transaction so a failure audit survives the business transaction's rollback. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(String action, boolean ok, String errorCode, int durationMs) {
        AuditLog entry = new AuditLog();
        entry.setAction(action);
        entry.setSuccess(ok);
        entry.setErrorCode(errorCode);
        entry.setDurationMs(durationMs);
        entry.setRequestId(MDC.get("requestId"));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AppPrincipal p) {
            entry.setActorUserId(p.getUserId());
            entry.setActorRole(p.getRole().name());
            entry.setTenantId(p.getTenantId());
        }
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes sra) {
            entry.setHttpMethod(sra.getRequest().getMethod());
            entry.setEndpoint(sra.getRequest().getRequestURI());
        }
        repository.save(entry);
    }
}
