package com.singlepoint.audit;

import com.singlepoint.common.error.AppException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Writes an {@link AuditLog} row for every state-changing admin / super-admin / provider API call.
 * Never fails the underlying request; never records secrets.
 */
@Aspect
@Component
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

    private final AuditWriter auditWriter;

    public AuditAspect(AuditWriter auditWriter) {
        this.auditWriter = auditWriter;
    }

    @Around("(@annotation(org.springframework.web.bind.annotation.PostMapping) "
          + "|| @annotation(org.springframework.web.bind.annotation.PutMapping) "
          + "|| @annotation(org.springframework.web.bind.annotation.DeleteMapping)) "
          + "&& within(com.singlepoint..api..*)")
    public Object audit(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.currentTimeMillis();
        String action = pjp.getSignature().getDeclaringType().getSimpleName() + "#" + pjp.getSignature().getName();
        String errorCode = null;
        boolean ok = true;
        try {
            return pjp.proceed();
        } catch (AppException ex) {
            ok = false;
            errorCode = ex.code();
            throw ex;
        } catch (Throwable ex) {
            ok = false;
            errorCode = "SP-500";
            throw ex;
        } finally {
            try {
                auditWriter.write(action, ok, errorCode, (int) (System.currentTimeMillis() - start));
            } catch (Exception e) {
                log.debug("audit write failed: {}", e.toString());
            }
        }
    }

    /** Records a row for reads of contact / location PII (endpoints annotated {@link AuditRead}). */
    @Around("@annotation(auditRead) && within(com.singlepoint..api..*)")
    public Object auditRead(ProceedingJoinPoint pjp, AuditRead auditRead) throws Throwable {
        long start = System.currentTimeMillis();
        String action = pjp.getSignature().getDeclaringType().getSimpleName() + "#" + pjp.getSignature().getName();
        UUID entityId = firstUuidArg(pjp);
        String errorCode = null;
        boolean ok = true;
        try {
            return pjp.proceed();
        } catch (AppException ex) {
            ok = false;
            errorCode = ex.code();
            throw ex;
        } catch (Throwable ex) {
            ok = false;
            errorCode = "SP-500";
            throw ex;
        } finally {
            try {
                auditWriter.write(action, ok, errorCode, (int) (System.currentTimeMillis() - start),
                        auditRead.entity(), entityId);
            } catch (Exception e) {
                log.debug("audit-read write failed: {}", e.toString());
            }
        }
    }

    private static UUID firstUuidArg(ProceedingJoinPoint pjp) {
        for (Object a : pjp.getArgs()) {
            if (a instanceof UUID u) return u;
        }
        return null;
    }
}
