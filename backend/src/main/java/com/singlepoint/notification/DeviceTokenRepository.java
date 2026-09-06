package com.singlepoint.notification;

import com.singlepoint.notification.domain.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, UUID> {

    Optional<DeviceToken> findByToken(String token);

    List<DeviceToken> findByUserId(UUID userId);

    void deleteByUserId(UUID userId);
}
