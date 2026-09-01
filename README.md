# Single Point

Multi-tenant mobile platform for gated communities / apartment complexes to raise, route,
track, and resolve tickets — and, in later phases, a verified-vendor marketplace with offers.

One app codebase serves **many communities** ("tenants"), each isolated by `tenant_id` +
PostgreSQL Row-Level Security.

Built **MVP by MVP** — see [`docs/roadmap.md`](docs/roadmap.md). This repo currently
implements **MVP-1 (Foundation)**.

---

## Repo layout

| Path | What |
|---|---|
| `backend/` | Spring Boot 2.7 / Java 17 / PostgreSQL API |
| `mobile/` | Expo / React Native (TypeScript) app — resident, admin, provider roles |
| `admin-web/` | Placeholder — dedicated React admin console arrives in MVP-2 |
| `docs/` | `openapi.yaml`, `data-model.md`, `decisions.md`, `roadmap.md`, Postman collection |

---

## MVP-1 scope

Resident onboarding (phone + OTP, join community/flat by invite code or admin approval) →
raise a ticket with photo + service location → admin triage (resolve / assign / reroute) →
manually-verified service provider accepts and drives to resolution → resident sees every
status change (push) and closes or reopens. Multi-tenant isolation enforced by RLS + RBAC.

**Not in MVP-1:** payments, vendor marketplace / offers, WhatsApp, admin category CRUD,
availability scheduling, SLA alerting, user portability, direct-to-provider mode,
subscriptions. Data-model columns and service seams for these are pre-placed where cheap.

---

## Quick start (local, no Docker required)

Prereqs: JDK 17, Maven 3.9+, Node 20+, and a local PostgreSQL 16+ on `localhost:5433`
(user `postgres` / password `POSTGRES`). Databases `singlepoint` and `singlepoint_test`.

```bash
# create databases (once)
psql -h localhost -p 5433 -U postgres -c "CREATE DATABASE singlepoint;"
psql -h localhost -p 5433 -U postgres -c "CREATE DATABASE singlepoint_test;"

# backend
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
# API at http://localhost:8080/api/v1  ·  Swagger UI at http://localhost:8080/swagger-ui.html

# mobile (separate terminal)
cd mobile
npm install
npx expo start
```

Dev OTP codes are printed to the backend log (`logs/app.log`) — no SMS provider needed locally.

A `docker-compose.yml` (Postgres + MinIO) is provided for environments that have Docker, but
it is not required for local development.

See [`backend/README.md`](backend/README.md) and [`mobile/README.md`](mobile/README.md) for detail.
