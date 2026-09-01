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
    private final byte[] hmacKey;

    public LocalKeyProvider(@Value("${sp.crypto.aes-key}") String aesKeyB64,
                            @Value("${sp.crypto.hmac-key}") String hmacKeyB64) {
        this.aesKey = Base64.getDecoder().decode(aesKeyB64.trim());
        this.hmacKey = Base64.getDecoder().decode(hmacKeyB64.trim());
    }

    @PostConstruct
    void validate() {
        if (aesKey.length != 32) {
            throw new IllegalStateException("sp.crypto.aes-key must decode to 32 bytes, got " + aesKey.length);
        }
        if (hmacKey.length < 16) {
            throw new IllegalStateException("sp.crypto.hmac-key must decode to at least 16 bytes");
        }
    }

    @Override public byte[] aesKey() { return aesKey.clone(); }
    @Override public byte[] hmacKey() { return hmacKey.clone(); }
}
