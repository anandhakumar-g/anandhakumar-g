# Architecture Decision Log — Single Point

Short ADRs. Newest first.

---

## ADR-028 — Household model: multi-flat membership, PRIMARY / SECONDARY
**Decision (MVP-6):** a `user_tenant_membership` is now the link between a user and **a flat in
a community**, not just a community. `V21` swaps the `(user_id, tenant_id)` active-unique index
for `(user_id, flat_id)` (plus a flat-less-request guard and a one-PRIMARY-per-flat index), and
adds `household_role` (`PRIMARY` | `SECONDARY`, default PRIMARY) + `invited_by_user_id`. One
person can therefore hold flats in several communities, or several flats in one. The first
person attached to a flat (admin invite / admin approval) is PRIMARY; a PRIMARY issues a
`HOUSEHOLD` invite code (`invite_code.kind`) that joins family members as SECONDARY with **no
admin step**. SECONDARY members raise/track their own requests and redeem offers but cannot
manage the roster (`POST /me/household/invites`, `.../members/{id}/remove` are PRIMARY-only).
`TicketService.raise` now **requires `flatId`** once the caller holds a flat membership in the
active community, and it must be one of theirs. Endpoints: `GET /me/flats`,
`GET /me/household/{flatId}/members`, `POST /me/household/invites`,
`POST /me/household/{flatId}/members/{userId}/remove`. Deferred: a PRIMARY seeing every
ticket raised for their flat (each member still sees only their own).

## ADR-027 — Direct-to-Provider mode
**Decision (MVP-6):** `ticket.request_mode` (a seam since MVP-1) goes live.
`tenant.direct_service_enabled` (`V20`, default off; set by the Super Admin **and** the
community admin) opts a community in. A resident then gets `GET /api/v1/providers` — a
contact-free directory (id, name, category label, rating, tier, availability) reusing
`ProviderService.directoryForTenant(tenantId, "rating")` — and may pass `providerId` to
`POST /api/v1/tickets`. `TicketService.raise` runs the unchanged `requireAssignableProvider`
gate (verified + active + `DIRECTORY_LISTING` + not lapsed + active enrolment in that
community → `422`), sets `request_mode = DIRECT_SERVICE`, `assigned_provider_id`,
`allocation_approved_by_resident = true`, and `status = ASSIGNED` directly (raise never
asserts transitions), records a `null → ASSIGNED` RESIDENT history line, notifies the
provider, and sends the admin an FYI. Direct bookings still count against
`TICKETS_PER_MONTH`. If the provider **declines**, the resident re-picks:
`POST /api/v1/tickets/{id}/rebook {providerId}` via a new RESIDENT `REJECTED → ASSIGNED`
edge; the admin's own `REJECTED → ASSIGNED` / `→ PENDING_RESIDENT_APPROVAL` edges are
untouched. A community running both `direct_service_enabled` and `require_allocation_approval`:
a direct booking still goes straight to `ASSIGNED` (the resident already chose). Gating is by
tenant flag only — a paid `DIRECT_SERVICE` entitlement is deferred. Community-less
"individual service users" who book a provider with no community at all are **MVP-7**.

## ADR-026 — User portability: explicit switch-community + session re-mint
**Decision (MVP-6):** `app_user.current_tenant_id` was write-once for residents. It becomes a
*user-driven* switch: `POST /api/v1/me/active-community {tenantId}` asserts an ACTIVE
`user_tenant_membership`, sets `current_tenant_id`, and returns a freshly-minted
`SessionResponse` (reusing `AuthService.refreshSessionFor` / `SessionResponse.from`). A new
`POST /api/v1/auth/refresh` re-mints the caller's session with no body — this also fixes the
edge where an admin-approved resident kept an unscoped token until re-login. Joining a 2nd
community does **not** auto-switch. `POST /api/v1/me/memberships/{tenantId}/leave` EXITs every
ACTIVE membership the caller holds there (+ `exited_at`), clears their flat occupant/owner
links, and falls back to another ACTIVE membership or onboarding `NEEDS_COMMUNITY`.
`resolveActiveTenant`'s fallback is now deterministically ordered (`joined_at`, then
`created_at`). No migration. Multi-community **admins** are MVP-7.

