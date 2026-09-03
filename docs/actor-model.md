# Single Point — Actor Model & Responsibilities

Canonical reference for who does what. Each row: the responsibility, its **state** today, and
the **MVP** that closes the gap. State legend — ✅ built · ◐ partial · ❌ not built ·
**RE-ENG** = changes something already built.

Shipped so far: MVP-1 Foundation · MVP-2 Marketplace & Offers · MVP-3 Payments + taxonomy admin ·
MVP-4 Monetization · MVP-5 Ticketing depth · MVP-6 Reach expansion (switch-community,
direct-to-provider, household model) · MVP-7 Actor authority & multi-community (Super-Admin
provider onboarding, `admin_tenant` + Super-Admin-as-admin, community locations, flat-owner
raise, gated user removal). See `roadmap.md` and `decisions.md`.

---

## Individual user

| # | Responsibility | State | MVP |
|---|---|---|---|
| I1 | Mobile number is the **unique identifier**; OTP login on first use | ✅ `app_user.phone_hash` unique, phone is the identity anchor (ADR-004/007) | — |
| I2 | **Biometric / fingerprint** unlock on subsequent logins (token already persisted) | ❌ `expo-local-authentication` gate before the app opens | **MVP-7** |
| I3 | **Device binding** — a new/changed device forces OTP again; **auto-read** the OTP | ❌ bind a device fingerprint to the session; Android SMS Retriever / iOS autofill for the code | **MVP-7** |
| I4 | Use the app and **book a service provider with no community** | ❌ today a community-less user is stuck at onboarding; tickets are `tenant_id`-scoped | **MVP-8** |
| I5 | Raise **enquiry / feedback** on a service **or on an offer** | ◐ ticket `request_type` ENQUIRY/FEEDBACK exists; offer-feedback is new | MVP-8 |
| I6 | Receive offers / promo codes — issued **only via the Super Admin** | ✅ Super Admin approves every offer (ADR-014); providers submit drafts | — |
| I7 | Join community(s) with an **invite code from the admin / Super Admin** — one-time | ✅ admin codes (MVP-1) + Super-Admin-issued codes (MVP-7, `/superadmin/tenants/{id}/invite-codes`) | — |
| I8 | Be **removed from a community by the admin / Super Admin**, gated on **pending bills / open tickets**; the person keeps the app but loses apartment features | ✅ unmap + bill/ticket gate (MVP-7, `AdminMembershipController`); the community-less app-user state is MVP-8 | MVP-7 (unmap + gate) · MVP-8 (community-less state) |

## Service provider

| # | Responsibility | State | MVP |
|---|---|---|---|
| P1 | **Super Admin onboards & verifies** the provider (document verification, risk / user safety) | ✅ (MVP-7) — `SuperAdminProviderController` create/verify/tier + `SuperAdminKycController`; a community admin only **enrols** a verified provider (`/admin/providers/catalog` + `/enrol`, gated by `tenant.provider_onboarding_allowed`) | — |
| P2 | Provide promo codes / offers / discounts tagged to a **community**, a **set of communities**, **specific individuals**, or **across all** | ◐ single-tenant + all-tenants + enquiry-based targeting exist (`offer_target`); multi-community and individual targeting are new | MVP-8 |
| P3 | Specify offer mechanics: **duration** ✅, **global count cap** (e.g. "top 100" redemptions) | ◐ `valid_from`/`valid_to` + `redemption_limit_per_user`; a total-redemptions cap is new (`offer.total_redemption_limit`) | MVP-8 |
| P4 | A provider may also be a **flat owner** and raise requests for their own flat | ✅ (MVP-7) — `raise` accepts an ADMIN/PROVIDER with an ACTIVE flat membership; `loadForActor`/`list`/`close` are raiser-aware | — |

## Admin

