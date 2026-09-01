package com.singlepoint.category.domain;

import com.singlepoint.common.domain.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Table;
import java.util.UUID;

/** Extensible vendor taxonomy. MVP-1 seeds MAINTENANCE only; verticals expand in MVP-2. */
@Entity
@Table(name = "vendor_category")
@Getter
@Setter
public class VendorCategory extends BaseEntity {

    public enum Kind {
        MAINTENANCE, FOOD_DINING, RETAIL, TRAVEL, ACCOMMODATION, EVENTS_ENTERTAINMENT, OTHER
    }

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "parent_category_id")
    private UUID parentCategoryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 30)
    private Kind kind = Kind.MAINTENANCE;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 100;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
