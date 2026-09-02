package com.singlepoint.tenant.domain;

import com.singlepoint.common.domain.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "tenant")
@Getter
@Setter
public class Tenant extends BaseEntity {

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "city", length = 120)
    private String city;

    @Column(name = "locality", length = 160)
    private String locality;

    @Column(name = "address")
    private String address;

    @Column(name = "pincode", length = 12)
    private String pincode;

    @Column(name = "geo_lat", precision = 9, scale = 6)
    private BigDecimal geoLat;

    @Column(name = "geo_lng", precision = 9, scale = 6)
    private BigDecimal geoLng;

    @Column(name = "logo_url")
    private String logoUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TenantStatus status = TenantStatus.ACTIVE;

    @Column(name = "reopen_window_hours", nullable = false)
    private int reopenWindowHours = 72;

    @Column(name = "require_allocation_approval", nullable = false)
    private boolean requireAllocationApproval = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "category_admin", nullable = false, length = 20)
    private CategoryAdmin categoryAdmin = CategoryAdmin.SUPER_ADMIN;

    @Column(name = "default_theme", length = 40)
    private String defaultTheme;

    @Column(name = "brand_logo_url")
    private String brandLogoUrl;

    @Column(name = "brand_primary_color", length = 9)
    private String brandPrimaryColor;
}
