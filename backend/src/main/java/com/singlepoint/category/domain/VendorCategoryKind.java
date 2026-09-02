package com.singlepoint.category.domain;

import com.singlepoint.common.domain.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

/** A vendor vertical ("kind"). Data, not an enum — Super Admin can add new ones (MVP-3). */
@Entity
@Table(name = "vendor_category_kind")
@Getter
@Setter
public class VendorCategoryKind extends BaseEntity {

    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @Column(name = "label", nullable = false, length = 80)
    private String label;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 100;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
