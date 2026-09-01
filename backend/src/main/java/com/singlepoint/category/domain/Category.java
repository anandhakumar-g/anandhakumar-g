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

/** Ticket category. MVP-1 uses the global set (tenant_id null); admin CRUD arrives in MVP-5. */
@Entity
@Table(name = "category")
@Getter
@Setter
public class Category extends BaseEntity {

    public enum RequestType { ISSUE, FEEDBACK, ENQUIRY }

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "parent_category_id")
    private UUID parentCategoryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false, length = 20)
    private RequestType requestType = RequestType.ISSUE;

    @Column(name = "default_provider_kind", length = 40)
    private String defaultProviderKind;

    @Column(name = "sla_hours")
    private Integer slaHours;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 100;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
