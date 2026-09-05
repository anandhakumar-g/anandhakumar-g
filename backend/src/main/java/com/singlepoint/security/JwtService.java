package com.singlepoint.security;

import com.singlepoint.common.error.AppException;
import com.singlepoint.common.error.ErrorCode;
import com.singlepoint.user.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/** Issues and validates the app session JWT (HS256). */
@Service
public class JwtService {

    private final String secret;
    private final long ttlSeconds;
    private final String issuer;
    private SecretKey key;

    public JwtService(@Value("${sp.jwt.secret}") String secret,
                      @Value("${sp.jwt.ttl-seconds:86400}") long ttlSeconds,
                      @Value("${sp.jwt.issuer:single-point}") String issuer) {
        this.secret = secret;
        this.ttlSeconds = ttlSeconds;
        this.issuer = issuer;
    }

    @PostConstruct
    void init() {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("sp.jwt.secret must be at least 32 characters");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    public String issue(UUID userId, Role role, UUID tenantId, String name, String deviceId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setIssuer(issuer)
                .setSubject(userId.toString())
                .claim("role", role.name())
                .claim("tenantId", tenantId != null ? tenantId.toString() : null)
                .claim("name", name)
                .claim("deviceId", deviceId)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public AppPrincipal parse(String token) {
        try {
            Jws<Claims> jws = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            Claims c = jws.getBody();
            String tid = c.get("tenantId", String.class);
            return new AppPrincipal(
                    UUID.fromString(c.getSubject()),
                    Role.valueOf(c.get("role", String.class)),
                    tid != null ? UUID.fromString(tid) : null,
                    c.get("name", String.class),
                    c.get("deviceId", String.class));
        } catch (ExpiredJwtException e) {
            throw new AppException(ErrorCode.TOKEN_EXPIRED, null);
        } catch (Exception e) {
            throw new AppException(ErrorCode.UNAUTHENTICATED, "Invalid token");
        }
    }

    public long getTtlSeconds() { return ttlSeconds; }
}