## ADR-025 — PII: encrypt free-text at rest, defer names and key rotation
**Decision (MVP-5):** extend the MVP-1 `@Convert(EncryptedStringConverter)` pattern to
`ticket.description` / `resolution_notes` / `rating_comment` / `service_landmark`,
`flat.address_text` and `tenant.address` (new `*_enc` columns; entity points at the encrypted
one; `ticket.description` loses `NOT NULL`). New writes are ciphertext immediately;
pre-existing plaintext is moved by `PiiBackfillRunner` — a boot-time `ApplicationRunner`
guarded by `sp.pii.backfill.enabled`, wildcard-scoped, batched, idempotent (`enc IS NULL`) —
run once per environment then turned off. Plaintext columns are kept this release and dropped
in a later migration once every environment is confirmed backfilled.
**Deferred — name encryption:** `app_user.name` / `service_provider.name` stay plaintext;
`TenantRepository.search` does DB `ORDER BY name` + `LIKE`, and directory/name lookups aren't
fully audited, so encrypting names needs an in-memory sort/search story or a `name_hash`
prefix scheme — its own project.
**Deferred — key rotation:** the AES envelope already carries a version byte (pinned to `1`).
The design is a small keyring, `decrypt` accepting `{1,2}`, and a `ReEncryptJob` re-wrapping
`v1 → v2`; not implemented.
Also: service address + geo are revealed in `TicketView` only to the raiser, the community
admin, or the assigned provider while the job is live (mirrors the phone-reveal rule);
`@AuditRead` + a second `AuditAspect` `@Around` record contact/location reads into
`audit_log` with `entity_type` / `entity_id` populated (applied to `GET /tickets/{id}` and the
KYC file downloads).

## ADR-024 — Provider directory management & availability are additive; availability is advisory
**Decision (MVP-5):** an admin can edit a provider enrolled in their community
(`PUT /api/v1/admin/providers/{id}` — name/email/area/category; a phone change re-hashes and
is collision-checked) and remove/restore it from **that community's** directory
(`tenant_service_provider.active`, never the global `service_provider.active`); a provider
self-serves contact email, service area and availability (`GET/PUT /api/v1/provider/profile`)
but not its name or phone (phone is the identity anchor + unique hash). `service_provider`
gains `availability` (`AVAILABLE` / `BUSY` / `AWAY`) + `availability_note`, and `app_user`
gains `away_until`. Availability is **surfaced, not enforced** — an `AWAY` provider can still
be assigned (a small community may have no alternative); a *deactivated* enrolment, however,
now blocks assignment (`requireAssignableProvider` requires an active enrolment).
Resident `away_until` is informational — shown on the raiser card only.

## ADR-023 — WhatsApp is an additive delivery channel gated by a plan entitlement
**Decision (MVP-5):** `OutboxDispatcher` keeps its push path unchanged and, for **ticket**
notifications only, *additionally* sends over WhatsApp when the community's plan carries the
`WHATSAPP_NOTIFICATIONS` entitlement **and** the recipient has opted in
(`notification_preference.whatsapp_enabled`, opt-in default). Because `SubscriptionPlan.limitFor`
treats an absent key as `-1` (entitled), a plan that excludes the feature carries an explicit
`0` (V16: `TENANT_FREE` = 0, `STANDARD`/`PLUS` = -1; PROVIDER plans untouched — the subject
is `TENANT`). `WhatsAppSender` is a profile split — `LoggingWhatsAppSender` (`!cloud`) /
`MetaCloudWhatsAppSender` (`cloud`, Meta Cloud API, plain REST) — matching the payment
gateway, so the `test` profile can never reach a live API. `notification.channel` already
allowed `WHATSAPP`; `record(...)` now takes the channel. Promo/offer notifications are
push-only this MVP.

