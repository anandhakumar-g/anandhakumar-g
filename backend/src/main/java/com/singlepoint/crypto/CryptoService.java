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

/**
 * AES-256-GCM field encryption + keyed HMAC-SHA256 lookup hashing.
 *
 * Ciphertext wire format (Base64 of):  [1 byte version=1][12 byte IV][GCM ciphertext+tag]
 */
@Service
public class CryptoService {

    private static final byte VERSION = 1;
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec aesKey;
    private final SecretKeySpec hmacKey;
    private final SecureRandom random = new SecureRandom();

    public CryptoService(CryptoKeyProvider keyProvider) {
        this.aesKey = new SecretKeySpec(keyProvider.aesKey(), "AES");
        this.hmacKey = new SecretKeySpec(keyProvider.hmacKey(), "HmacSHA256");
    }

    /** Encrypts a value for at-rest storage. {@code null} in -&gt; {@code null} out. */
    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        try {
            byte[] iv = new byte[IV_LEN];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[1 + IV_LEN + ct.length];
            out[0] = VERSION;
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
            if (in.length < 1 + IV_LEN + 16 || in[0] != VERSION) {
                throw new IllegalArgumentException("bad ciphertext envelope");
            }
            byte[] iv = new byte[IV_LEN];
            System.arraycopy(in, 1, iv, 0, IV_LEN);
            byte[] ct = new byte[in.length - 1 - IV_LEN];
            System.arraycopy(in, 1 + IV_LEN, ct, 0, ct.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new AppException(ErrorCode.INTERNAL, "Decryption failed", e);
        }
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
