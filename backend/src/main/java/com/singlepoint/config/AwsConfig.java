package com.singlepoint.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

import java.net.URI;

/** MVP-13 (C5): the S3 client used by the scheduled database backup. Cloud only. */
@Configuration
@Profile("cloud")
public class AwsConfig {

    @Bean
    public S3Client s3Client(@Value("${sp.aws.region:ap-south-1}") String region,
                             @Value("${sp.aws.s3-endpoint:}") String endpoint) {
        S3ClientBuilder b = S3Client.builder().region(Region.of(region));
        if (endpoint != null && !endpoint.isBlank()) {
            b = b.endpointOverride(URI.create(endpoint));
        }
        return b.build();
    }
}
