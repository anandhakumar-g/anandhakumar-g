package com.singlepoint.auth.domain;

import com.singlepoint.common.domain.CreatedOnlyEntity;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "otp_challenge")
@Getter
@Setter
public class OtpChallenge extends CreatedOnlyEntity {

    public enum Purpose { LOGIN, CASH_PAYMENT }

    @Column(name = "phone_hash", nullable = false, length = 64)
    private String phoneHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 24)
    private Purpose purpose = Purpose.LOGIN;

    @Column(name = "code_hash", nullable = false, length = 128)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    @Column(name = "consumed_at")
    private Instant consumedAt;
}
