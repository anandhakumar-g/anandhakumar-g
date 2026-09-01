package com.singlepoint.notification.domain;

import com.singlepoint.common.domain.CreatedOnlyEntity;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "device_token")
@Getter
@Setter
public class DeviceToken extends CreatedOnlyEntity {

    public enum Platform { IOS, ANDROID, WEB }
    public enum Provider { EXPO, FCM, APNS }

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 12)
    private Platform platform;

    @Column(name = "token", nullable = false)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 12)
    private Provider provider = Provider.EXPO;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt = Instant.now();
}
