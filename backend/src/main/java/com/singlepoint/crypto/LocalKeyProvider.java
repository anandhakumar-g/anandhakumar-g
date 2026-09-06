package com.singlepoint.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Base64;

/** Reads keys from configuration (base64). Used for every profile except {@code cloud}. */
@Component
@Profile("!cloud")
public class LocalKeyProvider implements CryptoKeyProvider {

    private final byte[] aesKey;
    private final byte[] aesKeyV2;
    private final byte[] hmacKey;
    private final byte keyVersion;

    public LocalKeyProvider(@Value("${sp.crypto.aes-key}") String aesKeyB64,
                            @Value("${sp.crypto.aes-key-2:}") String aesKeyV2B64,
                            @Value("${sp.crypto.hmac-key}") String hmacKeyB64,
                            @Value("${sp.crypto.key-version:1}") int keyVersion) {
        this.aesKey = Base64.getDecoder().decode(aesKeyB64.trim());
        this.aesKeyV2 = aesKeyV2B64 == null || aesKeyV2B64.isBlank()
                ? null : Base64.getDecoder().decode(aesKeyV2B64.trim());
        this.hmacKey = Base64.getDecoder().decode(hmacKeyB64.trim());
        this.keyVersion = (byte) keyVersion;
    }

    @PostConstruct
    void validate() {
        if (aesKey.length != 32) {
            throw new IllegalStateException("sp.crypto.aes-key must decode to 32 bytes, got " + aesKey.length);
        }
        if (hmacKey.length < 16) {
            throw new IllegalStateException("sp.crypto.hmac-key must decode to at least 16 bytes");
        }
        if (keyVersion == 2 && (aesKeyV2 == null || aesKeyV2.length != 32)) {
            throw new IllegalStateException("sp.crypto.key-version=2 needs a 32-byte sp.crypto.aes-key-2");
        }
    }

    @Override public byte[] aesKey() { return aesKey(keyVersion); }
    @Override public byte[] hmacKey() { return hmacKey.clone(); }
    @Override public byte currentVersion() { return keyVersion; }

    @Override
    public byte[] aesKey(byte version) {
        return switch (version) {
            case 1 -> aesKey.clone();
            case 2 -> {
                if (aesKeyV2 == null) throw new IllegalArgumentException("sp.crypto.aes-key-2 is not set");
                yield aesKeyV2.clone();
            }
            default -> throw new IllegalArgumentException("no key configured for ciphertext version " + version);
        };
    }
}
