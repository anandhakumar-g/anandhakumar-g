package com.singlepoint.category.domain;

import com.singlepoint.common.domain.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.util.UUID;

/**
 * Extensible vendor taxonomy. {@code kind} is a free-text code referencing
 * {@link VendorCategoryKind#getCode()} — Super Admin can add new verticals (MVP-3).
 */
@Entity
@Table(name = "vendor_category")
@Getter
@Setter
public class VendorCategory extends BaseEntity {

    /** Well-known seed kinds. New verticals are plain codes, not members of this enum. */
    public static final String MAINTENANCE = "MAINTENANCE";

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "parent_category_id")
    private UUID parentCategoryId;

    @Column(name = "kind", nullable = false, length = 40)
    private String kind = MAINTENANCE;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 100;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
