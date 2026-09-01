package com.singlepoint.crypto;

/** Supplies symmetric key material for PII field encryption and deterministic hashing. */
public interface CryptoKeyProvider {

    /** 32-byte AES-256 key. */
    byte[] aesKey();

    /** Key for HMAC-SHA256 lookup hashes (phone_hash, email_hash). */
    byte[] hmacKey();
}
