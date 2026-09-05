# Single Point — Actor Model & Responsibilities

Canonical reference for who does what. Each row: the responsibility, its **state** today, and
the **MVP** that closes the gap. State legend — ✅ built · ◐ partial · ❌ not built ·
**RE-ENG** = changes something already built.

Shipped so far: MVP-1 Foundation · MVP-2 Marketplace & Offers · MVP-3 Payments + taxonomy admin ·
MVP-4 Monetization · MVP-5 Ticketing depth · MVP-6 Reach expansion (switch-community,
direct-to-provider, household model) · MVP-7 Actor authority & multi-community (Super-Admin
provider onboarding, `admin_tenant` + Super-Admin-as-admin, community locations, flat-owner
raise, gated user removal) · MVP-8 Community-less users & offers depth (READY-without-community,
tenant-less booking, `USER_LIST`/`TENANT_LIST` offer targeting, redemption-cap hardening,
offer feedback) · MVP-9 Audit visibility & broadcasts (Super-Admin audit-log view; admin /
Super-Admin announcements over the outbox) · MVP-10 Dashboards, device security & paid
bookings (per-role `GET /dashboard`, device-bound sessions + biometric unlock + OTP
autofill, paid community-less bookings) · MVP-11 Platform scale (community self-onboarding
with a Super-Admin review queue, `GET /superadmin/analytics`, a standalone Super Admin web
console in `admin-web/`). See `roadmap.md` and `decisions.md`.

---

## Individual user

| # | Responsibility | State | MVP |
|---|---|---|---|
| I1 | Mobile number is the **unique identifier**; OTP login on first use | ✅ `app_user.phone_hash` unique, phone is the identity anchor (ADR-004/007) | — |
| I2 | **Biometric / fingerprint** unlock on subsequent logins (token already persisted) | ✅ (MVP-10) — opt-in, default off, gated on hardware + enrollment; a lock screen gates the whole app before any route renders. `expo-local-authentication`. ADR-037 | — |
| I3 | **Device binding** — a new/changed device forces OTP again; **auto-read** the OTP | ✅ (MVP-10) — a JWT `deviceId` claim + `X-Device-Id` header (`JwtAuthFilter`, `SP-401-DEVICE` on mismatch) rejects a replayed token on another device, forcing a fresh OTP sign-in there; OTP autofill via built-in `TextInput` props (`textContentType`/`autoComplete`), no native module. ADR-037 | — |
| I4 | Use the app and **book a service provider with no community** | ✅ (MVP-8) — a profile-complete resident with no community is READY; `POST /tickets` with a `providerId` books tenant-free (nullable `ticket.tenant_id` + a user-scoped RLS branch, ADR-034). Payment on such bookings is MVP-9. | — |
| I5 | Raise **enquiry / feedback** on a service **or on an offer** | ✅ (MVP-8) — `offer_feedback` + `POST /offers/{id}/feedback` (ADR-035); ticket ENQUIRY/FEEDBACK already existed | — |
| I6 | Receive offers / promo codes — issued **only via the Super Admin** | ✅ Super Admin approves every offer (ADR-014); providers submit drafts | — |
| I7 | Join community(s) with an **invite code from the admin / Super Admin** — one-time | ✅ admin codes (MVP-1) + Super-Admin-issued codes (MVP-7, `/superadmin/tenants/{id}/invite-codes`) | — |
| I8 | Be **removed from a community by the admin / Super Admin**, gated on **pending bills / open tickets**; the person keeps the app but loses apartment features | ✅ (MVP-7) unmap + bill/ticket gate (`AdminMembershipController`); ✅ (MVP-8) the removed person is `READY` with no community and can still book directly | — |

## Service provider

