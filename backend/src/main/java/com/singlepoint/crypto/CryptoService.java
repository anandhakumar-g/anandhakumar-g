package com.singlepoint.crypto;

import com.singlepoint.common.error.AppException;
import com.singlepoint.common.error.ErrorCode;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * AES-256-GCM field encryption + keyed HMAC-SHA256 lookup hashing.
 *
 * Ciphertext wire format (Base64 of):  [1 byte version][12 byte IV][GCM ciphertext+tag].
 * MVP-13 (C4): {@code encrypt} stamps {@link CryptoKeyProvider#currentVersion()}; {@code decrypt}
 * reads the version byte and picks the matching key, so v1 ciphertext keeps decrypting after a roll.
 */
@Service
public class CryptoService {

    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;

    private final byte currentVersion;
    private final Map<Byte, SecretKeySpec> aesKeys = new HashMap<>();
    private final SecretKeySpec hmacKey;
    private final SecureRandom random = new SecureRandom();

    public CryptoService(CryptoKeyProvider keyProvider) {
        this.currentVersion = keyProvider.currentVersion();
        for (byte v = 1; v <= currentVersion; v++) {
            aesKeys.put(v, new SecretKeySpec(keyProvider.aesKey(v), "AES"));
        }
        this.hmacKey = new SecretKeySpec(keyProvider.hmacKey(), "HmacSHA256");
    }

    /** Encrypts a value for at-rest storage. {@code null} in -&gt; {@code null} out. */
    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        try {
            byte[] iv = new byte[IV_LEN];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, aesKeys.get(currentVersion), new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[1 + IV_LEN + ct.length];
            out[0] = currentVersion;
            System.arraycopy(iv, 0, out, 1, IV_LEN);
            System.arraycopy(ct, 0, out, 1 + IV_LEN, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new AppException(ErrorCode.INTERNAL, "Encryption failed", e);
        }
    }

    /** Reverses {@link #encrypt}. {@code null} in -&gt; {@code null} out. */
    public String decrypt(String stored) {
        if (stored == null) return null;
        try {
            byte[] in = Base64.getDecoder().decode(stored);
            SecretKeySpec key = in.length >= 1 + IV_LEN + 16 ? aesKeys.get(in[0]) : null;
            if (key == null) {
                throw new IllegalArgumentException("bad ciphertext envelope or unknown key version");
            }
            byte[] iv = new byte[IV_LEN];
            System.arraycopy(in, 1, iv, 0, IV_LEN);
            byte[] ct = new byte[in.length - 1 - IV_LEN];
            System.arraycopy(in, 1 + IV_LEN, ct, 0, ct.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new AppException(ErrorCode.INTERNAL, "Decryption failed", e);
        }
    }

    /** MVP-13 (C4): the wire-format version byte of a stored ciphertext, or -1 if it isn't one. */
    public int ciphertextVersion(String stored) {
        if (stored == null) return -1;
        try {
            byte[] in = Base64.getDecoder().decode(stored);
            return in.length >= 1 + IV_LEN + 16 ? (in[0] & 0xFF) : -1;
        } catch (RuntimeException e) {
            return -1;
        }
    }

    public byte currentVersion() {
        return currentVersion;
    }

    /** Deterministic lowercase-hex HMAC used for unique constraints / equality lookup. */
    public String lookupHash(String value) {
        if (value == null) return null;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(hmacKey);
            byte[] h = mac.doFinal(value.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(h.length * 2);
            for (byte b : h) sb.append(Character.forDigit((b >> 4) & 0xF, 16))
                               .append(Character.forDigit(b & 0xF, 16));
            return sb.toString();
        } catch (Exception e) {
            throw new AppException(ErrorCode.INTERNAL, "Hashing failed", e);
        }
    }
}
