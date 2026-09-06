# Architecture Decision Log — Single Point

Short ADRs. Newest first.

---

## ADR-039 — Reporting exports, console parity, ops observability, mobile Screen contract
**Decision (MVP-12):**

- **CSV exports** (`com.singlepoint.export`). A hand-rolled `CsvWriter` (RFC-4180: `\r\n`,
  a UTF-8 BOM so Excel reads accents, a field quoted only when it holds `, " CR LF`, inner
  `"` doubled) and an `ExportService` with one `@Transactional(readOnly)` method per dataset.
  `GET /api/v1/admin/exports/{tickets,payments,members}.csv` (ADMIN / SUPER_ADMIN, scoped to
  `principal.getTenantId()`, 403 `SP-403` "No active community" when acting tenant-less) and
  `GET /api/v1/superadmin/exports/{tickets,offers,communities}.csv` (SUPER_ADMIN,
  platform-wide, tickets also `?tenantId=`). Filters are **date range + status only**
  (`?from=&to=&status=`) — `from`/`to` are ISO-8601 instants bound to the row's created time,
  `status` an exact enum-name match. Responses are `text/csv; charset=utf-8` +
  `Content-Disposition: attachment; filename="<name>-<date>.csv"`. **Buffered, not streamed:**
  `Csv.download` builds the body on the request thread into a `byte[]` rather than returning a
  `StreamingResponseBody`, because the export queries hit RLS-forced tables and depend on the
  `TenantContext` / `UserContext` ThreadLocals `JwtAuthFilter` sets per request — a streaming
  body runs on a separate async thread where that context is absent and every row fails
  closed. Fine at demo scale; `?from=&to=` is the size valve. No migration, no new RLS policy
  (admin exports run under the admin's tenant GUC, Super Admin exports under the wildcard).

- **`<a download>` can't carry a bearer token**, so both the console and Expo-web downloads
  do `fetch(url, {headers})` → `blob` → `URL.createObjectURL` → click a synthetic link
  (`admin-web/src/api.ts` `downloadCsv`, `mobile/src/lib/download.ts`). Native Expo has no
  download path — the row is hidden (`canDownloadCsv = Platform.OS === "web"`); an
  `expo-file-system` + share-sheet follow-up is noted, not built.

- **Console parity** (`admin-web/`). The Super Admin console gains the surfaces ADR-038
  deferred: `/taxonomy` (vendor verticals, vendor categories, global ticket categories —
  list / create / rename / de-reactivate over the existing `/superadmin/vendor-category-kinds`
  · `/vendor-categories` · `/ticket-categories`), `/audit` (filterable paged viewer over
  `/superadmin/audit-logs[/actions]` with a row-detail card), `/broadcasts` (compose
  ALL_ADMINS / ALL_USERS / one community + history over `/superadmin/broadcasts`), `/billing`
  plan create + edit forms (entitlements as key→number rows) over `POST`/`PUT
  /superadmin/plans`, and `/reports` (the exports above). Gate stays `npm run build`.

- **Tenant lifecycle from the console.** `PUT /api/v1/superadmin/tenants/{id}` body gains
  `status` — `TenantService.update` maps it via `TenantStatus.valueOf` and **rejects
  `PENDING_REVIEW`** (400 `SP-400`; that transition is the onboarding flow's alone).
  `GET /api/v1/superadmin/tenants` gains `?status=` (defaults to `ACTIVE`, unchanged when
  omitted) via a `TenantService.search(query, status, pageable)` overload, and `TenantHealth`
  now carries `status`, so `/communities` can list and reactivate SUSPENDED / ARCHIVED rows.
  No migration — `V30` already widened the `tenant.status` CHECK.

- **Ops observability** (`com.singlepoint.observability`). `micrometer-registry-prometheus`
  (runtime) + `/actuator/prometheus` exposed on the management port (18081), tagged
  `application="single-point"`. Boot's `@ConditionalOnEnabledMetricsExport` default wasn't
  resolving in this build, so `management.metrics.export.prometheus.enabled=true` /
  `management.endpoint.prometheus.enabled=true` are set explicitly. `AppMetrics` registers
  four domain counters — `sp.tickets.raised`, `sp.offers.redeemed`, `sp.broadcasts.sent`,
  `sp.communities.self_onboarded` — incremented in `TicketService.raise`, `OfferService.redeem`,
  `BroadcastService.send`, `OnboardingService.approve`. `docs/ops-runbook.md` documents the
  Prometheus scrape config, a starter Grafana panel + alert list, and the scheduled
  `pg_dump` → S3 lifecycle + restore-drill procedure — **documentation, not automation**; the
  only shipped code is the registry, the config, and the four counters.

- **Mobile `Screen` contract** (`Themed.Screen`). `Screen` now owns safe-area padding — the
  tab-bar / home-indicator inset is added to the scroll content's bottom padding (top was
  already handled by `SafeAreaView edges`) — and takes optional `loading` / `error` / `empty`
  props that render a centered slot **inside** the frame. Screens that returned a bare
  `<Loading/>` before `<Screen>` (losing the safe area — the pattern in `(provider)/index`)
  now `return <Screen loading />`. New `src/components/Icon.tsx` maps the ~dozen text glyphs
  in use behind one `<Icon name>` so call sites don't change when a real icon font lands;
  adoption is incremental (Home's "Raise a ticket" CTA first). Gate stays `tsc --noEmit`.

---

## ADR-038 — Community self-onboarding, platform analytics, a Super Admin console
**Decision (MVP-11):**

- **Community self-onboarding.** `POST /api/v1/onboarding/community` (any authenticated
  user) creates a `tenant` in a new `PENDING_REVIEW` status (`V30` widens the `tenant.status`
  CHECK, adds `tenant.requested_by_user_id`). `tenant` is the root table — never RLS'd — so a
  pending row is automatically excluded by every ACTIVE-only query (`TenantRepository.search`,
  `tenantHealth`) with no extra code. `GET /api/v1/superadmin/community-requests` is the
  review queue; `POST /.../{tenantId}/approve` flips the tenant to `ACTIVE`, promotes the
  requester **RESIDENT → ADMIN** (`app_user.role` + an `admin_tenant` link +
  `current_tenant_id`), seeds a "Main" `location` (same call BootstrapService makes), and
  notifies them; `POST /.../{tenantId}/reject {reason}` archives it and notifies. The
  requester picks up the new role on their next `/auth/refresh`. Reject reuses
  `TenantStatus.ARCHIVED` (no new `REJECTED` value); the reason lives only in the
  notification. 409 if the caller already has a pending request or already manages a
  community.

- **Platform analytics.** `GET /api/v1/superadmin/analytics?bucket=WEEK|MONTH&points=12`
  (SUPER_ADMIN) → a fixed-length series `{periodStart, ticketsCreated, ticketsResolved,
  offersRedeemed, newUsers, revenue}` + "now" totals `{communities, activeCommunities,
  residents, providers, openTickets, mrr}`. `AnalyticsRepository` runs windowed **native
  projections** (`select created_at ... where created_at >= :since`) and `AnalyticsService`
  buckets them in Java (week = Monday, month = 1st, UTC) — deliberately **no `date_trunc`**,
  so timezone semantics can't drift between the DB and the zip. `bucket` is validated to
  `WEEK`/`MONTH` and `points` clamped `1..52` before any query. Revenue = `SUM(amount)` over
  `subscription_invoice` rows with `status = 'PAID'`. No migration.

- **Super Admin web console** (`admin-web/`). The long-deferred "dedicated React admin
  console" is finally started — a **standalone Vite + React + TypeScript app**, not folded
  into Expo-web, consuming the same `/api/v1/superadmin/*` surface. It is **Super-Admin-only
  and deliberately scoped** to the platform review surfaces: login, a dashboard (the
  analytics above + totals + inline-SVG charts), communities (read-only list + health), the
  self-onboarding approval queue, providers (verify / KYC review / tier), the offer approval
  queue, and billing (subscriptions comp/cancel, invoices mark-paid). **Community-admin work,
  taxonomy/category CRUD, the audit-log viewer, broadcasts, plan CRUD, per-ticket drill-down,
  and offer audience overrides all stay in the Expo `(super)` group** — the Expo app is left
  entirely intact (only a small "Platform trends" tile added). Gate is `npm run build`
  (`tsc --noEmit` + `vite build`); no unit tests, matching the Expo side's `tsc`-only
  discipline.

## ADR-037 — Dashboards, device-bound sessions, paid community-less bookings
**Decision (MVP-10):**

- **Per-role dashboards.** One endpoint, `GET /api/v1/dashboard`, one flexible
  `DashboardDtos.DashboardView` — mirrors `MeResponse`'s existing style of a single shape
  whose fields carry different meaning per role, so the mobile client calls the same
  `dashboard.get()` everywhere. `DashboardService.forPrincipal` branches on
  `principal.getRole()`: RESIDENT gets ticket buckets + `pendingApprovalCount` /
  `resolvedAwaitingCloseCount` / `pendingPaymentCount` (a tenant-agnostic
  `TicketPaymentRepository.findUnsettledForRaiserAcrossTenants`, so it also covers a
  community-less resident once ADR paid-bookings below lands) / `activeOffersCount`
  (reuses `OfferService.feedForResident`, `.size()`); PROVIDER gets `awaitingAcceptCount`
  + rating; ADMIN (tenant-scoped, 403 with no active community) gets `unassignedCount` /
  `slaBreachedCount` / `pendingJoinRequestsCount`; SUPER_ADMIN gets platform-wide totals
  incl. a `communityLessOpenTickets` bucket, providers pending verification, offers
  pending approval, community count. Every new count query mirrors an existing finder in
  the same repository — no new query infrastructure. Mobile: one `DashboardSummary`
  component (self-fetching) mounted on all four existing home screens — no new screens.

- **Device-bound sessions.** A session JWT can carry a `deviceId` claim
  (`JwtService.issue`/`parse`, `AppPrincipal.deviceId`) — no new table, no "known devices"
  registry. `JwtAuthFilter` rejects a request when the token's claim doesn't match the
  `X-Device-Id` header (missing or different) with a new `SP-401-DEVICE`, forcing a fresh
  OTP sign-in on that device, which mints a token bound there instead. A token with **no**
  claim is never gated, so every pre-MVP-10 client and test keeps working unmodified —
  only a client that sends the header opts into the protection. `AuthService.buildSession`
  (the single call site of `issue`) threads `deviceId` through `verifyOtp` and
  `refreshSessionFor`; every re-mint call site (`/auth/profile`, `/auth/refresh`,
  `/me/active-community`, `/me/stop-acting`, `/me/memberships/leave`, community join)
  passes `principal.getDeviceId()` through unchanged. Mobile generates one id per install
  (a binding tag, not a secret — the JWT signature is the real security boundary),
  persists it in `expo-secure-store` (which also now holds the session token itself,
  replacing `AsyncStorage`), and sends it as `X-Device-Id` on every request.

- **Biometric unlock is opt-in, default off**, gated on `hasHardwareAsync() &&
  isEnrolledAsync()`. A lock screen renders in place of the whole app (before any route)
  whenever it's on and a token was restored at boot; a failed attempt offers retry and
  never signs the user out. **OTP autofill uses only built-in React Native `TextInput`
  props** (`textContentType="oneTimeCode"`, `autoComplete="sms-otp"`) — no SMS Retriever
  native module, no extra Android permission.

- **Paid community-less bookings.** `ticket_payment` / `payment_receipt` / `payment_event`
  lose their `tenant_id NOT NULL` (mirrors `ticket`, ADR-034) and gain a null-tenant RLS
  branch (`V29`): `ticket_payment` resolves via its own `charged_by_user_id` or one hop to
  `ticket` (raiser / assigned provider); `payment_receipt` / `payment_event` resolve two
  hops further, through `ticket_payment` into `ticket` — the deepest RLS nesting in the
  schema so far, same "nested policy evaluation, not recursion" category as ADR-034's
  precedent. `PaymentService.requireTicket` tries the tenant-scoped lookup first (zero
  behaviour change for every existing tenant-bound flow) and only falls back to a
  null-tenant path when that comes up empty — necessary because a **PROVIDER's**
  `principal.tenantId` is never null even when the ticket is, so the tenant-scoped lookup
  alone could never reach a community-less ticket. `adjust()` stays ADMIN-only and so
  stays unreachable for a community-less ticket (no admin exists there) — not a
  regression, just unused for that ticket shape.

## ADR-036 — Audit read-model & broadcast announcements
**Decision (MVP-9):**

- **Audit viewer (read-only).** `audit_log` is already written on every state-changing
  admin / super-admin / provider call and on `@AuditRead` PII reads (`AuditAspect`) — MVP-9
  adds no capture, only `GET /api/v1/superadmin/audit-logs` (SUPER_ADMIN). Every filter
  optional (`actorUserId`, `tenantId`, `action` substring, `entityType`, `success`, `from`,
  `to`), paged, `created_at DESC`; actor + community names hydrated per page (two
  `findAllById` batch loads), phone masked. `.../audit-logs/actions` lists distinct actions
  for the filter UI. `V27` adds three read indexes. The filter query is **native** so each
  nullable bind is `CAST(:p AS …)`-anchored — a bare `:p IS NULL` over a JPQL nullable param
  makes PostgreSQL throw *"could not determine data type of parameter"*. `action` stays
  `"<Controller>#<method>"` (no migration to semantic names). No admin-scoped view this MVP.

- **Broadcasts.** New `com.singlepoint.broadcast`. A community **admin** announces to every
  ACTIVE resident of the community they're acting in (`POST /api/v1/admin/broadcasts`,
  scope fixed to `principal.getTenantId()`); the **Super Admin** announces to `ALL_ADMINS`,
  `ALL_USERS`, or a named `COMMUNITY` (`POST /api/v1/superadmin/broadcasts`). One append-only
  `broadcast` row (`V28`, `body` encrypted at rest like ticket free-text) + **one**
  `notification_outbox` row via new `DomainEventPublisher.publishBroadcast`; the existing
  `OutboxPoller` → `OutboxDispatcher` fans it out (push + in-app; **not** digest-deferred,
  **not** WhatsApp). Recipient resolution reuses `UserTenantMembershipRepository
  .findByTenantIdAndStatus(ACTIVE)` / new `AdminTenantRepository.findActiveAdminUserIds` /
  new `AppUserRepository.findAllIds`; the sender is dropped, ids de-duped. `GET` on both
  routes lists past sends (admin: own community only). The `POST` is auto-audited by
  `AuditAspect`.

- **Delivery discriminator.** `enqueue(...)` gains a `kind` written into the outbox payload
  (`"transactional"` | `"promo"` | `"broadcast"`; the old `"promo": boolean` stays for
  back-compat). `OutboxDispatcher.gate()` suppresses a broadcast only when
  `notification_preference.broadcast_enabled` is `false`. **Announcements are opt-out**
  (`broadcast_enabled` default `true`), unlike promos (opt-in by category) — a resident who
  muted *ticket* notifications still gets announcements unless they also turn off the new
  toggle. `GET/PUT /api/v1/me/notification-preferences` carries `broadcastEnabled`.

- **Abuse guard, not a billing meter.** `BroadcastService` enforces a per-sender cooldown
  (`sp.broadcast.min-interval-seconds`, default 60) + a daily cap
  (`sp.broadcast.daily-cap`, default 20), both counted off the `broadcast` table → `SP-429`.
  A `BROADCASTS_PER_MONTH` plan entitlement is deferred. `ALL_USERS` = every `app_user` row
  (one `select u.id` + one outbox row); chunked dispatch for real scale is deferred.

## ADR-035 — Offers depth: individual targeting, cap hardening, feedback
**Decision (MVP-8):**
- **Target a set of communities** (`TENANT_LIST`) was already wired end to end (targeting
  resolution, `describe`, and the resident feed gate) — MVP-8 only adds the mobile pickers.
- **Target named individuals** (`USER_LIST`, new): `V25` widens the `offer_target.target_type`
  CHECK and adds `offer_target.user_ids` (CSV, mirrors `tenant_ids`). `OfferDtos.TargetRequest`
  gains `phones` (and `userIds`); `OfferService.upsertTarget` resolves phones via
  `CryptoService.lookupHash` + `AppUserRepository.findByPhoneHash`, unions with any explicit
  ids, and `422`s when nothing resolves. Resolution happens at submit / approval time and the
  ids are frozen — a later signup is not retro-added. `resolveRecipients` / `describe` /
  `feedForResident.isTargeted` gain a `USER_LIST` arm with **no** membership filter (targeted
  people may be community-less).
- **Global redemption cap** (`offer.redemption_limit_total`, present since V6, enforced in
  `redeem`): `OfferService.redeem` now loads the offer with
  `OfferRepository.findByIdForUpdate` (`PESSIMISTIC_WRITE`) so concurrent redemptions of one
  offer serialise. `OfferView` exposes `redemptionsRemaining`.
- **Feedback on an offer** (new): `V26` `offer_feedback` (one row per `(offer, user)`, rating
  1–5, `comment_enc` encrypted at rest like `ticket.rating_comment_enc`) — app-scoped, no RLS,
  consistent with the offer module. `POST /api/v1/offers/{id}/feedback` (RESIDENT, upsert;
  notifies the author via `DomainEventPublisher.publish`), `GET /api/v1/offers/{id}/feedback`
  (author or Super Admin). `OfferView` carries `ratingAvg` / `ratingCount`. Open to any
  resident who can fetch the offer, not only redeemers.

## ADR-034 — Tenant-less service requests for community-less users
**Decision (MVP-8):** a profile-complete `RESIDENT` with no community is now `READY`
(`AuthService.onboardingState` — `NEEDS_COMMUNITY` is no longer returned; `PENDING_APPROVAL`
still applies while a join request is open). Such a user books a **verified provider with no
tenant at all**: `POST /api/v1/tickets` with no active tenant **requires** a `providerId`,
runs a new `requireAssignableProviderGlobal` (VERIFIED + active + a live listing plan, **no**
`tenant_service_provider` enrolment), and creates a `tenant_id = NULL`, `DIRECT_SERVICE`
ticket straight to `ASSIGNED` — no admin routing, no `TICKETS_PER_MONTH` quota, and **no
in-app payment** (the `ticket_payment` tables keep `tenant_id NOT NULL` + tenant RLS; paid
community-less bookings are deferred). `GET /api/v1/providers` returns the global verified
assignable list (contact-free) for a no-tenant caller.

**RLS.** `V24` makes `ticket` / `ticket_status_history` / `ticket_attachment` `tenant_id`
nullable and rebuilds their policies with a null-tenant branch. A second per-connection GUC
`app.current_user_id` (new `UserContext`, set for every authenticated request by
`JwtAuthFilter`, written/`RESET` per borrow by `TenantAwareDataSource`, `''` for
system/unauthenticated) scopes tenant-less rows:
`tenant_id IS NULL AND (current_tenant_id = '*' OR raised_by_user_id = current_user_id OR the
row's assigned provider's user_id = current_user_id)`; the two child tables resolve ownership
through the parent `ticket` in an `EXISTS` (`service_provider` carries no RLS; one level of
nested policy evaluation, not recursion). Existing tenant rows are unaffected (`tenant_id IS
NULL` is false for them). `TicketService.loadForActor` / `list` drop the `requireTenant`
gate and rely on RLS + the role check for isolation.

## ADR-033 — Gated community removal
**Decision (MVP-7):** an admin (or a Super Admin acting as one) can remove a user from a
community via `POST /api/v1/admin/members/{userId}/remove`, preceded by
`GET /api/v1/admin/members/{userId}/removal-check`. `MembershipService.removalBlockers`
reports every ticket the user **raised** in that community that is not `CLOSED` (`RESOLVED`
still counts — the reopen window is live) plus every `ticket_payment` on those tickets whose
status is not in `PAID_ONLINE / PAID_CASH / WAIVED / FAILED`
(`TicketPaymentRepository.findUnsettledForRaiser`, statuses passed as a bind parameter so the
JPQL validates). If either list is non-empty the remove is a hard `409` carrying both lists —
no force-override this MVP. On success every ACTIVE `(user, tenant)` membership goes `EXITED`
(+ `exited_at` + the acting admin), the flat occupant/owner links are cleared, the `AppUser`
is retained, and `current_tenant_id` repoints to another active membership or `null`. No
migration. Mobile: a "Members" tab in `(admin)/community.tsx`.

## ADR-032 — Any flat owner can raise; raiser-aware ticket access
**Decision (MVP-7):** the RESIDENT-only guard in `TicketService.raise` is lifted. A caller
whose role is `ADMIN` or `PROVIDER` may raise a ticket **only** with a `flatId` for a flat
they hold an ACTIVE `user_tenant_membership` in (the pre-existing ownership check enforces
"one of yours"); a non-resident with no `flatId` gets `403`. `SUPER_ADMIN` still cannot raise
(no personal flat). `loadForActor` now returns the ticket to whoever raised it, before the
role switch, so a non-resident raiser sees and can `close` / `reopen` their own ticket (those
transitions were already recorded as `Role.RESIDENT`); `list()` unions a provider's assigned
jobs with tickets they raised. `TicketController` `POST /tickets` + `/close` + `/reopen`
accept `RESIDENT | ADMIN | PROVIDER`. No migration. A mobile raise entry point for
flat-owning admins / providers is a deferred follow-up (backend + tests ship now).

## ADR-031 — Community spans multiple locations
**Decision (MVP-7):** a community (`tenant`) is no longer a single place. `V23` adds a
`location` child table `(tenant_id, label, address_enc, geo_lat/lng, pincode, active)` and
`flat.location_id` (nullable — the app always sets it), backfilling one default location per
existing community and pointing every flat at it. **RLS is enabled + forced on `location`
after the backfill** in the same migration, so the non-superuser Flyway role (no `BYPASSRLS`)
never runs a statement against the forced policy; the policy itself is the standard
`current_tenant_id = '*' OR tenant_id::text = current_tenant_id` copied from `V2`.
`LocationService` + `AdminLocationController` (`/api/v1/admin/locations`, `ADMIN` or a
tenant-scoped `SUPER_ADMIN`); `FlatService.create` requires a `locationId`; `FlatView` gains
`locationId` / `locationLabel`; `TicketService.raise` adds a final service-address fallback
through the flat's location. Invite codes, join, the JWT and RLS scoping stay community-level
(location is an organizing layer only). `location.address` is encrypted at rest like
`flat.address_text`.

## ADR-030 — Provider onboarding authority moves to the Super Admin
**Decision (MVP-7, re-engineers ADR-012/ADR-024):** creating and verifying a provider —
and reviewing its KYC — is Super-Admin-only. `SuperAdminProviderController`
(`/api/v1/superadmin/providers`: `GET` / `POST` / `PUT /{id}` / `POST /{id}/verify` /
`POST /{id}/tier`, the tier route moved off `SuperAdminBillingController`) and
`SuperAdminKycController` (`/api/v1/superadmin/providers/{id}/kyc`, a copy of the old admin
KYC controller with **no** per-community enrolment check). `AdminKycController` and the admin
`POST /providers`, `POST /{id}/verify`, `PUT /{id}` routes are **deleted**. A community admin
now only browses the global **verified** directory (`GET /api/v1/admin/providers/catalog`)
and enrols a provider into their community (`POST /api/v1/admin/providers/{id}/enrol`), and
only when the Super Admin has set `tenant.provider_onboarding_allowed` (`V22`, default off,
settable via `PUT /api/v1/superadmin/tenants/{id}`). `ProviderService` splits: `createGlobal`
(upsert by phone hash, no enrolment), `enrol` (requires `VERIFIED` + the flag),
`verifiedGlobal`, `updateGlobal`; `createForTenant` is kept only for the bootstrap seed.
`KycService.assertVerifiable` is unchanged. Mobile: a new `(super)/providers.tsx` console;
`(admin)/providers.tsx` becomes enrol-only.

## ADR-029 — Admin ↔ many communities via `admin_tenant`; Super-Admin-as-admin
**Decision (MVP-7, re-engineers the single-tenant admin model):** an ADMIN account can
administer several communities. `V22` adds an app-scoped `admin_tenant`
`(admin_user_id, tenant_id, added_by_user_id, active)` mapping table (not RLS, like
`user_tenant_membership`), backfilled from every admin's `current_tenant_id`.
`AuthService.resolveActiveTenant` is now the single public source of truth (its duplicate in
`MeController.me` is removed); the ADMIN branch resolves the active tenant from `admin_tenant`
(the pinned `current_tenant_id` if still assigned, else the oldest). `POST
/api/v1/me/active-community` is generalised to `RESIDENT | ADMIN | SUPER_ADMIN` — an ADMIN is
validated against `admin_tenant`, a **SUPER_ADMIN against any ACTIVE tenant**, which mints a
token that still says `role = SUPER_ADMIN` but carries that `tenantId`; `JwtAuthFilter` then
scopes such a token to that community instead of the RLS wildcard ("acting as admin"). `POST
/api/v1/me/stop-acting` (SUPER_ADMIN) clears it back to wildcard. Every `hasRole('ADMIN')`
controller is widened to `hasAnyRole('ADMIN','SUPER_ADMIN')` (each still 403s when the token
has no active community). `ADMIN_SEATS` counting and admin notification / billing recipients
move from `app_user.current_tenant_id` to `admin_tenant` via a small `AdminDirectory` helper
(`SuperAdminController.createAdmin` quota, `MeBillingController`, `BillingService`,
`TicketService.notifyAdmins`, `SlaBreachJob`). `SuperAdminController.createAdmin` attaches an
existing admin instead of 409; new attach/detach/list routes. **Super-Admin-issued invite
codes**: `POST/GET/DELETE /api/v1/superadmin/tenants/{id}/invite-codes` reuse
`InviteCodeService` under `tenantScoped.inTenant` — no schema change (`invite_code.kind`
already exists, its RLS admits the wildcard). `me.memberships` synthesises an `ADMIN` row per
administered community for the mobile switcher.

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
