package com.singlepoint.tenant.domain;

/** Who curates a community's ticket categories. */
public enum CategoryAdmin {
    /** The platform team manages this community's categories on its behalf. */
    SUPER_ADMIN,
    /** The community's own admin manages its categories. */
    COMMUNITY
}
