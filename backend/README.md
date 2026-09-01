# Single Point — Backend (MVP-1)

Spring Boot 2.7 · Java 17 · PostgreSQL · Flyway · JWT/OTP auth · Row-Level Security

## Run locally (no Docker)

Prereqs: JDK 17, Maven 3.9+, a PostgreSQL 14+ instance on `localhost:5433`
(the dev default expects superuser `postgres` / `POSTGRES` to create the app role).

```bash
# 1. one-time: create databases + the non-superuser app role
psql -h localhost -p 5433 -U postgres -c "CREATE DATABASE singlepoint;"
psql -h localhost -p 5433 -U postgres -c "CREATE DATABASE singlepoint_test;"
psql -h localhost -p 5433 -U postgres -d singlepoint      -f db/bootstrap.sql
psql -h localhost -p 5433 -U postgres -d singlepoint_test -f db/bootstrap.sql

# 2. run (Flyway migrates; demo data is seeded on first start)
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

- API base: `http://localhost:18080/api/v1`  (ports 18080/18081 to avoid local collisions)
- Swagger UI: `http://localhost:18080/swagger-ui.html`
- Health: `http://localhost:18081/actuator/health`
- **Dev OTP codes are printed to `logs/app.log`** — no SMS provider needed. `POST /auth/otp/request`
  also returns the code in `devCode` while `sp.otp.dev-mode=true`.

### Demo data (local profile, first start)

| Who | Phone | Notes |
|---|---|---|
| Super Admin | `+919000000000` | platform owner |
| Green Meadows admin | `+919000000101` | tenant admin; invite code printed in the startup banner |
| Lakeview Residency admin | `+919000000201` | second tenant (isolation testing) |
| Sparky Electricals | `+919000000301` | VERIFIED provider, enrolled in Green Meadows |

Reset local data: `psql ... -d singlepoint -f db/reset-local.sql` then restart.

## Layout (feature-first)

```
com.singlepoint
├── common        BaseEntity / CreatedOnlyEntity, error model (AppException, GlobalExceptionHandler), PageResponse
├── config        TenantAwareDataSource (sets app.current_tenant_id per connection), OpenAPI
├── security      JwtService, JwtAuthFilter, TenantContext, TenantScopedExecutor, SecurityConfig
├── crypto        AES-GCM field encryption + HMAC lookup hash, JPA AttributeConverter
├── auth          OTP login, SmsSender (LoggingSmsSender)
├── tenant        Tenant, Super Admin onboarding, community lookup
├── user          AppUser, UserTenantMembership, join flow + admin approval queue, /me
├── flat          Flat register, invite codes
├── category      global ticket + vendor category taxonomy (read-only in MVP-1)
├── provider      ServiceProvider directory + manual verification, tenant enrolment
├── ticket        Ticket, state machine, transitions, attachments, timeline, auto-close job
├── notification  transactional outbox + poller, PushSender (noop / Expo)
├── storage       StorageService (local filesystem in dev, S3 in cloud)
├── entitlement   always-entitled seam (MVP-4 flips this)
└── audit         AuditAspect -> append-only audit_log
```

## Multi-tenant isolation

`tenant_id` on every tenant-scoped table + PostgreSQL **RLS** on `flat`, `invite_code`,
`ticket`, `ticket_attachment`, `ticket_status_history`. The app connects as a non-superuser
role and sets `SET app.current_tenant_id = '<uuid>'` (or `'*'` for Super Admin / system jobs)
per pooled connection; policies fail closed when it is unset. See `docs/decisions.md`.

## Tests

```bash
mvn test          # integration tests against localhost:5433/singlepoint_test
```

A Postman collection lives at `../docs/Single-Point-MVP1.postman_collection.json`.
