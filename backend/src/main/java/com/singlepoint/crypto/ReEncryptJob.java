package com.singlepoint.crypto;

import com.singlepoint.security.TenantScopedExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * MVP-13 (C4): after an encryption-key roll (sp.crypto.key-version bumped, a new
 * sp.crypto.aes-key-2 in place), re-wrap every ciphertext still on an older key version.
 * Same shape as {@link PiiBackfillRunner}: guarded by {@code sp.crypto.rewrap.enabled},
 * wildcard scope, batched, idempotent — it only touches rows below the current version.
 * Run once with the flag on, confirm the residual count is zero, then turn it back off.
 */
@Component
@ConditionalOnProperty(name = "sp.crypto.rewrap.enabled", havingValue = "true")
public class ReEncryptJob implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ReEncryptJob.class);
    private static final int BATCH = 500;

    /** Every {@code @Convert(EncryptedStringConverter)} column in the schema. */
    static final List<String[]> COLUMNS = List.of(
            new String[]{"app_user", "phone_enc"},
            new String[]{"app_user", "email_enc"},
            new String[]{"ticket", "description_enc"},
            new String[]{"ticket", "resolution_notes_enc"},
            new String[]{"ticket", "rating_comment_enc"},
            new String[]{"ticket", "service_landmark_enc"},
            new String[]{"flat", "address_text_enc"},
            new String[]{"tenant", "address_enc"},
            new String[]{"broadcast", "body_enc"},
            new String[]{"offer_feedback", "comment_enc"});

    private final JdbcTemplate jdbc;
    private final CryptoService crypto;
    private final TenantScopedExecutor tenantScoped;

    public ReEncryptJob(JdbcTemplate jdbc, CryptoService crypto, TenantScopedExecutor tenantScoped) {
        this.jdbc = jdbc;
        this.crypto = crypto;
        this.tenantScoped = tenantScoped;
    }

    @Override
    public void run(ApplicationArguments args) {
        int total = runNow();
        log.info("Key rotation re-wrap complete — {} value(s) moved to version {}", total, crypto.currentVersion());
    }

    /** Exposed for tests. Returns the number of values re-wrapped. */
    public int runNow() {
        return tenantScoped.inWildcard(() -> {
            int total = 0;
            for (String[] c : COLUMNS) total += rewrap(c[0], c[1]);
            return total;
        });
    }

    private int rewrap(String table, String col) {
        int touched = 0;
        int offset = 0;
        while (true) {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "select id, " + col + " as v from " + table + " where " + col + " is not null "
                            + "order by id limit " + BATCH + " offset " + offset);
            if (rows.isEmpty()) break;
            for (Map<String, Object> r : rows) {
                String v = (String) r.get("v");
                if (crypto.ciphertextVersion(v) >= crypto.currentVersion()) continue;
                jdbc.update("update " + table + " set " + col + " = ? where id = ?",
                        crypto.encrypt(crypto.decrypt(v)), r.get("id"));
                touched++;
            }
            offset += rows.size();
            if (rows.size() < BATCH) break;
        }
        if (touched > 0) log.info("  {}.{}: re-wrapped {}", table, col, touched);
        return touched;
    }
}
