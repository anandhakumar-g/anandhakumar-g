package com.singlepoint.auth;

import com.singlepoint.auth.domain.OtpChallenge;
import com.singlepoint.auth.sms.SmsSender;
import com.singlepoint.common.error.AppException;
import com.singlepoint.common.error.ErrorCode;
import com.singlepoint.crypto.CryptoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Issues and verifies one-time codes for phone login (and, later, cash-payment confirmation). */
@Service
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);

    private final OtpChallengeRepository repository;
    private final SmsSender smsSender;
    private final CryptoService crypto;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final SecureRandom random = new SecureRandom();

    private final int codeLength;
    private final int ttlSeconds;
    private final int maxAttempts;
    private final int resendCooldownSeconds;
    private final int perHourLimit;
    private final boolean devMode;

    public OtpService(OtpChallengeRepository repository, SmsSender smsSender, CryptoService crypto,
                      @Value("${sp.otp.length:6}") int codeLength,
                      @Value("${sp.otp.ttl-seconds:300}") int ttlSeconds,
                      @Value("${sp.otp.max-attempts:5}") int maxAttempts,
                      @Value("${sp.otp.resend-cooldown-seconds:30}") int resendCooldownSeconds,
                      @Value("${sp.otp.rate-limit.per-phone-per-hour:5}") int perHourLimit,
                      @Value("${sp.otp.dev-mode:false}") boolean devMode) {
        this.repository = repository;
        this.smsSender = smsSender;
        this.crypto = crypto;
        this.codeLength = codeLength;
        this.ttlSeconds = ttlSeconds;
        this.maxAttempts = maxAttempts;
        this.resendCooldownSeconds = resendCooldownSeconds;
        this.perHourLimit = perHourLimit;
        this.devMode = devMode;
    }

    /** @return the code when dev-mode is on (so the client/tests can display it), else null. */
    @Transactional
    public String request(String phoneE164, OtpChallenge.Purpose purpose) {
        String phoneHash = crypto.lookupHash(phoneE164);
        Instant now = Instant.now();

        List<OtpChallenge> recent = repository.findByPhoneHashAndPurposeAndCreatedAtAfter(
                phoneHash, purpose, now.minus(1, ChronoUnit.HOURS));
        if (recent.size() >= perHourLimit) {
            throw new AppException(ErrorCode.RATE_LIMITED, "Too many code requests. Try again later.");
        }
        boolean cooling = recent.stream().anyMatch(
                c -> c.getCreatedAt().isAfter(now.minusSeconds(resendCooldownSeconds)));
        if (cooling) {
            throw new AppException(ErrorCode.RATE_LIMITED,
                    "Please wait a few seconds before requesting another code.");
        }

        String code = randomCode();
        OtpChallenge challenge = new OtpChallenge();
        challenge.setPhoneHash(phoneHash);
        challenge.setPurpose(purpose);
        challenge.setCodeHash(encoder.encode(code));
        challenge.setExpiresAt(now.plusSeconds(ttlSeconds));
        repository.save(challenge);

        smsSender.sendOtp(phoneE164, code, ttlSeconds);
        return devMode ? code : null;
    }

    @Transactional
    public void verify(String phoneE164, String code, OtpChallenge.Purpose purpose) {
        String phoneHash = crypto.lookupHash(phoneE164);
        OtpChallenge challenge = repository
                .findFirstByPhoneHashAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(phoneHash, purpose)
                .orElseThrow(() -> new AppException(ErrorCode.OTP_INVALID, null));

        if (challenge.getExpiresAt().isBefore(Instant.now())) {
            throw new AppException(ErrorCode.OTP_INVALID, "Code has expired. Request a new one.");
        }
        if (challenge.getAttempts() >= maxAttempts) {
            throw new AppException(ErrorCode.OTP_INVALID, "Too many incorrect attempts. Request a new code.");
        }
        if (!encoder.matches(code, challenge.getCodeHash())) {
            challenge.setAttempts(challenge.getAttempts() + 1);
            repository.save(challenge);
            throw new AppException(ErrorCode.OTP_INVALID, "Incorrect code.");
        }
        challenge.setConsumedAt(Instant.now());
        repository.save(challenge);
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(codeLength);
        for (int i = 0; i < codeLength; i++) sb.append(random.nextInt(10));
        return sb.toString();
    }
}