## ADR-022 — Provider ratings aggregate onto the global provider row
**Decision (MVP-5):** `service_provider` gains `rating_avg` (`numeric(3,2)`) + `rating_count`,
denormalised like `tier` (global, no RLS). `TicketService.close` recomputes them from the
provider's rated tickets (`TicketRepository.ratingAggregate`) on the **resident close** path
only — `TicketAutoCloseJob` captures no rating. `ProviderService.directoryForTenant` takes an
optional `sort` key (`FEATURED` first, then `rating` desc with unrated last when
`?sort=rating`, else name); the rating is surfaced in the admin provider list, the assigned
provider on a `TicketView`, and the Super Admin provider list.

## ADR-021 — Ticket-category management is configurable per community
**Decision (MVP-5):** a platform-wide catalogue (`category.tenant_id IS NULL`) always exists.
`tenant.category_admin` (`SUPER_ADMIN` default, or `COMMUNITY`) — set by the Super Admin at/
after onboarding — decides who curates a community's own categories. Super Admin manages both
scopes (`/api/v1/superadmin/ticket-categories?tenantId=`); a community admin manages only its
own list (`/api/v1/admin/ticket-categories`) and only when `category_admin = COMMUNITY` (else
`403`); global rows are never reachable from the community editor (`404`). `GET /categories`
returns the global set plus the caller's community's own; `TicketService.raise` accepts only
an active in-scope category (other-tenant / inactive / unknown → `404`). Mirrors the MVP-3
vendor-taxonomy admin, with a thin `TicketCategoryAdminService` shared by the two controllers.

## ADR-020 — SLA breach is alert-and-flag only
**Decision (MVP-5):** `ticket.sla_due_at` (already set at raise from `category.sla_hours`)
gains a companion `sla_breached_at`. `SlaBreachJob` (`@Scheduled`, wildcard-scoped, `:05` so it
never shares a minute with the auto-close job at `:15`) flags any still-open ticket found past
its due time **once** and publishes `TICKET_SLA_BREACHED` to the community admins + the
assigned provider. No status change, no priority bump, no rerouting — auto-escalation is a
later concern.

## ADR-019 — Resident approval-of-allocation is an explicit parked state
**Decision (MVP-5):** when `tenant.require_allocation_approval` is on, `TicketService.assign`
parks the ticket in a new `PENDING_RESIDENT_APPROVAL` status (V11 widens the status columns
to `varchar(30)` and rebuilds the CHECK) and does **not** notify the provider. The resident
approves (`POST /tickets/{id}/allocation/approve` → `ASSIGNED`, provider engaged now) or
declines with a required reason (`.../allocation/reject` → back to `ACKNOWLEDGED`, admins
notified). A reroute while parked re-parks. Reassignment already clears
`allocation_approved_by_resident`. The gate is toggled via a new sparse tenant-update path
(`PUT /api/v1/superadmin/tenants/{id}` — all fields incl. `category_admin`;
`PUT /api/v1/admin/community-settings` — reopen window + the gate only). Also: reroute,
provider decline and `ON_HOLD` now require a non-blank reason, and the timeline API carries
the actor's name.

## ADR-018 — Featured vendor tier is a manual Super-Admin toggle
**Decision (MVP-4):** the platform's paid placement is a per-provider `service_provider.tier`
flag (`STANDARD` / `FEATURED`), set only by a Super Admin (`POST
/api/v1/superadmin/providers/{id}/tier`). It is **not** attached to a subscription plan —
featuring is an editorial/commercial decision made case by case. `ProviderService`
`directoryForTenant` and `OfferService.feedForResident` sort `FEATURED` ahead of the rest
(then by name / feed order); nothing else changes. Automating it (plan-driven, auction, etc.)
is deferred.

## ADR-017 — Monetization: subscription plans + real entitlement enforcement, billed through the payment gateway
**Decision (MVP-4):** `com.singlepoint.billing` owns `subscription_plan` / `subscription` /
`subscription_invoice` — app-scoped, **no RLS** (a tenant admin reads its own by `subject_id`,
a provider its own, Super Admin all), consistent with [ADR-011]. A subject (TENANT or
PROVIDER) with no `subscription` row resolves to its target's `is_default` FREE plan — **zero
migration data**, nothing that worked before breaks. `entitlements` is a JSON map
`{FEATURE: limit}` with sentinels `-1` unlimited / `0` disabled / positive = per-calendar-month
cap. FREE tiers carry deliberately generous caps (V10) and core ticketing
(`TICKETS_PER_MONTH`) is never metered on any tier.