| # | Responsibility | State | MVP |
|---|---|---|---|
| P1 | **Super Admin onboards & verifies** the provider (document verification, risk / user safety) | ✅ (MVP-7) — `SuperAdminProviderController` create/verify/tier + `SuperAdminKycController`; a community admin only **enrols** a verified provider (`/admin/providers/catalog` + `/enrol`, gated by `tenant.provider_onboarding_allowed`) | — |
| P2 | Provide promo codes / offers / discounts tagged to a **community**, a **set of communities**, **specific individuals**, or **across all** | ✅ (MVP-8) — `TENANT_LIST` + `USER_LIST` (by phone) join the existing single/all/enquiry targeting (ADR-035) | — |
| P3 | Specify offer mechanics: **duration** ✅, **global count cap** (e.g. "top 100" redemptions) | ✅ (MVP-8) — `offer.redemption_limit_total` enforced in `redeem` under a pessimistic lock; `redemptionsRemaining` on the view (ADR-035) | — |
| P4 | A provider may also be a **flat owner** and raise requests for their own flat | ✅ (MVP-7) — `raise` accepts an ADMIN/PROVIDER with an ACTIVE flat membership; `loadForActor`/`list`/`close` are raiser-aware | — |

## Admin

| # | Responsibility | State | MVP |
|---|---|---|---|
| A1 | Issue **invite codes** to tag users under the community | ✅ (MVP-1) | — |
| A2 | **Resolve tickets** — himself, with a provider, or via in-house basic staff (electricians, plumbers) | ✅ `resolveDirect` + provider assignment; in-house staff can be modelled as providers or just resolved directly | — |
| A3 | Track a ticket to closure | ✅ (MVP-1 lifecycle + MVP-5 SLA) | — |
| A4 | **Unmap a user** from the community after verifying pending bills; the person stays an app user, loses apartment features | ✅ (MVP-7, see I8) | — |
| A5 | **Broadcast a notification** to all community residents | ✅ (MVP-9) — `POST /api/v1/admin/broadcasts` (scope fixed to the acting community) → one outbox row fanned out to every ACTIVE resident; opt-out via `notification_preference.broadcast_enabled`; 60s cooldown + 20/day cap. ADR-036 | — |
| A6 | An admin may also be a **flat owner** and raise for their own flat | ✅ (MVP-7, see P4) | — |
| A7 | Manage **one or more communities** (with a switcher) | ✅ (MVP-7) — `admin_tenant` mapping; `POST /me/active-community` generalised to ADMIN; `me.memberships` carries admin rows for the switcher | — |

## Super Admin (platform owner)

| # | Responsibility | State | MVP |
|---|---|---|---|
| S1 | Add a **community by location**; **one community may span multiple places**, each labelled | ✅ (MVP-7) — `V23` `location` child table + `flat.location_id`; `AdminLocationController`. ✅ (MVP-11) — communities can now also **self-onboard**: `POST /onboarding/community` → a `PENDING_REVIEW` tenant a Super Admin approves (`/superadmin/community-requests/{id}/approve`), promoting the requester to its ADMIN. ADR-038 | — |
| S2 | Add an **admin** for a community | ✅ `createAdmin` | — |
| S3 | **If a community has no admin, the Super Admin performs the admin activity** and tags users | ✅ (MVP-7) — `POST /me/active-community` for SUPER_ADMIN mints a tenant-scoped token; `POST /me/stop-acting` returns to platform scope | — |
| S4 | **Validate & onboard service providers** after document verification; manage provider maintenance | ✅ (MVP-7, see P1) | — |
| S5 | Validate offers / discounts / promo codes and tag them to communities or **groups of individuals** | ✅ (MVP-8) — the Super Admin can override an offer's audience to `USER_LIST` (phones) at approval (ADR-035) | — |
| S6 | **Broadcast** to all community admins, or across all users | ✅ (MVP-9) — `POST /api/v1/superadmin/broadcasts` with `scope` `ALL_ADMINS` / `ALL_USERS` / `COMMUNITY`; same outbox fan-out + opt-out + rate guard as A5. ADR-036 | — |
| S7 | Issue invite codes directly (when acting for an admin-less community) | ✅ (MVP-7) — `/superadmin/tenants/{id}/invite-codes` | — |

