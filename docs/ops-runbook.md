# Single Point — Ops Runbook

Operational reference for running the backend in a deployed environment: metrics scraping,
dashboards, and database backups. Introduced in **MVP-12 (Part C)**.

> Scope note: shipped code for this track — the Prometheus registry + config + four domain
> counters (`com.singlepoint.observability.AppMetrics`, MVP-12), and **as of MVP-13** the
> scheduled backup job (`com.singlepoint.ops.BackupJob` + `GET /api/v1/superadmin/backup-status`).
> Grafana dashboards and the S3 bucket / lifecycle policy remain deploy-environment setup.

---

## 1. Metrics

### What is exposed

Micrometer is on the classpath via `io.micrometer:micrometer-registry-prometheus`. Spring
Boot auto-instruments:

| Meter | Meaning |
|---|---|
| `http_server_requests_seconds{uri,method,status,outcome}` | per-endpoint latency histogram + count |
| `hikaricp_connections{state=active\|idle\|pending}` | DB pool saturation |
| `jvm_memory_used_bytes`, `jvm_gc_pause_seconds`, `jvm_threads_live_threads` | JVM health |
| `process_cpu_usage`, `system_cpu_usage`, `process_uptime_seconds` | host / process |
| `flyway_migrations` (gauge) | applied migration count at boot |

Plus the four domain counters (`AppMetrics`), each rendered as `sp_*_total`:

| Counter | Incremented when |
|---|---|
| `sp_tickets_raised_total` | a ticket is created (`TicketService.raise`) |
| `sp_offers_redeemed_total` | an offer redemption is recorded (`OfferService.redeem`) |
| `sp_broadcasts_sent_total` | a broadcast is dispatched (`BroadcastService.send`) |
| `sp_communities_self_onboarded_total` | a self-onboarded community is approved (`OnboardingService.approve`) |

Every series carries the common tag `application="single-point"`
(`management.metrics.tags.application`).

### Endpoint

`GET http://<host>:<MANAGEMENT_PORT>/actuator/prometheus`

The management server runs on its own port (`MANAGEMENT_PORT`, `18081` in the local profile,
`8081` by default) so scraping never touches the public API surface or its auth chain.
Exposure is set by `management.endpoints.web.exposure.include=health,info,metrics,prometheus`.
Keep the management port firewalled to the metrics collector and the load balancer's health
probe — do not expose it publicly.

### Prometheus scrape config

```yaml
scrape_configs:
  - job_name: single-point-api
    metrics_path: /actuator/prometheus
    scrape_interval: 15s
    static_configs:
      - targets: ["api-1.internal:18081", "api-2.internal:18081"]
```

### Starter Grafana panels

1. **p95 request latency by URI** —
   `histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[5m])) by (le, uri))`
2. **Error rate** —
   `sum(rate(http_server_requests_seconds_count{outcome=~"SERVER_ERROR|CLIENT_ERROR"}[5m]))
   / sum(rate(http_server_requests_seconds_count[5m]))`
3. **Hikari pool** — `hikaricp_connections{state="active"}` vs `..{state="idle"}` vs
   `..{state="pending"}` (alert when `pending > 0` for 5m).
4. **JVM heap** — `jvm_memory_used_bytes{area="heap"}` / `jvm_memory_max_bytes{area="heap"}`.
5. **GC pause** — `rate(jvm_gc_pause_seconds_sum[5m])`.
6. **Business volume** — `increase(sp_tickets_raised_total[1h])`,
   `increase(sp_offers_redeemed_total[1h])`, `increase(sp_broadcasts_sent_total[1d])`,
   `increase(sp_communities_self_onboarded_total[7d])`.

### Suggested alerts

| Alert | Expression (for 5–10m) |
|---|---|
| API 5xx burst | error-rate expression `> 0.05` |
| Latency regression | p95 by-URI `> 2s` |
| DB pool exhausted | `hikaricp_connections{state="pending"} > 0` |
| Heap pressure | heap-used / heap-max `> 0.9` |
| Instance down | `up{job="single-point-api"} == 0` |

---

## 2. Database backups

PostgreSQL holds all durable state. RLS, encryption-at-rest for PII, and Flyway history are
all *inside* the database, so a single logical dump is a complete, restorable snapshot.

### Scheduled logical backup — `BackupJob` (MVP-13)

