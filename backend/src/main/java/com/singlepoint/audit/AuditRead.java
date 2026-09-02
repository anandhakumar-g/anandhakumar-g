package com.singlepoint.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * MVP-5 (D): mark a GET endpoint that reveals contact / location PII so that each read is
 * recorded in {@code audit_log} (with {@code entity_type} / {@code entity_id} populated from
 * a {@code UUID} path variable when present).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AuditRead {
    /** The entity type recorded on the audit row, e.g. "ticket". */
    String entity();
}
