package com.singlepoint;

import com.singlepoint.crypto.CryptoKeyProvider;
import com.singlepoint.crypto.CryptoService;
import com.singlepoint.crypto.ReEncryptJob;
import com.singlepoint.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MVP-13 (C4): the crypto keyring reads both versions and ReEncryptJob re-wraps stale rows.
 * Uses hand-built {@link CryptoService} instances so the shared (v1) application context is
 * never reconfigured — a v2 context here would rotate every other test's ciphertext.
 */
class KeyRotationIT extends IntegrationTestBase {

    /** The application's real v1 key (base64 of this exact 32-char string). */
    private static final byte[] APP_V1 = "localDevSinglePointAesKey32bytes".getBytes(StandardCharsets.UTF_8);
    private static final byte[] K1 = pad("keyOne");
    private static final byte[] K2 = pad("keyTwo");
    private static final byte[] HMAC = pad("hmac");

    @Autowired com.singlepoint.security.TenantScopedExecutor tenantScoped;

    private static byte[] pad(String s) {
        byte[] b = new byte[32];
        byte[] src = s.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(src, 0, b, 0, Math.min(src.length, 32));
        return b;
    }

    private static CryptoService service(byte currentVersion, byte[]... keysByVersion) {
        return new CryptoService(new CryptoKeyProvider() {
            public byte[] aesKey() { return aesKey(currentVersion); }
            public byte[] hmacKey() { return HMAC.clone(); }
            public byte currentVersion() { return currentVersion; }
            public byte[] aesKey(byte v) { return keysByVersion[v - 1].clone(); }
        });
    }

    @Test
    void decryptFollowsTheCiphertextVersion() {
        CryptoService v1 = service((byte) 1, K1);
        CryptoService v2 = service((byte) 2, K1, K2);

        String c1 = v1.encrypt("hello");
        assertEquals(1, Base64.getDecoder().decode(c1)[0]);
        assertEquals("hello", v2.decrypt(c1), "a v2-configured service still reads v1 ciphertext");

        String c2 = v2.encrypt("hello");
        assertEquals(2, Base64.getDecoder().decode(c2)[0]);
        assertEquals("hello", v2.decrypt(c2));

        assertThrows(RuntimeException.class, () -> v1.decrypt(c2), "a v1-only service can't read v2");
    }

    @Test
    void reEncryptJobRewrapsStaleRows() {
        CryptoService v1 = service((byte) 1, APP_V1);
        CryptoService v2 = service((byte) 2, APP_V1, K2);

        String v1Cipher = v1.encrypt("221B Baker Street");
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update("insert into tenant (id, name, status, address_enc) values (?, ?, 'ACTIVE', ?)",
                tenantId, "Rotation Co " + tenantId, v1Cipher);
        assertEquals(1, Base64.getDecoder().decode(
                jdbcTemplate.queryForObject("select address_enc from tenant where id = ?", String.class, tenantId))[0]);

        int rewrapped = new ReEncryptJob(jdbcTemplate, v2, tenantScoped).runNow();
        assertTrue(rewrapped >= 1);

        String after = jdbcTemplate.queryForObject(
                "select address_enc from tenant where id = ?", String.class, tenantId);
        assertEquals(2, Base64.getDecoder().decode(after)[0], "row is now on v2");
        assertEquals("221B Baker Street", v2.decrypt(after));

        jdbcTemplate.update("delete from tenant where id = ?", tenantId);
    }
}