`EntitlementService` (was a no-op seam since MVP-1) now reads the effective plan and enforces:
`requireWithinQuota(...)` → `402 SP-402-QUOTA`; `assertWritable(...)` → `402 SP-402-SUBSCRIPTION`
once a paid plan has lapsed past its grace window. Guards sit in `TicketService.raise`
(`TICKETS_PER_MONTH`), `SuperAdminController.createAdmin` (`ADMIN_SEATS`),
`OfferService.createDraft` (`OFFERS_PER_MONTH`, subject = provider or tenant), and provider
assignability / directory visibility (`DIRECTORY_LISTING` + not lapsed).

Billing mechanism reuses the MVP-3 `PaymentGateway`: assigning a paid plan (Super Admin or
self-serve `POST /api/v1/me/billing/plan`) creates the subscription `PAST_DUE` with
`grace_until = now + sp.billing.grace-days` (7) and a `DUE` `subscription_invoice` carrying a
one-off pay link. `PaymentService.handleWebhook` gained a `WebhookFallback` seam (interface in
`payment.gateway`, implemented by `BillingService` — one-way dependency, no cycle): when a
callback's gateway ref is not a ticket payment, the fallback settles the matching invoice and
flips the subscription to `ACTIVE` with fresh period dates. Super Admin can also `comp` a
subscription (→ `COMPED`, no invoice) or `mark-paid` an invoice out of band. `BillingRenewalJob`
(`@Scheduled`, wildcard tenant scope) rolls `ACTIVE`/`COMPED` past `current_period_end` into
the next period (`COMPED` renews free; paid → `PAST_DUE` + new invoice + grace) and expires
`PAST_DUE` past `grace_until` (→ `EXPIRED`, writes blocked, reads still fine). No recurring
auto-charge, proration, or dunning yet.

## ADR-016 — Vendor-category "kinds" are data
**Decision (MVP-3, pulled from MVP-7):** `vendor_category.kind` is a free-text code, and the
`CHECK` constraint is replaced by a `vendor_category_kind` lookup table. Super Admin can open
new verticals and categories at runtime via `SuperAdminTaxonomyController`; deactivation is
soft (rows already referencing a category keep it, it just leaves the pickers). New categories
are data, no migration (blueprint 4.17).

## ADR-015 — Payments module: loosely coupled, gateway-abstracted, receipts immutable
**Decision (MVP-3):** `com.singlepoint.payment` owns `ticket_payment` / `payment_receipt`
(append-only trigger) / `payment_event`, all RLS-scoped like the ticket child tables. It
references `ticket_id` and **never mutates `ticket.status`** (blueprint 4.6). Online payment
goes through a `PaymentGateway` interface — `StubGateway` locally (with a `/dev/pay` page that
self-posts a signed synthetic webhook) and `RazorpayGateway` on the cloud profile (Payment
Links + `X-Razorpay-Signature` HMAC, no SDK). Cash uses the existing `OtpService`
(`CASH_PAYMENT` purpose) — the OTP is sent to the resident and either the resident or the
assigned provider may submit it (blueprint 4.9). Webhook handling is idempotent. Receipts are
a structured record + a plain-text `shareText`; no PDF (deferred). Saved payment methods
(`PaymentMethodToken`) skipped — a payment-link flow doesn't need stored tokens.

