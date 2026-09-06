package com.singlepoint.crypto;

/** Supplies symmetric key material for PII field encryption and deterministic hashing. */
public interface CryptoKeyProvider {

    /** 32-byte AES-256 key — the current version's key (see {@link #currentVersion()}). */
    byte[] aesKey();

    /** Key for HMAC-SHA256 lookup hashes (phone_hash, email_hash). */
    byte[] hmacKey();

    /**
     * MVP-13 (C4): the AES key for a specific wire-format version. Default supports version 1
     * only; a provider configured for a rotation overrides this and {@link #currentVersion()}.
     */
    default byte[] aesKey(byte version) {
        if (version == 1) return aesKey();
        throw new IllegalArgumentException("no key configured for ciphertext version " + version);
    }

    /** The version {@code CryptoService.encrypt} stamps onto new ciphertext. */
    default byte currentVersion() {
        return 1;
    }
}
