package com.singlepoint.auth;

import com.singlepoint.auth.domain.OtpChallenge;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, UUID> {

    Optional<OtpChallenge> findFirstByPhoneHashAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
            String phoneHash, OtpChallenge.Purpose purpose);

    List<OtpChallenge> findByPhoneHashAndPurposeAndCreatedAtAfter(
            String phoneHash, OtpChallenge.Purpose purpose, Instant since);

    long deleteByExpiresAtBefore(Instant cutoff);
}
