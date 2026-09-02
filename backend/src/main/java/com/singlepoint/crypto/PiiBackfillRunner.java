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
 * MVP-5 (D): one-off migration of pre-existing plaintext PII into the encrypted columns.
 * Flyway Java migrations run before the Spring context, so they can't use {@link CryptoService};
 * this runs after it, guarded by {@code sp.pii.backfill.enabled}. Idempotent — only touches
 * rows whose {@code *_enc} column is still null. Deploy once with the flag on, confirm the
 * residual count is zero, then turn it back off.
 */
@Component
@ConditionalOnProperty(name = "sp.pii.backfill.enabled", havingValue = "true")
public class PiiBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PiiBackfillRunner.class);
    private static final int BATCH = 500;

    private record Col(String table, String plain, String enc, boolean rlsScoped) { }

    private static final List<Col> COLUMNS = List.of(
            new Col("ticket", "description", "description_enc", true),
            new Col("ticket", "resolution_notes", "resolution_notes_enc", true),
            new Col("ticket", "rating_comment", "rating_comment_enc", true),
            new Col("ticket", "service_landmark", "service_landmark_enc", true),
            new Col("flat", "address_text", "address_text_enc", true),
            new Col("tenant", "address", "address_enc", false));

    private final JdbcTemplate jdbc;
    private final CryptoService crypto;
    private final TenantScopedExecutor tenantScoped;

    public PiiBackfillRunner(JdbcTemplate jdbc, CryptoService crypto, TenantScopedExecutor tenantScoped) {
        this.jdbc = jdbc;
        this.crypto = crypto;
        this.tenantScoped = tenantScoped;
    }

    @Override
    public void run(ApplicationArguments args) {
        tenantScoped.inWildcard(() -> {
            int total = 0;
            for (Col c : COLUMNS) total += backfill(c);
            log.info("PII backfill complete — {} row-column value(s) encrypted", total);
        });
    }

    private int backfill(Col c) {
        String select = "select id, " + c.plain() + " as v from " + c.table()
                + " where " + c.enc() + " is null and " + c.plain() + " is not null limit " + BATCH;
        int done = 0;
        while (true) {
            List<Map<String, Object>> rows = jdbc.queryForList(select);
            if (rows.isEmpty()) break;
            for (Map<String, Object> row : rows) {
                jdbc.update("update " + c.table() + " set " + c.enc() + " = ? where id = ?",
                        crypto.encrypt((String) row.get("v")), row.get("id"));
            }
            done += rows.size();
            if (rows.size() < BATCH) break;
        }
        if (done > 0) log.info("PII backfill: {}.{} -> {} ({} rows)", c.table(), c.plain(), c.enc(), done);
        return done;
    }
}
