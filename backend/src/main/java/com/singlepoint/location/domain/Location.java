package com.singlepoint.location.domain;

import com.singlepoint.common.domain.BaseEntity;
import com.singlepoint.crypto.EncryptedStringConverter;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/** MVP-7: a labelled physical place within a community. Flats hang off a location. */
@Entity
@Table(name = "location")
@Getter
@Setter
public class Location extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "label", nullable = false, length = 120)
    private String label;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "address_enc")
    private String address;

    @Column(name = "geo_lat", precision = 9, scale = 6)
    private BigDecimal geoLat;

    @Column(name = "geo_lng", precision = 9, scale = 6)
    private BigDecimal geoLng;

    @Column(name = "pincode", length = 12)
    private String pincode;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
