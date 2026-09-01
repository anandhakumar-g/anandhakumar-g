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
    private final String region;
    private byte[] aesKey;
    private byte[] hmacKey;

    public AwsSecretsKeyProvider(@Value("${sp.crypto.aws-secret-name}") String secretName,
                                 @Value("${sp.crypto.aws-region:ap-south-1}") String region) {
        this.secretName = secretName;
        this.region = region;
    }

    @PostConstruct
    void load() {
        try (SecretsManagerClient client = SecretsManagerClient.builder()
                .region(Region.of(region)).build()) {
            String json = client.getSecretValue(GetSecretValueRequest.builder()
                    .secretId(secretName).build()).secretString();
            JsonNode node = new ObjectMapper().readTree(json);
            this.aesKey = Base64.getDecoder().decode(node.get("aesKey").asText());
            this.hmacKey = Base64.getDecoder().decode(node.get("hmacKey").asText());
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load crypto keys from Secrets Manager: " + secretName, e);
        }
        if (aesKey.length != 32) {
            throw new IllegalStateException("aesKey in secret must be 32 bytes");
        }
    }

    @Override public byte[] aesKey() { return aesKey.clone(); }
    @Override public byte[] hmacKey() { return hmacKey.clone(); }
}
