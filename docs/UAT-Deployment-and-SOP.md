# Single Point — UAT Deployment & Operations SOP

How to stand up the platform on a UAT server, get an installable Android build for
end-to-end testing, and run day-2 operations for the **API**, the **web console** and the
**database**.

Baseline: `main` @ tag `mvp-13` + design system v2. Backend jar `single-point-api-1.0.0.jar`
(Spring Boot 2.7 · Java 17). Console: static build from `admin-web/`. Mobile: Expo SDK 57,
distributed as an APK from EAS.

> This SOP targets a single **Linux VM** running the API + PostgreSQL + nginx. No Docker /
> Kubernetes assets are in the repo yet — containerising UAT is on the post-UAT list
> (`docs/POST-UAT-HARDENING.md`).

---

## 0. Topology

| Tier | What | Default port | Talks to |
|---|---|---|---|
| **API** | `java -jar single-point-api-1.0.0.jar`, Spring profile `uat` | app `18080`, management `18081` | PostgreSQL |
| **Web console** | static files (`admin-web/dist`) served by nginx | `443` (or `80`) | the API, cross-origin |
| **Mobile app** | Android APK on testers' phones | — | the API over the internet / VPN |
| **Database** | PostgreSQL 16 | `5432` | — (localhost only) |

The `uat` Spring profile (`backend/src/main/resources/application-uat.properties`) is
production-like — env-driven datasource, Flyway `validate`, INFO logging — but keeps the
**external providers on stubs**: OTP codes are shown on screen (no SMS gateway), payments use
the built-in stub (`/dev/pay/**` settle endpoints), WhatsApp is log-only, files go to the
server disk. Switch any one to "real" by setting its env vars (see Appendix A).

---

## 1. One-time server setup

### 1.1 Prerequisites (Ubuntu 22.04 / RHEL 9 example)

```bash
sudo apt update
sudo apt install -y openjdk-17-jre-headless postgresql-16 postgresql-client-16 nginx git
java -version          # must report 17
psql --version         # 16.x
```

Node/EAS are **not** needed on this server — the APK builds in Expo's cloud.

### 1.2 Database + application role

Single Point relies on PostgreSQL Row-Level Security, which is ignored for superusers — the
app **must** connect as a non-superuser role that owns the schema.

```bash
sudo -u postgres createdb singlepoint
# one-time bootstrap: creates the non-superuser role 'singlepoint_app' and gives it the schema
sudo -u postgres psql -d singlepoint -f /opt/single-point/repo/backend/db/bootstrap.sql
# set a real password (bootstrap.sql seeds a throwaway one)
sudo -u postgres psql -c "ALTER ROLE singlepoint_app PASSWORD 'REPLACE_WITH_A_STRONG_SECRET';"
```

Keep PostgreSQL bound to `localhost` (`listen_addresses = 'localhost'` in
`postgresql.conf`). The API is the only client.

### 1.3 Directories and a service account

```bash
sudo useradd --system --home /opt/single-point --shell /usr/sbin/nologin singlepoint
sudo mkdir -p /opt/single-point/releases /var/lib/single-point/attachments /var/log/single-point /etc/single-point
sudo chown -R singlepoint:singlepoint /opt/single-point /var/lib/single-point /var/log/single-point
sudo chmod 750 /etc/single-point
```

### 1.4 Crypto keys (generate once, keep safe)

```bash
# 32-byte AES key and 32-byte HMAC key, base64-encoded
openssl rand -base64 32     # -> SP_AES_KEY
openssl rand -base64 32     # -> SP_HMAC_KEY
openssl rand -base64 48     # -> JWT_SECRET (any >= 32 chars)
```

> The HMAC key is what makes `phone_hash` lookups work. **If it ever changes, every existing
> user becomes unfindable by phone.** Treat it as permanent for the life of the environment.

### 1.5 Environment file

Create `/etc/single-point/uat.env` (root-owned, `chmod 640`, group `singlepoint`). Minimum:

```ini
SPRING_PROFILES_ACTIVE=uat
PORT=18080
MANAGEMENT_PORT=18081

DB_URL=jdbc:postgresql://localhost:5432/singlepoint
DB_USER=singlepoint_app
DB_PASSWORD=REPLACE_WITH_A_STRONG_SECRET

JWT_SECRET=<from openssl>
SP_AES_KEY=<from openssl>
SP_HMAC_KEY=<from openssl>

# the console's public origin(s), comma-separated — needed for CORS
CORS_ALLOWED_ORIGINS=https://uat-console.example.com

# public base URL of THIS api, used to build attachment links
PUBLIC_API_URL=https://uat-api.example.com

# first-boot Super Admin (created once if no SUPER_ADMIN exists)
SUPER_ADMIN_PHONE=+9190000000000
SUPER_ADMIN_NAME=UAT Platform Owner

# UAT default: OTP shown on the screen, no SMS gateway. Set false + the MSG91_* vars to test real SMS.
OTP_DEV_MODE=true
```

