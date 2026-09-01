package com.singlepoint.crypto;

import com.singlepoint.common.error.AppException;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class CryptoServiceTest {

    private final CryptoService crypto = new CryptoService(new CryptoKeyProvider() {
        public byte[] aesKey() { return "0123456789abcdef0123456789abcdef".getBytes(); }  // 32 bytes
        public byte[] hmacKey() { return "test-hmac-key-32-bytes-long-xxxxx".getBytes(); }
    });

    @Test
    void encryptDecrypt_roundTrips() {
        String plain = "+919876543210";
        String enc = crypto.encrypt(plain);
        assertNotEquals(plain, enc);
        assertEquals(plain, crypto.decrypt(enc));
    }

    @Test
    void encrypt_isNonDeterministic() {
        assertNotEquals(crypto.encrypt("same"), crypto.encrypt("same"));
    }

    @Test
    void nullPassesThrough() {
        assertNull(crypto.encrypt(null));
        assertNull(crypto.decrypt(null));
        assertNull(crypto.lookupHash(null));
    }

    @Test
    void lookupHash_isDeterministicAndCaseInsensitive() {
        assertEquals(crypto.lookupHash("Foo@Bar.com"), crypto.lookupHash("  foo@bar.com "));
        assertEquals(64, crypto.lookupHash("x").length());
        assertNotEquals(crypto.lookupHash("a"), crypto.lookupHash("b"));
    }

    @Test
    void decrypt_rejectsTamperedCiphertext() {
        String enc = crypto.encrypt("secret");
        byte[] raw = Base64.getDecoder().decode(enc);
        raw[raw.length - 1] ^= 0x01;
        String tampered = Base64.getEncoder().encodeToString(raw);
        assertThrows(AppException.class, () -> crypto.decrypt(tampered));
    }
}
