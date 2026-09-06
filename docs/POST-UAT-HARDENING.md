# Post-UAT hardening backlog

Items deferred by decision until **UAT sign-off**, then done together as one hardening
sprint. Nothing here changes functional behaviour; all of it is dependency / platform /
process work.

Status: **queued** — do not start until UAT is complete.

---

## A. Dependency & vulnerability scanning in CI

**Why.** There is currently no automated scanning. `.github/` has no workflows. A new CVE in
any dependency is invisible until someone runs `npm audit` / `mvn` by hand. Console `npm
audit` is clean today; mobile shows 14 *moderate* advisories, all in the Expo build
toolchain (no high/critical, nothing in app runtime); backend has never been scanned.

**Scope.**

1. **GitHub Dependabot** — `.github/dependabot.yml` for three ecosystems:
   - `maven` on `backend/` (weekly)
   - `npm` on `admin-web/` and `mobile/` (weekly, grouped, ignore Expo canary tags)
   - `github-actions` (monthly)
2. **A `security` GitHub Actions workflow** (`.github/workflows/security.yml`) on PR + a
   weekly schedule:
   - `npm audit --audit-level=high` in `admin-web/` and `mobile/` (fail on high+).
   - **OWASP Dependency-Check** (or `mvn org.owasp:dependency-check-maven:check`) on the
     backend, fail on CVSS ≥ 7, with a suppression file for accepted findings.
   - **CodeQL** for `java` and `javascript-typescript`.
3. **`gitleaks`** (or GitHub secret scanning) on push — catch committed keys.
4. Wire the results into the PR status checks; triage weekly.

**Effort:** ~0.5–1 day. Low risk (CI only). No code change.

**Acceptance:** a red CI check on any new high/critical advisory; a documented, dated
suppression list for anything intentionally accepted.

---

## B. Spring Boot 2.7 → 3.x upgrade

**Why.** Spring Boot **2.7 is end of life** — OSS support ended Nov 2023, commercial Aug
2025. `2.7.18` receives no security patches; Spring Framework 5.3.x, Spring Security 5.7.x
and the bundled Tomcat 9.0.83 are all frozen. This is the single largest platform risk.

**Target.** The current Spring Boot 3.x line (3.3+/3.4+). **Java 17 stays** (3.x baseline).
This is a `javax` → `jakarta` namespace migration plus a Spring Security 6 config rewrite.

**Known work.**

| Area | Change |
|---|---|
| Namespace | `javax.persistence.*` → `jakarta.persistence.*`, `javax.validation.*` → `jakarta.validation.*`, `javax.servlet.*` → `jakarta.servlet.*` across all entities, DTOs, filters, config. Use the OpenRewrite `UpgradeSpringBoot_3` recipe as the first pass. |
| Spring Security | `WebSecurityConfigurerAdapter` is gone → the lambda `SecurityFilterChain` bean style (already partly there); re-check `StrictHttpFirewall`, the CORS source, and the custom `JwtAuthFilter` ordering. |
| Hibernate 6 | dialect auto-detected (drop the explicit `hibernate.dialect`); check `@Type`/`@Convert` on the `EncryptedStringConverter` columns; verify native queries in `AnalyticsRepository` and `AuditLogRepository` still bind (`CAST(:p AS ...)` patterns). |
| Flyway | 9.x → 10.x; confirm the `baseline-on-migrate` + `clean-disabled` config and that `V1..V36` replay clean on a fresh DB. |
| jjwt | 0.11.5 → 0.12.x (API changed — `parserBuilder()` → `parser()`, `SignatureAlgorithm` → `Jwts.SIG`). Isolated to `JwtService`. |
| springdoc | `springdoc-openapi-ui` 1.x → `springdoc-openapi-starter-webmvc-ui` 2.x. Regenerate `docs/openapi.yaml` and diff. |
| Actuator | endpoint paths/props are stable; re-verify `/actuator/prometheus` and the health probes. |
| AWS SDK v2 | bump to the current 2.3x; API-compatible. |
| Tests | `IntegrationTestBase` (`@SpringBootTest`), `TestRestTemplate` (still no Apache HttpClient → keep using PUT not PATCH), the RLS `TenantAwareDataSource` GUC hook — run the full 183-test suite and the known `resetSchema` TRUNCATE-deadlock flake discipline applies. |
| Build | `spring-boot-maven-plugin` 3.x; confirm the fat jar + the `@Profile("cloud")` beans still resolve. |

**Effort:** ~3–5 focused days including a full regression pass. Medium risk — the namespace
migration is mechanical but broad; Spring Security 6 and Hibernate 6 are where surprises
live.

**Sequencing.** Do this **after** A (so the upgrade PR runs through the new scanners) and on
its own branch with the full suite green before merge. Consider bumping Java to 21 (LTS) in
the same window — optional, low marginal cost once on Boot 3.

**Acceptance:** Boot 3.x, `javax` gone, 183+ tests green, `docs/openapi.yaml` regenerated,
a new ADR recording the jump, and Dependabot showing the framework as current.

---

## C. Smaller items to fold in

- **Global API rate limiting** — today only OTP and broadcasts are throttled. Add a coarse
  per-IP / per-token limit (bucket4j or a gateway rule).
- **Security headers on the API** — `Strict-Transport-Security`, `X-Content-Type-Options`,
  `Referrer-Policy` (nginx or a Spring filter).
- **Containerise UAT/prod** — a `Dockerfile` (distroless JRE 17/21) + `docker-compose` for
  API + Postgres, so environments stop being hand-built VMs.
- **Pin mobile store targets explicitly** — add `android.compileSdkVersion` /
  `targetSdkVersion` to `app.json` and verify against Google Play's current minimum at build
  time (Expo's defaults are compliant but make it explicit).
- **Penetration test** — schedule an external test once Boot 3 lands and the real
  payment / SMS integrations are in.
- **Secret rotation runbook** — document a cadence for `JWT_SECRET` and the crypto keys
  (the mechanism exists — ADR-040 — the policy doesn't).