`com.singlepoint.ops.BackupJob` runs `pg_dump --format=custom --compress=9` on a cron, uploads
the file to `s3://<bucket>/pg/single-point-<utcTs>.dump` (`STANDARD_IA`), and prunes objects
older than the retention window. It is **`cloud` profile only and off by default**.

| Property | Env | Default | Notes |
|---|---|---|---|
| `sp.backup.enabled` | `BACKUP_ENABLED` | `false` | the job bean only loads when `true` |
| `sp.backup.bucket` | `BACKUP_BUCKET` | — | required when enabled |
| `sp.backup.cron` | `BACKUP_CRON` | `0 30 2 * * *` | daily 02:30; tighten for a shorter RPO |
| `sp.backup.retention-days` | `BACKUP_RETENTION_DAYS` | `30` | objects older than this are deleted after each run |
| `sp.aws.region` | `AWS_REGION` | `ap-south-1` | |
| `sp.aws.s3-endpoint` | `AWS_S3_ENDPOINT` | — | override for MinIO / a test endpoint |

- The container/host **must have a `pg_dump` binary matching the server major version** on
  `PATH`. DB coordinates come from `spring.datasource.*`; the password is passed via
  `PGPASSWORD` on the child process only.
- The DB role used needs read on every table; RLS is bypassed for a superuser or a
  `BYPASSRLS` role. The app role (`singlepoint_app`) is deliberately *not* one of those —
  point `spring.datasource.username` at a dedicated backup role for this job's environment,
  or run the standalone script below instead.
- `GET /api/v1/superadmin/backup-status` (SUPER_ADMIN) reports `{enabled, bucket,
  lastObjectKey, lastBackupAt, ageHours, retentionDays}` from a live `ListObjectsV2`; a
  `sp_backup_last_success_epoch` gauge is on `/actuator/prometheus`.

### Standalone script (cron / k8s CronJob alternative)

If you'd rather not shell out from the app process, the equivalent as a job:

```bash
#!/usr/bin/env bash
set -euo pipefail
TS=$(date -u +%Y%m%dT%H%M%SZ)
FILE="single-point-${TS}.dump"

pg_dump --host="$PGHOST" --port="$PGPORT" --username="$PGUSER" \
  --format=custom --compress=9 --no-owner --no-privileges \
  --file="/tmp/${FILE}" "$PGDATABASE"

aws s3 cp "/tmp/${FILE}" "s3://${BACKUP_BUCKET}/pg/${FILE}" --storage-class STANDARD_IA
rm -f "/tmp/${FILE}"
```

### S3 lifecycle

```
s3://singlepoint-backups/pg/
  - 0–7 days    : STANDARD_IA          (fast restore window)
  - 8–30 days   : GLACIER_IR
  - 31–365 days : DEEP_ARCHIVE
  - > 365 days  : expire
```

Enable bucket versioning + a bucket policy that denies `s3:DeleteObject` outside the
lifecycle rules, so a compromised app credential cannot erase history.

### Restore drill (run quarterly)

```bash
createdb singlepoint_restore_test
pg_restore --dbclean --if-exists --no-owner --no-privileges \
  --dbname=singlepoint_restore_test singlepoint-<TS>.dump

# sanity: row counts + latest migration
psql -d singlepoint_restore_test -c "select version from flyway_schema_history order by installed_rank desc limit 1;"
psql -d singlepoint_restore_test -c "select count(*) from ticket;"
```

On restore into a fresh environment the app boots normally: Flyway sees the schema is current
and runs no migrations, and `TenantAwareDataSource` re-establishes RLS on the first request.

### Point-in-time recovery

For an RPO tighter than the 6-hour dump cadence, enable WAL archiving on the primary
(`archive_mode=on`, `archive_command` shipping segments to
`s3://singlepoint-backups/wal/`) or use the managed provider's PITR feature (retain 7 days).
The logical dumps above remain the portable, cross-version escape hatch.

---

## 3. Environment quick reference

| Concern | Local | Deployed |
|---|---|---|
| API port | `18080` | `PORT` (8080) |
| Management port | `18081` | `MANAGEMENT_PORT` (8081) |
| DB | `localhost:5433/singlepoint` | `spring.datasource.*` env |
| Metrics | `curl :18081/actuator/prometheus` | scraped by Prometheus |
| Backups | not run | 6-hourly `pg_dump` → S3 + lifecycle |
