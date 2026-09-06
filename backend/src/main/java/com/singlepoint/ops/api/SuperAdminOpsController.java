package com.singlepoint.ops.api;

import com.singlepoint.ops.BackupJob;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/** MVP-13 (C5): a read-only view of the scheduled database backup for the Super Admin console. */
@RestController
@RequestMapping("/api/v1/superadmin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Super Admin — Ops", description = "Backup status")
public class SuperAdminOpsController {

    private final ObjectProvider<S3Client> s3;
    private final boolean enabled;
    private final String bucket;
    private final int retentionDays;

    public SuperAdminOpsController(ObjectProvider<S3Client> s3,
                                  @Value("${sp.backup.enabled:false}") boolean enabled,
                                  @Value("${sp.backup.bucket:}") String bucket,
                                  @Value("${sp.backup.retention-days:30}") int retentionDays) {
        this.s3 = s3;
        this.enabled = enabled;
        this.bucket = bucket;
        this.retentionDays = retentionDays;
    }

    @GetMapping("/backup-status")
    @Operation(summary = "Age of the most recent database backup in S3")
    public ResponseEntity<Map<String, Object>> backupStatus() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", enabled);
        out.put("retentionDays", retentionDays);
        S3Client client = s3.getIfAvailable();
        if (!enabled || client == null || bucket.isBlank()) {
            return ResponseEntity.ok(out);
        }
        out.put("bucket", bucket);
        S3Object latest = client.listObjectsV2(ListObjectsV2Request.builder()
                        .bucket(bucket).prefix(BackupJob.PREFIX).build())
                .contents().stream().max(Comparator.comparing(S3Object::lastModified)).orElse(null);
        if (latest != null) {
            out.put("lastObjectKey", latest.key());
            out.put("lastBackupAt", latest.lastModified().toString());
            out.put("ageHours", Duration.between(latest.lastModified(), Instant.now()).toHours());
        }
        return ResponseEntity.ok(out);
    }
}