Full list, including how to turn on real SMS / payments / S3 / WhatsApp: **Appendix A**.

---

## 2. Deploy / update the API

### 2.1 Build the jar (on a build box or CI — needs JDK 17 + Maven)

```bash
cd repo/backend
JAVA_HOME=/path/to/jdk-17 mvn -q -o -DskipTests clean package
ls target/single-point-api-1.0.0.jar
```

Run the test suite in CI before promoting (`mvn -q test` against a throwaway
`singlepoint_test` DB) — do not gate UAT on tests run on the UAT box.

### 2.2 Install (first time)

```bash
sudo install -o singlepoint -g singlepoint -m 640 \
  target/single-point-api-1.0.0.jar /opt/single-point/releases/
sudo ln -sfn /opt/single-point/releases/single-point-api-1.0.0.jar /opt/single-point/current.jar
sudo cp deploy/single-point-api.service /etc/systemd/system/   # unit in Appendix B
sudo systemctl daemon-reload
sudo systemctl enable --now single-point-api
```

### 2.3 Verify

```bash
curl -s localhost:18081/actuator/health          # {"status":"UP", ...}
curl -s localhost:18080/v3/api-docs.yaml | head  # openapi: 3.0.1 ... version: MVP-13
journalctl -u single-point-api -n 50 --no-pager  # look for "Started SinglePointApplication"
```

On the **first** boot against an empty DB:
- Flyway applies `V1`..`V36` (watch the log; it stops the app on a failed migration).
- `SuperAdminEnsurer` creates one SUPER_ADMIN from `SUPER_ADMIN_PHONE` — logged as
  `SuperAdminEnsurer: created SUPER_ADMIN for +91...`. Idempotent on later boots.

### 2.4 Rolling update (new version)

```bash
sudo install -o singlepoint -g singlepoint -m 640 \
  target/single-point-api-<new>.jar /opt/single-point/releases/
sudo ln -sfn /opt/single-point/releases/single-point-api-<new>.jar /opt/single-point/current.jar
sudo systemctl restart single-point-api
journalctl -u single-point-api -f     # watch Flyway + "Started"
curl -s localhost:18081/actuator/health
```

New Flyway migrations run automatically on restart. `spring.flyway.clean-disabled=true` in
the `uat` profile prevents an accidental wipe.

### 2.5 Rollback

Only safe if the new version added **no** migrations (check `git log` for `Vxx__` files):

```bash
sudo ln -sfn /opt/single-point/releases/single-point-api-<previous>.jar /opt/single-point/current.jar
sudo systemctl restart single-point-api
```

If migrations ran, roll back by restoring the pre-deploy DB dump (see §6.5) and then the old
jar.

---

## 3. Deploy / update the web console

### 3.1 Build (on the build box — needs Node 20+)

The API base URL is **compiled in**, so build once per environment:

```bash
cd repo/admin-web
npm ci
VITE_API_BASE=https://uat-api.example.com/api/v1 npm run build
# -> admin-web/dist/  (static files)
```

### 3.2 Publish

```bash
sudo rsync -a --delete admin-web/dist/ /var/www/single-point-console/
# nginx site config in Appendix B — SPA fallback to index.html
sudo nginx -t && sudo systemctl reload nginx
```

### 3.3 Verify

Open `https://uat-console.example.com`, sign in with `SUPER_ADMIN_PHONE` and the code shown
on screen (`OTP_DEV_MODE=true`). You should land on the Dashboard.

> The console only follows the browser's light/dark setting — there is no in-app toggle.

---

## 4. Android APK for end-to-end testing