## ADR-014 — Offers module: own tables, no RLS, Super-Admin-gated
**Decision (MVP-2):** `offer` / `offer_target` / `offer_redemption` live in their own
`com.singlepoint.offer` package and share only the provider directory, tenant list and
notification service with the core. No RLS — offers are cross-tenant by nature; visibility is
computed in the query layer from `offer_target` + the requesting user's tenant / enquiry
history. Every offer passes through `PENDING_APPROVAL` and only a Super Admin can approve
(optionally rewriting the target audience) or reject. `OfferTargetingService` resolves
SINGLE_TENANT / TENANT_LIST / ALL_TENANTS / USER_SEGMENT / ENQUIRY_BASED to a recipient set at
approval time (under the Super Admin's cross-tenant DB scope), then publishes one promo event.

## ADR-013 — Anti-fatigue is opt-in by category
**Decision (MVP-2):** `notification_preference` defaults to **no** subscribed vendor
categories, so promotional pushes are suppressed until the resident actively subscribes; the
in-app deals tab is always visible. `OutboxDispatcher` gates each promo recipient on:
opt-out, category subscription, a central weekly frequency cap (counts SENT `OFFER_*`
notifications in the last 7 days), and digest deferral. Ticket-status notifications are gated
**only** by `ticket_notifications_enabled` — muting promos never mutes ticket updates.

## ADR-012 — KYC verification gate + private storage
**Decision (MVP-2):** setting a provider to `VERIFIED` now requires an ACCEPTED `GOV_ID` and
`ADDRESS_PROOF` (plus `COMPANY_REG` for companies), enforced in `ProviderService.setVerification`
(`422 SP-422-KYC`). KYC files use `StorageService.putPrivate` (local `private/` prefix; the
separate KYC bucket in cloud) and are only ever streamed back through an auth-gated endpoint
to the owning provider or an admin of a tenant the provider is enrolled in — never a public URL.

## ADR-011 — RLS scope narrowed to the ticket domain
**Context:** `user_tenant_membership` and `tenant_service_provider` are read across tenant
boundaries during login / onboarding / portability (before the caller has any tenant scope),
which fought the per-connection GUC model.
**Decision:** RLS is enforced on `flat`, `invite_code`, `ticket`, `ticket_attachment`,
`ticket_status_history` only. `user_tenant_membership` and `tenant_service_provider` are
scoped at the application layer — every query filters by `user_id` / `provider_id` / the
caller's own `tenant_id`. `app_user`, `otp_challenge`, `device_token`, `notification*`,
`category`, `vendor_category`, `service_provider`, `audit_log` were never tenant-scoped.
(Migrations V2 + V4.)

## ADR-010 — Lombok for entity/DTO accessors
**Context:** the plan mirrored the DCC project's "no Lombok in production" rule. MVP-1 has ~18
entities and many DTOs.
**Decision:** use Lombok (`@Getter/@Setter/@Builder/@NoArgsConstructor/@AllArgsConstructor`)
for entities and DTOs. Hand-writing accessors at this scale is a false economy and adds review
noise. Lombok is excluded from the repackaged jar. Business/service/controller classes stay
plain with constructor injection, as in DCC.

## ADR-009 — Local dev + tests use a running PostgreSQL, not Docker
**Context:** the build machine has PostgreSQL 18 on `localhost:5433` but no Docker daemon.
**Decision:** local + test profiles target the local PostgreSQL instance (databases
`singlepoint`, `singlepoint_test`). Integration tests (`*IT`) are full-stack HTTP tests
(`@SpringBootTest(RANDOM_PORT)` + `TestRestTemplate`) against `singlepoint_test`; the base
class runs Flyway once then `TRUNCATE … RESTART IDENTITY CASCADE` + reseeds a minimal taxonomy
before each test (fast, no DDL-lock deadlocks). `docker-compose.yml` (Postgres + MinIO) is
kept for Docker-capable environments / CI, where Testcontainers can wrap the same tests.
**Consequence:** RLS, triggers and the tenant-GUC datasource are all exercised for real;
developers need the local instance (`db/bootstrap.sql`) or Docker.

## ADR-008 — Object storage abstraction: local filesystem in dev, S3 in cloud
**Context:** no Docker locally ⇒ no MinIO. Attachments still need somewhere to live.
**Decision:** `StorageService` interface with `LocalFsStorageService` (`@Profile("local"|"test")`,
writes under `./local-attachments`, "presign" returns an app upload/download URL) and
`S3StorageService` (`@Profile("cloud")`, real S3 presigned URLs). Same API to callers.

## ADR-007 — Tenant identity from the JWT claim, enforced by RLS
**Decision:** the authenticated JWT carries a `tenantId` claim. A servlet filter puts it in a
`TenantContext` (request-scoped); a JPA transaction listener issues
`SET LOCAL app.current_tenant_id = '<uuid>'` so PostgreSQL RLS policies
(`USING (tenant_id = current_setting('app.current_tenant_id')::uuid)`) enforce isolation at the
database, not just the app layer. Deviates from the DCC project's spoofable `X-Tenant-ID`
header. Super Admin endpoints run without the GUC and are limited to non-ticket tables.

## ADR-006 — UUID primary keys
**Decision:** all PKs are `uuid` (`gen_random_uuid()`), not `bigserial`. Rationale:
multi-tenant, planned user portability across tenants, non-enumerable IDs. Deviates from the
DCC project's `Long IDENTITY`.

## ADR-005 — Feature-first package layout
**Decision:** top-level packages are features (`ticket`, `auth`, `provider`, …), each holding
its own controller/service/entity/repository/dto, rather than the DCC project's layer-first
(`controller/`, `service/`, …). The domain is much larger and maps cleanly to MVP boundaries.

## ADR-004 — PII field encryption via JPA AttributeConverter
**Decision:** `phone`, `email`, `contact_phone` are stored encrypted (AES-256-GCM) through a
JPA `AttributeConverter` (`@Convert`). A keyed HMAC-SHA256 `phone_hash` column carries the
uniqueness constraint and login lookup without decryption. Deviates from the DCC project's
service-layer crypto calls — a converter is cleaner for simple symmetric field crypto spread
across many entities. A `CryptoService` facade still owns key management.

## ADR-003 — Notifications via a transactional outbox
**Decision:** business code never calls the push/SMS provider inline. Every domain event
(ticket raised, assigned, status change, rerouted, resolved, reopened, closed) writes a
`notification_outbox` row in the same transaction as the state change. An `OutboxPoller`
delivers with retry/backoff via a `PushSender` (Expo push in MVP-1; FCM behind the same
interface) and an `SmsSender` (logging stub in MVP-1). WhatsApp is added in MVP-5 as another
sender.

## ADR-002 — Immutable status history + audit log
**Decision:** `ticket_status_history` and `audit_log` are append-only. A `BEFORE UPDATE OR
DELETE` trigger raises an exception; the app role is granted only `INSERT`/`SELECT`.

## ADR-001 — Stack
**Decision:** Spring Boot 2.7.18 / Java 17 / PostgreSQL backend (mirrors the team's existing
DCC middleware stack and conventions); Expo / React Native / TypeScript mobile app (matches
the team's existing `myforex-demo` Expo project). Flutter was the blueprint's suggestion but
Expo was chosen for toolchain continuity.

---

## Open decisions (defaults chosen for MVP-1, revisit as noted)

| # | Question | MVP-1 default | Revisit |
|---|---|---|---|
| 1 | Object storage in dev | Local filesystem (ADR-008) | When cloud deploy is set up |
| 2 | Push transport | Expo push (no Firebase project yet); FCM behind same `PushSender` | When Firebase credentials exist |
| 3 | OTP/SMS provider | Logging stub (code in server log) | Before any non-dev release — pick MSG91 / Gupshup / Twilio Verify |
| 4 | Reopen window + auto-close | 72h, stored `tenant.reopen_window_hours`; scheduled job auto-closes RESOLVED tickets past the window (with a notification) | With real usage data |
| 5 | Resident approval-of-allocation | Informational only (no gate) | MVP-5 makes it a tenant-configurable hard gate |
| 6 | Naming | backend `com.singlepoint` / `single-point-api`; app "Single Point" / `com.singlepoint.app` | — |
| 7 | First Super Admin bootstrap | Seeded by migration `V4` (local) / `sp.bootstrap.super-admin-phone` env (cloud) | When tenant self-onboarding lands (MVP-7) |
| 8 | Promotional notification subscription default | (MVP-2) — leaning opt-in by category | MVP-2 |
