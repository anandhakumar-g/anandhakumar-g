package com.singlepoint.user.domain;

/** How a user relates to a flat within a tenant. MVP-1 exercises OCCUPANT only. */
public enum MembershipRelation {
    OCCUPANT, OWNER, WATCHER
}