There is no Android SDK on the server; the APK builds in **EAS (Expo's cloud)** under the
Expo account. `mobile/eas.json` has a `preview` profile that produces a side-loadable
**release APK**.

### 4.1 Point the build at UAT

`mobile/eas.json` → `build.preview.env.EXPO_PUBLIC_API_URL` — set it to the **public** UAT
API URL (must be reachable from testers' phones):

```json
"preview": {
  "distribution": "internal",
  "android": { "buildType": "apk" },
  "env": { "EXPO_PUBLIC_API_URL": "https://uat-api.example.com" }
}
```

Commit that change. The URL is baked in — rebuild whenever it changes.

### 4.2 Build

```bash
cd repo/mobile
eas login                                   # the Expo account
eas build --platform android --profile preview
```

First run is interactive: accept "create a project" (writes `extra.eas.projectId` to
`app.json` — commit it) and "generate a keystore". Build takes ~10–20 min; the CLI prints a
direct `.apk` download link (also at `expo.dev` → project **single-point** → Builds).

### 4.3 Distribute & install

- Share the EAS link, or host the `.apk` behind the UAT login, or push via MDM.
- On the phone: open the `.apk` → Android warns about unknown sources → allow this source →
  install → open **Single Point**.
- Sign in with any seed / test phone; the OTP is shown on screen.

### 4.4 If every request fails on the device

`EXPO_PUBLIC_API_URL` is wrong or unreachable, the API isn't on HTTPS, or a firewall blocks
it. Confirm `https://uat-api.example.com/swagger-ui.html` loads in the phone's browser, then
rebuild.

### 4.5 iOS

The `preview` APK is Android only. iOS UAT needs an Apple Developer account and a
TestFlight (or ad-hoc) build: `eas build --platform ios --profile preview` +
`eas submit`. Out of scope for the first UAT round.

---

## 5. Post-deploy smoke test (~10 min)

Run after every API or console deploy. Sign in on the console as Super Admin and in the app
(browser or APK) as a resident.

1. **API up** — `/actuator/health` UP; `/v3/api-docs.yaml` shows `version: MVP-13`.
2. **Console login** — Super Admin signs in; Dashboard renders with tiles and charts.
3. **Onboard** — approve a community request (or seed one); its requester becomes admin.
4. **Resident** — sign in, complete profile, join with an admin invite code, raise a ticket.
5. **Admin** — see the ticket in the queue, acknowledge, assign to a verified provider.
6. **Provider** — accept the job, resolve it.
7. **Resident** — close + rate; the rating shows on the provider.
8. **Payment (stub)** — provider creates a charge; open the pay link;
   `POST /dev/pay/<ref>/settle`; a receipt is issued.
9. **Notifications** — the resident's Alerts tab shows the updates unread; badge clears on read.
10. **Export** — `GET /api/v1/me/export` returns the resident's JSON.

Any failure → check `journalctl -u single-point-api` and the "Common issues" table (§6.9).

---

## 6. Day-2 operations

### 6.1 Start / stop / restart

```bash
sudo systemctl {start|stop|restart|status} single-point-api
sudo systemctl reload nginx        # after a console redeploy
sudo systemctl restart postgresql  # rarely; drains connections
```

### 6.2 Logs

| What | Where |
|---|---|
| API (stdout/stderr) | `journalctl -u single-point-api -f` (or `-n 200 --no-pager`) |
| API access log | same stream — lines tagged `com.singlepoint.ACCESS` (`GET /path -> 200 (12 ms)`) |
| API exceptions | same stream — `com.singlepoint.EXCEPTION` (business `AppException`s) and stack traces |
| nginx | `/var/log/nginx/{access,error}.log` |
| PostgreSQL | `/var/log/postgresql/postgresql-16-main.log` |

`journald` handles rotation. To grep a request end-to-end, filter by its `requestId` (in
every error body and access line).

### 6.3 Health & metrics

```bash
curl -s localhost:18081/actuator/health | jq
curl -s localhost:18081/actuator/prometheus | grep -E 'sp_|http_server_requests|hikaricp'
```

Point a Prometheus scraper at `:18081/actuator/prometheus` (tag `application="single-point"`).
Panels + alert list: `docs/ops-runbook.md`.

### 6.4 Database

```bash
# ALWAYS connect as the app role, never as postgres — RLS is invisible as a superuser
PGPASSWORD=... psql -h localhost -U singlepoint_app -d singlepoint
```

Useful:

```sql
select version, description, success from flyway_schema_history order by installed_rank desc limit 10;
select role, count(*) from app_user group by role;
select status, count(*) from ticket group by status;
select name, status, brand_primary_color from tenant;
```

> Reading a tenant-scoped table (`ticket`, `flat`, `ticket_payment`, …) as `singlepoint_app`
> without setting the GUCs returns **zero rows** — that's RLS failing closed, not data loss.
> For an admin read across everything: `SET app.current_tenant_id = '*'; SET app.current_user_id = '*';`
> in the same session first.

### 6.5 Backups (UAT)

The automated `pg_dump → S3` job is `cloud`-profile only. For UAT, a cron on the box:

```bash
# /etc/cron.d/single-point-uat-backup
30 2 * * * postgres pg_dump -Fc singlepoint > /var/backups/single-point/uat-$(date +\%F).dump && \
           find /var/backups/single-point -name 'uat-*.dump' -mtime +14 -delete
```

Always take a manual dump **before** deploying a version that adds migrations:

```bash
sudo -u postgres pg_dump -Fc singlepoint > /var/backups/single-point/pre-deploy-$(date +%F-%H%M).dump
```

Restore drill:

```bash
sudo systemctl stop single-point-api
sudo -u postgres dropdb singlepoint && sudo -u postgres createdb singlepoint
sudo -u postgres psql -d singlepoint -f .../backend/db/bootstrap.sql
sudo -u postgres psql -c "ALTER ROLE singlepoint_app PASSWORD '...';"
sudo -u postgres pg_restore -d singlepoint --no-owner /var/backups/single-point/<dump>
sudo systemctl start single-point-api
```

### 6.6 Reset UAT data (start clean)

```bash
sudo systemctl stop single-point-api
sudo -u postgres dropdb singlepoint && sudo -u postgres createdb singlepoint
sudo -u postgres psql -d singlepoint -f .../backend/db/bootstrap.sql
sudo -u postgres psql -c "ALTER ROLE singlepoint_app PASSWORD '...';"
sudo systemctl start single-point-api   # Flyway rebuilds; SuperAdminEnsurer recreates the Super Admin
```

### 6.7 Rotate a secret

| Secret | Procedure | Blast radius |
|---|---|---|
| `JWT_SECRET` | change it, restart the API | every session is invalidated — users sign in again |
| `SP_AES_KEY` (encryption key) | set `SP_AES_KEY_2` + `SP_CRYPTO_KEY_VERSION=2`, restart (old data still decrypts); then `CRYPTO_REWRAP_ENABLED=true` for one boot to re-wrap, then set it back to false | none if done in order — see ADR-040 |
| `SP_HMAC_KEY` | **do not rotate** — breaks phone lookup for all existing users |
| DB password | `ALTER ROLE singlepoint_app PASSWORD ...`, update `uat.env`, restart the API |

### 6.8 Grant platform / community access

- **Super Admin** — set `SUPER_ADMIN_PHONE` before the first boot, or (later) promote an
  existing user: as `singlepoint_app` with the GUCs set to `*`,
  `UPDATE app_user SET role='SUPER_ADMIN' WHERE phone='+91...';` then that user signs out/in.
- **Community Admin** — the Super Admin approves that person's community request in the
  console, or attaches them via `POST /api/v1/superadmin/tenants/{id}/admins`.
- **Provider** — Super Admin creates + verifies in the console; a community admin then enrols.

### 6.9 Common issues

| Symptom | Likely cause | Fix |
|---|---|---|
| API won't start, log shows `FlywayValidateException` / `checksum mismatch` | a migration file changed after it was applied | restore the pre-deploy dump; never edit an applied `Vxx__` file |
| API won't start, `Table 'xxx' ... missing column` | jar and schema out of step (`ddl-auto=validate`) | deploy the jar whose migrations match, or restore + redeploy |
| Every tenant-scoped query returns nothing | connected as `postgres`, or GUCs unset | connect as `singlepoint_app`; `SET app.current_tenant_id='*'` for admin reads |
| Console: "Failed to fetch" / CORS error in devtools | `CORS_ALLOWED_ORIGINS` doesn't list the console origin | add it to `uat.env`, restart the API |
| Console: "This console is for platform Super Admins only" | signing in as a non-Super-Admin | use `SUPER_ADMIN_PHONE` |
| App on device: all calls fail | `EXPO_PUBLIC_API_URL` wrong / not HTTPS / unreachable | fix `eas.json`, rebuild the APK |
| No OTP shown | `OTP_DEV_MODE=false` without a working SMS provider | set it back to `true`, or configure `MSG91_*` |
| `pg_dump` in cron does nothing | runs as the wrong user / no perms on `/var/backups` | run as `postgres`, `mkdir -p` + `chown` the dir |
| Lots of `SP-429` in the log | OTP or broadcast rate limit hit during load testing | expected; back off, or raise `sp.otp.rate-limit.*` for the UAT window |

---

## 7. Per-release checklist

- [ ] CI: `mvn test` green; `admin-web` `npm run build` clean; `mobile` `npx tsc --noEmit` clean.
- [ ] Note whether the release adds `Vxx__` migrations (affects rollback).
- [ ] `pg_dump -Fc` the UAT DB (§6.5).
- [ ] Deploy the API jar (§2.4); watch Flyway; `/actuator/health` UP.
- [ ] Rebuild + publish the console if `admin-web/` changed (§3).
- [ ] Rebuild the APK if `mobile/` or `EXPO_PUBLIC_API_URL` changed (§4); circulate the new link.
- [ ] Run the §5 smoke test.
- [ ] Record the version, git SHA and time in the deployment log.

---

## Appendix A — environment variables

**Required** (`uat` profile refuses to start / misbehaves without these):

| Var | Meaning |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `uat` |
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | PostgreSQL connection (`jdbc:postgresql://localhost:5432/singlepoint`) |
| `JWT_SECRET` | ≥ 32 chars; signs session tokens |
| `SP_AES_KEY` | base64 of 32 random bytes; PII field encryption |
| `SP_HMAC_KEY` | base64 of 32 random bytes; deterministic phone-hash lookup — **permanent** |

**Recommended:**

| Var | Default | Meaning |
|---|---|---|
| `PORT` / `MANAGEMENT_PORT` | `8080` / `8081` | use `18080` / `18081` to match the docs |
| `CORS_ALLOWED_ORIGINS` | `*` | set to the console origin(s), comma-separated |
| `PUBLIC_API_URL` | `http://localhost:8080` | used to build attachment URLs |
| `SUPER_ADMIN_PHONE` | — | first-boot Super Admin; `+` and country code |
| `SUPER_ADMIN_NAME` | `Platform Owner` | display name |
| `OTP_DEV_MODE` | `true` | `true` = code on screen; `false` needs a real SMS provider |
| `DB_POOL_MAX` | `15` | Hikari pool size |
| `SSL_ENABLED` | `false` | leave off; terminate TLS at nginx |

**Optional — turn a stub into a real integration:**

| Feature | Vars |
|---|---|
| Real SMS OTP | `OTP_DEV_MODE=false`, `OTP_PROVIDER=msg91`, `MSG91_AUTH_KEY`, `MSG91_TEMPLATE_ID` |
| Real payments | `PAYMENT_PROVIDER=razorpay`, `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, `RAZORPAY_WEBHOOK_SECRET` (needs the `cloud` profile — the Razorpay beans are `@Profile("cloud")`) |
| S3 attachments | `STORAGE_PROVIDER=s3`, `S3_ATTACHMENTS_BUCKET`, `AWS_REGION`, and AWS credentials in the environment |
| WhatsApp (Meta) | `WHATSAPP_PROVIDER=meta`, `WHATSAPP_META_TOKEN`, `WHATSAPP_META_PHONE_NUMBER_ID` |
| Push (default Expo, no creds) | `PUSH_PROVIDER=expo` (already the default) |

> Razorpay, S3 and the automated backup job are wired only under the `cloud` profile. If UAT
> needs real payments, run `SPRING_PROFILES_ACTIVE=cloud` and supply the **full** cloud var
> set (`application-cloud.properties`) — there are no defaults there.

---

## Appendix B — unit & site config

### `/etc/systemd/system/single-point-api.service`

```ini
[Unit]
Description=Single Point API
After=network.target postgresql.service
Wants=postgresql.service

[Service]
User=singlepoint
Group=singlepoint
EnvironmentFile=/etc/single-point/uat.env
ExecStart=/usr/bin/java -XX:MaxRAMPercentage=70 -jar /opt/single-point/current.jar
SuccessExitStatus=143
Restart=on-failure
RestartSec=5
# hardening
NoNewPrivileges=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=/var/lib/single-point /var/log/single-point
PrivateTmp=true

[Install]
WantedBy=multi-user.target
```

### `/etc/nginx/sites-available/single-point-console`

```nginx
server {
    listen 443 ssl http2;
    server_name uat-console.example.com;
    ssl_certificate     /etc/letsencrypt/live/uat-console.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/uat-console.example.com/privkey.pem;

    root /var/www/single-point-console;
    index index.html;

    # SPA: unknown paths fall back to index.html
    location / { try_files $uri $uri/ /index.html; }

    # static asset caching
    location /assets/ { expires 1y; add_header Cache-Control "public, immutable"; }
}
```

A second nginx `server {}` for `uat-api.example.com` should `proxy_pass` to
`http://127.0.0.1:18080;` (and only expose `:18081` internally). Set
`proxy_set_header X-Forwarded-Proto https;` and a generous `client_max_body_size 30m;` for
attachment uploads.
