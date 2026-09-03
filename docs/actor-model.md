# Single Point — Actor Model & Responsibilities

Canonical reference for who does what. Each row: the responsibility, its **state** today, and
the **MVP** that closes the gap. State legend — ✅ built · ◐ partial · ❌ not built ·
**RE-ENG** = changes something already built.

Shipped so far: MVP-1 Foundation · MVP-2 Marketplace & Offers · MVP-3 Payments + taxonomy admin ·
MVP-4 Monetization · MVP-5 Ticketing depth · MVP-6 Reach expansion (switch-community,
direct-to-provider, household model). See `roadmap.md` and `decisions.md`.

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
| I7 | Join community(s) with an **invite code from the admin / Super Admin** — one-time | ✅ admin codes (MVP-1); Super-Admin-issued codes ◐ (small add) | MVP-7 |
| I8 | Be **removed from a community by the admin / Super Admin**, gated on **pending bills / open tickets**; the person keeps the app but loses apartment features | ❌ MVP-6 added *self*-leave only; admin-initiated unmap + the bill/ticket gate + the app-user-without-community state | **MVP-7** (unmap + gate) · MVP-8 (community-less state) |

## Service provider

| # | Responsibility | State | MVP |
|---|---|---|---|
| P1 | **Super Admin onboards & verifies** the provider (document verification, risk / user safety) | **RE-ENG** — today the community admin creates the provider (`AdminProviderController.create`) and reviews KYC. Move creation + verification to the Super Admin; a community admin only **enrols** an already-verified provider (or requests one) | **MVP-7** |
| P2 | Provide promo codes / offers / discounts tagged to a **community**, a **set of communities**, **specific individuals**, or **across all** | ◐ single-tenant + all-tenants + enquiry-based targeting exist (`offer_target`); multi-community and individual targeting are new | MVP-8 |
| P3 | Specify offer mechanics: **duration** ✅, **global count cap** (e.g. "top 100" redemptions) | ◐ `valid_from`/`valid_to` + `redemption_limit_per_user`; a total-redemptions cap is new (`offer.total_redemption_limit`) | MVP-8 |
| P4 | A provider may also be a **flat owner** and raise requests for their own flat | ❌ `raise` is RESIDENT-only | **MVP-7** |

## Admin

| # | Responsibility | State | MVP |
|---|---|---|---|
| A1 | Issue **invite codes** to tag users under the community | ✅ (MVP-1) | — |
| A2 | **Resolve tickets** — himself, with a provider, or via in-house basic staff (electricians, plumbers) | ✅ `resolveDirect` + provider assignment; in-house staff can be modelled as providers or just resolved directly | — |
| A3 | Track a ticket to closure | ✅ (MVP-1 lifecycle + MVP-5 SLA) | — |
| A4 | **Unmap a user** from the community after verifying pending bills; the person stays an app user, loses apartment features | ❌ (see I8) | **MVP-7** / MVP-8 |
| A5 | **Broadcast a notification** to all community residents | ❌ new admin endpoint over `DomainEventPublisher` | MVP-8 |
| A6 | An admin may also be a **flat owner** and raise for their own flat | ❌ `raise` is RESIDENT-only | **MVP-7** |
| A7 | Manage **one or more communities** (with a switcher) | ❌ admin is single-tenant (`current_tenant_id`) | **MVP-7** |

## Super Admin (platform owner)

| # | Responsibility | State | MVP |
|---|---|---|---|
| S1 | Add a **community by location**; **one community may span multiple places**, each labelled | **RE-ENG** — `tenant` is a single place (city/locality/address). Add a `location` child table (label / geo / pincode); flats hang off a location | **MVP-7** |
| S2 | Add an **admin** for a community | ✅ `createAdmin` | — |
| S3 | **If a community has no admin, the Super Admin performs the admin activity** and tags users | ❌ Super-Admin-as-admin fallback | **MVP-7** |
| S4 | **Validate & onboard service providers** after document verification; manage provider maintenance | RE-ENG (see P1) | **MVP-7** |
| S5 | Validate offers / discounts / promo codes and tag them to communities or **groups of individuals** | ◐ offer approval ✅; group-of-individuals targeting ❌ | MVP-8 |
| S6 | **Broadcast** to all community admins, or across all users | ❌ | MVP-8 |
| S7 | Issue invite codes directly (when acting for an admin-less community) | ◐ small add on top of S3 | MVP-7 |

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

1. **Provider onboarding authority: community admin → Super Admin.** (P1/S4) Providers become
   Super-Admin-created + verified global entities. A community admin only enrols a verified
   provider into their community (or files a request). Touches `AdminProviderController`,
   `ProviderService.createForTenant`, the KYC review flow, `BootstrapService` seed, and the
   mobile admin "Add provider" screen.
2. **`tenant` → community with multiple locations.** (S1) New `location` child table
   `(id, tenant_id, label, address, geo_lat, geo_lng, pincode, active)`; `flat.location_id`
   replaces the flat living directly under `tenant`. Onboarding, the flat register, address
   defaults and the community directory all shift to locations. RLS stays on `tenant_id`.
3. **Admin ↔ many communities.** (A7/S3) Either a new `admin_tenant` mapping table or reuse
   `user_tenant_membership` with an ADMIN-flavoured role. An admin's JWT carries one active
   tenant that they switch with the MVP-6 machinery (`/me/active-community` generalised beyond
   RESIDENT). Super-Admin-as-admin = the Super Admin can assume any tenant scope on demand
   (already true for reads via the RLS wildcard; needs write paths + UI).
4. **Any role that owns a flat can raise a request.** (P4/A6) Lift the RESIDENT-only guard in
   `TicketService.raise`; a caller with an ACTIVE flat membership may raise regardless of
   role, and `loadForActor` treats them as the raiser for that ticket (not "admin, full
   tenant visibility").
5. **Community-less individual user** (I4/I5/I8-b) — the largest: an onboarding path that ends
   `READY` with no community, plus tenant-less service requests (a nullable `ticket.tenant_id`
   with a dedicated RLS policy, or a separate `direct_request` table) and direct provider
   booking with no tenant. Deferred to **MVP-8** so MVP-7 stays structural.

---

## MVP breakdown (from here)

| MVP | Theme | Contents |
|---|---|---|
| **MVP-7** | **Actor authority & multi-community** | Super-Admin provider onboarding (re-eng #1) · community → multiple **locations** (re-eng #2) · **admin ↔ many communities** + switcher + Super-Admin-as-admin (re-eng #3) · **admin/provider raise for their own flat** (re-eng #4) · **admin/Super-Admin removes a user** with a pending-bills / open-tickets gate · Super-Admin-issued invite codes. |
| **MVP-8** | **Community-less users & offers depth** | Individual user with **no community** — onboarding + tenant-less service requests + direct booking + **biometric login / device binding / OTP auto-read** · **offer targeting** to a set of communities or named individuals · offer **global count cap** ("top 100") · **feedback on an offer** · **broadcast notifications** (admin → residents, Super Admin → admins / all). |
| **MVP-9** | **Visibility** | Per-role **dashboards** (open / in-progress / done, action items) · **audit-log view** for the Super Admin · reporting/exports. |
| **Continuous** | **UX & Ops** | Icon / loading / imagery polish · safe-area & responsive audit (mobile + web) · Micrometer metrics + APM · scheduled backups + S3 lifecycle. |
