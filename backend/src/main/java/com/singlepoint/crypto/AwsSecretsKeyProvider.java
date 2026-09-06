package com.singlepoint.crypto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;

import javax.annotation.PostConstruct;
import java.util.Base64;

/**
 * Loads a JSON secret {@code {"aesKey":"<b64>","hmacKey":"<b64>"}} from AWS Secrets Manager.
 * Active only on the {@code cloud} profile.
 */
@Component
@Profile("cloud")
public class AwsSecretsKeyProvider implements CryptoKeyProvider {

    private final String secretName;
    private final String secretNameV2;
    private final String region;
    private final byte keyVersion;
    private byte[] aesKey;
    private byte[] aesKeyV2;
    private byte[] hmacKey;

    public AwsSecretsKeyProvider(@Value("${sp.crypto.aws-secret-name}") String secretName,
                                 @Value("${sp.crypto.aws-secret-name-2:}") String secretNameV2,
                                 @Value("${sp.crypto.aws-region:ap-south-1}") String region,
                                 @Value("${sp.crypto.key-version:1}") int keyVersion) {
        this.secretName = secretName;
        this.secretNameV2 = secretNameV2;
        this.region = region;
        this.keyVersion = (byte) keyVersion;
    }

    @PostConstruct
    void load() {
        try (SecretsManagerClient client = SecretsManagerClient.builder()
                .region(Region.of(region)).build()) {
            JsonNode node = new ObjectMapper().readTree(client.getSecretValue(
                    GetSecretValueRequest.builder().secretId(secretName).build()).secretString());
            this.aesKey = Base64.getDecoder().decode(node.get("aesKey").asText());
            this.hmacKey = Base64.getDecoder().decode(node.get("hmacKey").asText());
            if (secretNameV2 != null && !secretNameV2.isBlank()) {
                JsonNode n2 = new ObjectMapper().readTree(client.getSecretValue(
                        GetSecretValueRequest.builder().secretId(secretNameV2).build()).secretString());
                this.aesKeyV2 = Base64.getDecoder().decode(n2.get("aesKey").asText());
            }
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load crypto keys from Secrets Manager: " + secretName, e);
        }
        if (aesKey.length != 32) {
            throw new IllegalStateException("aesKey in secret must be 32 bytes");
        }
        if (keyVersion == 2 && (aesKeyV2 == null || aesKeyV2.length != 32)) {
            throw new IllegalStateException("sp.crypto.key-version=2 needs a 32-byte aesKey in sp.crypto.aws-secret-name-2");
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
                if (aesKeyV2 == null) throw new IllegalArgumentException("no v2 secret configured");
                yield aesKeyV2.clone();
            }
            default -> throw new IllegalArgumentException("no key configured for ciphertext version " + version);
        };
    }
}
