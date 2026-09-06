package com.singlepoint.ops;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MVP-13 (C5): a scheduled logical backup — {@code pg_dump} → gzip (custom format is already
 * compressed) → S3 {@code PutObject} → prune objects past the retention window. Cloud only and
 * off by default ({@code sp.backup.enabled}); the host must have a {@code pg_dump} binary that
 * matches the server major version. See {@code docs/ops-runbook.md}.
 */
@Component
@Profile("cloud")
@ConditionalOnProperty(name = "sp.backup.enabled", havingValue = "true")
public class BackupJob {

    private static final Logger log = LoggerFactory.getLogger(BackupJob.class);
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    public static final String PREFIX = "pg/";

    private final S3Client s3;
    private final String bucket;
    private final int retentionDays;
    private final String jdbcUrl;
    private final String dbUser;
    private final String dbPassword;
    private final AtomicLong lastSuccessEpoch = new AtomicLong(0);

    public BackupJob(S3Client s3, MeterRegistry metrics,
                     @Value("${sp.backup.bucket}") String bucket,
                     @Value("${sp.backup.retention-days:30}") int retentionDays,
                     @Value("${spring.datasource.url}") String jdbcUrl,
                     @Value("${spring.datasource.username}") String dbUser,
                     @Value("${spring.datasource.password}") String dbPassword) {
        this.s3 = s3;
        this.bucket = bucket;
        this.retentionDays = retentionDays;
        this.jdbcUrl = jdbcUrl;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
        metrics.gauge("sp.backup.last_success_epoch", lastSuccessEpoch);
    }

    @Scheduled(cron = "${sp.backup.cron:0 30 2 * * *}")
    public void run() {
        try {
            runOnce();
        } catch (Exception e) {
            log.error("sp.backup.failed", e);
        }
    }

    void runOnce() throws Exception {
        URI u = URI.create(jdbcUrl.substring("jdbc:".length()));
        String host = u.getHost();
        int port = u.getPort() > 0 ? u.getPort() : 5432;
        String db = u.getPath().replaceFirst("^/", "");

        File dump = File.createTempFile("single-point-", ".dump");
        try {
            ProcessBuilder pb = new ProcessBuilder("pg_dump",
                    "--host=" + host, "--port=" + port, "--username=" + dbUser,
                    "--format=custom", "--compress=9", "--no-owner", "--no-privileges",
                    "--file=" + dump.getAbsolutePath(), db);
            pb.environment().put("PGPASSWORD", dbPassword);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes());
            if (!p.waitFor(30, TimeUnit.MINUTES) || p.exitValue() != 0) {
                throw new IllegalStateException("pg_dump exit " + p.exitValue() + ": " + out);
            }

            String key = PREFIX + "single-point-" + TS.format(Instant.now()) + ".dump";
            s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key)
                            .storageClass(StorageClass.STANDARD_IA).build(),
                    RequestBody.fromFile(dump));
            lastSuccessEpoch.set(Instant.now().getEpochSecond());
            log.info("sp.backup.ok key={} bytes={}", key, Files.size(dump.toPath()));
            prune();
        } finally {
            //noinspection ResultOfMethodCallIgnored
            dump.delete();
        }
    }

    private void prune() {
        Instant cutoff = Instant.now().minusSeconds(retentionDays * 86400L);
        ListObjectsV2Response resp = s3.listObjectsV2(
                ListObjectsV2Request.builder().bucket(bucket).prefix(PREFIX).build());
        for (S3Object o : resp.contents()) {
            if (o.lastModified().isBefore(cutoff)) {
                s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(o.key()).build());
                log.info("sp.backup.pruned key={}", o.key());
            }
        }
    }
}