## Platform (Single Point app)

| # | Concern | State | Track |
|---|---|---|---|
| X1 | **Audit log of all onboarding** | ✅ (MVP-9) — `AuditAspect` capture (since MVP-1) + `@AuditRead` (MVP-5) + the Super-Admin **audit view**: `GET /superadmin/audit-logs[/actions]` with filters + a mobile viewer. ADR-036 | — |
| X2 | **Dashboards** for every role — info / action items, completed, in-progress | ✅ (MVP-10) — `GET /api/v1/dashboard`, one flexible view shaped by the caller's role, mounted on each existing home screen. ✅ (MVP-11) — **platform analytics**: `GET /superadmin/analytics` time-bucketed series + totals, surfaced richly in the new Super Admin console and as a compact tile in the Expo `(super)` home. ADR-037, ADR-038 | — |
| X3 | **Archival & backup** configuration | ❌ ops — scheduled `pg_dump` + S3 lifecycle rules; documented, not app code | ops |
| X4 | **Performance monitoring** | ◐ Actuator `/health` + an access-log filter with timings; add Micrometer metrics + an APM hook | ops |
| X5 | **Look & feel** — fancy icons, loading states, images, contextual notifications | ◐ custom fonts (MVP-2), base component kit; needs a dedicated polish pass | continuous UX track |
| X6 | **Screen fit** — mobile + web; never hide the top panel / back button; respect Android & iOS safe areas | ◐ `SafeAreaProvider` is wired; needs a screen-by-screen audit | continuous UX track |

---

## Re-engineering items (change existing design)

1. ✅ **Provider onboarding authority: community admin → Super Admin.** (P1/S4, MVP-7)
   `SuperAdminProviderController` + `SuperAdminKycController`; `AdminKycController` and the
   admin create/verify routes removed; `ProviderService` split into `createGlobal` / `enrol` /
   `verifiedGlobal` / `updateGlobal`; `tenant.provider_onboarding_allowed` (V22) gates admin
   enrolment. See ADR-030.
2. ✅ **`tenant` → community with multiple locations.** (S1, MVP-7) `V23` `location` child
   table + `flat.location_id` (RLS enabled after the backfill); `AdminLocationController`;
   `FlatService.create` requires a `locationId`. See ADR-031.
3. ✅ **Admin ↔ many communities.** (A7/S3, MVP-7) New `admin_tenant` mapping (V22).
   `AuthService.resolveActiveTenant` is the single source of truth; `POST /me/active-community`
   generalised to ADMIN + SUPER_ADMIN; a SUPER_ADMIN token with a `tenantId` claim is scoped
   to that community by `JwtAuthFilter` (acting-as-admin); `POST /me/stop-acting` returns to
   wildcard. `ADMIN_SEATS` / notify / billing recipients move to `AdminDirectory`. See ADR-029.
4. ✅ **Any role that owns a flat can raise a request.** (P4/A6, MVP-7) Guard lifted in
   `TicketService.raise`; `loadForActor` returns the ticket to its raiser before the role
   switch; `list` / `close` / `reopen` are raiser-aware. See ADR-032.
5. ✅ **Community-less individual user.** (I4/I5/I8-b, MVP-8) Onboarding ends `READY` with no
   community (`NEEDS_COMMUNITY` retired); tenant-less service requests via a nullable
   `ticket.tenant_id` + a new `app.current_user_id` GUC + a null-tenant RLS branch on `ticket`
   and its two children (`V24`), scoping such rows to their raiser + assigned provider;
   `POST /tickets` with a `providerId` books any verified provider with no tenant
   (`requireAssignableProviderGlobal`), `GET /providers` falls back to the global verified
   list. No admin, no quota, no payment (payment deferred to MVP-9). See ADR-034.

