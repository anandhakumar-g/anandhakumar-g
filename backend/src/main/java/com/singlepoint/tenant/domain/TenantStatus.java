package com.singlepoint.tenant.domain;

public enum TenantStatus {
    /** MVP-11: self-onboarded, awaiting a Super Admin's review. Invisible to residents. */
    PENDING_REVIEW, ACTIVE, SUSPENDED, ARCHIVED
}