| # | Responsibility | State | MVP |
|---|---|---|---|
| A1 | Issue **invite codes** to tag users under the community | ✅ (MVP-1) | — |
| A2 | **Resolve tickets** — himself, with a provider, or via in-house basic staff (electricians, plumbers) | ✅ `resolveDirect` + provider assignment; in-house staff can be modelled as providers or just resolved directly | — |
| A3 | Track a ticket to closure | ✅ (MVP-1 lifecycle + MVP-5 SLA) | — |
| A4 | **Unmap a user** from the community after verifying pending bills; the person stays an app user, loses apartment features | ✅ (MVP-7, see I8) | — |
| A5 | **Broadcast a notification** to all community residents | ❌ new admin endpoint over `DomainEventPublisher` | MVP-8 |
| A6 | An admin may also be a **flat owner** and raise for their own flat | ✅ (MVP-7, see P4) | — |
| A7 | Manage **one or more communities** (with a switcher) | ✅ (MVP-7) — `admin_tenant` mapping; `POST /me/active-community` generalised to ADMIN; `me.memberships` carries admin rows for the switcher | — |

## Super Admin (platform owner)

| # | Responsibility | State | MVP |
|---|---|---|---|
| S1 | Add a **community by location**; **one community may span multiple places**, each labelled | ✅ (MVP-7) — `V23` `location` child table + `flat.location_id`; `AdminLocationController` | — |
| S2 | Add an **admin** for a community | ✅ `createAdmin` | — |
| S3 | **If a community has no admin, the Super Admin performs the admin activity** and tags users | ✅ (MVP-7) — `POST /me/active-community` for SUPER_ADMIN mints a tenant-scoped token; `POST /me/stop-acting` returns to platform scope | — |
| S4 | **Validate & onboard service providers** after document verification; manage provider maintenance | ✅ (MVP-7, see P1) | — |
| S5 | Validate offers / discounts / promo codes and tag them to communities or **groups of individuals** | ◐ offer approval ✅; group-of-individuals targeting ❌ | MVP-8 |
| S6 | **Broadcast** to all community admins, or across all users | ❌ | MVP-8 |
| S7 | Issue invite codes directly (when acting for an admin-less community) | ✅ (MVP-7) — `/superadmin/tenants/{id}/invite-codes` | — |

## Platform (Single Point app)

| # | Concern | State | Track |
|---|---|---|---|
| X1 | **Audit log of all onboarding** | ◐ `AuditAspect` already records every onboarding write (tenant / admin / provider create, membership approve); MVP-5 added `@AuditRead`. Missing: a Super-Admin **audit view** API/screen | MVP-9 |
| X2 | **Dashboards** for every role — info / action items, completed, in-progress | ❌ only a tiny `tenantHealth` aggregate | MVP-9 |
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
5. **Community-less individual user** (I4/I5/I8-b) — the largest: an onboarding path that ends
   `READY` with no community, plus tenant-less service requests (a nullable `ticket.tenant_id`
   with a dedicated RLS policy, or a separate `direct_request` table) and direct provider
   booking with no tenant. Deferred to **MVP-8** so MVP-7 stays structural.

---

## MVP breakdown (from here)

| MVP | Theme | Contents |
|---|---|---|
| **MVP-7** ✅ | **Actor authority & multi-community** | Super-Admin provider onboarding (re-eng #1) · community → multiple **locations** (re-eng #2) · **admin ↔ many communities** + switcher + Super-Admin-as-admin (re-eng #3) · **admin/provider raise for their own flat** (re-eng #4) · **admin/Super-Admin removes a user** with a pending-bills / open-tickets gate · Super-Admin-issued invite codes. *Deferred: mobile raise entry for flat-owning admins/providers.* |
| **MVP-8** | **Community-less users & offers depth** | Individual user with **no community** — onboarding + tenant-less service requests + direct booking + **biometric login / device binding / OTP auto-read** · **offer targeting** to a set of communities or named individuals · offer **global count cap** ("top 100") · **feedback on an offer** · **broadcast notifications** (admin → residents, Super Admin → admins / all). |
| **MVP-9** | **Visibility** | Per-role **dashboards** (open / in-progress / done, action items) · **audit-log view** for the Super Admin · reporting/exports. |
| **Continuous** | **UX & Ops** | Icon / loading / imagery polish · safe-area & responsive audit (mobile + web) · Micrometer metrics + APM · scheduled backups + S3 lifecycle. |
