package com.singlepoint.flat.domain;

import com.singlepoint.common.domain.BaseEntity;
import com.singlepoint.crypto.EncryptedStringConverter;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "flat")
@Getter
@Setter
public class Flat extends BaseEntity {

    public enum OccupancyType { OWNER_OCCUPIED, RENTED, VACANT }

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /** MVP-7: the community location this flat sits in. */
    @Column(name = "location_id")
    private UUID locationId;

    @Column(name = "block", length = 40)
    private String block;

    @Column(name = "flat_number", nullable = false, length = 40)
    private String flatNumber;

    @Column(name = "owner_user_id")
    private UUID ownerUserId;

    @Column(name = "current_occupant_user_id")
    private UUID currentOccupantUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "occupancy_type", nullable = false, length = 20)
    private OccupancyType occupancyType = OccupancyType.VACANT;

    @Column(name = "geo_lat", precision = 9, scale = 6)
    private BigDecimal geoLat;

    @Column(name = "geo_lng", precision = 9, scale = 6)
    private BigDecimal geoLng;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "address_text_enc")
    private String addressText;

    public String label() {
        return (block != null && !block.isBlank()) ? block + " - " + flatNumber : flatNumber;
    }
}