---

## MVP breakdown (from here)

| MVP | Theme | Contents |
|---|---|---|
| **MVP-7** ✅ | **Actor authority & multi-community** | Super-Admin provider onboarding (re-eng #1) · community → multiple **locations** (re-eng #2) · **admin ↔ many communities** + switcher + Super-Admin-as-admin (re-eng #3) · **admin/provider raise for their own flat** (re-eng #4) · **admin/Super-Admin removes a user** with a pending-bills / open-tickets gate · Super-Admin-issued invite codes. *Deferred: mobile raise entry for flat-owning admins/providers.* |
| **MVP-8** ✅ | **Community-less users & offers depth** | Individual user with **no community** — onboarding ends `READY` with no tenant + tenant-less service requests (nullable `ticket.tenant_id` + `app.current_user_id` GUC + null-tenant RLS branch) + direct booking of any verified provider · **offer targeting** to a set of communities (`TENANT_LIST`) or named individuals by phone (`USER_LIST`) · offer **global count cap** ("top 100") hardened with a pessimistic redeem lock + `redemptionsRemaining` · **feedback on an offer** (`offer_feedback`, encrypted comment, `ratingAvg`/`ratingCount`). *Deferred to MVP-9: biometric login / device binding / OTP auto-read; broadcast notifications; paid community-less bookings.* |
| **MVP-9** ✅ | **Audit visibility & broadcasts** | **Super-Admin audit-log view** (`GET /superadmin/audit-logs[/actions]`, filterable + paged, over the rows `AuditAspect` already writes; `V27` read indexes) · **broadcast announcements** (`com.singlepoint.broadcast`: admin → acting community's residents, Super Admin → `ALL_ADMINS` / `ALL_USERS` / a `COMMUNITY`; one `broadcast` row + one outbox row fanned out; `kind:"broadcast"` + `notification_preference.broadcast_enabled` opt-out; 60s + 20/day rate guard; `V28`). *Deferred to MVP-10: per-role dashboards; biometric / device-binding / OTP auto-read; reporting / exports; paid community-less bookings.* |
| **MVP-10** ✅ | **Dashboards, device security & paid bookings** | Per-role **dashboards** (`GET /dashboard`, one flexible view; open / in-progress / done buckets + role-specific action items) mounted on the existing home screens · **device-bound sessions** (a JWT `deviceId` claim + `X-Device-Id`, `SP-401-DEVICE` on mismatch, fully backward compatible) · **biometric unlock** (opt-in, default off) · **OTP autofill** (built-in `TextInput` props, no native module) · **paid community-less bookings** (`ticket_payment`/`payment_receipt`/`payment_event` relaxed like `ticket`, V29). *Deferred to MVP-11: reporting/exports (CSV); a Super Admin web console; multi-tenant self-onboarding.* |
| **MVP-11** ✅ | **Platform scale** | **Community self-onboarding** (`POST /onboarding/community` → `PENDING_REVIEW` tenant, `V30`; Super-Admin approve/reject queue; approve promotes the requester to ADMIN) · **platform analytics** (`GET /superadmin/analytics`, week/month time-bucketed series + now-totals, Java bucketing) · a **standalone Super Admin web console** in `admin-web/` (Vite + React; login, dashboard, communities, onboarding queue, providers + KYC, offers, billing; the Expo `(super)` group untouched). *Deferred to MVP-12: reporting / CSV exports; console parity for taxonomy CRUD / audit viewer / broadcasts / plan CRUD.* |
| **MVP-12** | **Reporting & polish** | Reporting / exports (CSV) · console parity for the remaining Expo `(super)` surfaces · the continuous UX & Ops track. |
| **Continuous** | **UX & Ops** | Icon / loading / imagery polish · safe-area & responsive audit (mobile + web) · Micrometer metrics + APM · scheduled backups + S3 lifecycle. |
