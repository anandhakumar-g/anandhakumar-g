package com.singlepoint.offer.domain;

import com.singlepoint.common.domain.BaseEntity;
import com.singlepoint.crypto.EncryptedStringConverter;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.util.UUID;

/** MVP-8 (B4): one resident's rating (1–5) and optional comment on an offer. */
@Entity
@Table(name = "offer_feedback")
@Getter
@Setter
public class OfferFeedback extends BaseEntity {

    @Column(name = "offer_id", nullable = false)
    private UUID offerId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "rating", nullable = false)
    private int rating;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "comment_enc")
    private String comment;
}
