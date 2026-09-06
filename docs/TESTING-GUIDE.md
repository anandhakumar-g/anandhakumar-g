# Single Point — Testing Guide (web + mobile)

How to bring the full stack up and start exercising every role, on the web console, on
mobile‑web, and on a real Android phone via an installable APK.

Applies to the codebase at tag **`mvp-13`** (`main`). Ports and seed data are the local
defaults; override with env vars where noted.

---

## 0. What runs where

| Piece | Command dir | URL | Notes |
|---|---|---|---|
| PostgreSQL 16+ | — | `localhost:5433` | DBs `singlepoint` (dev) + `singlepoint_test` (tests) |
| Backend API | `backend/` | `http://localhost:18080/api/v1` | mgmt/actuator on `:18081` |
| Super Admin console | `admin-web/` | `http://localhost:19020` | Vite dev server |
| Mobile app (web) | `mobile/` | `http://localhost:19010` (Expo) | `npx expo start --web` |
| Mobile app (phone) | `mobile/` | — | Expo Go (dev) **or** the preview APK (§4) |

**Java:** run Maven/Gradle under **JDK 17** (`C:\Program Files\Microsoft\jdk-17.0.20.8-hotspot`).
A newer JDK silently breaks Spring Boot 2.7.18's pinned Lombok.
**Node:** use **Node 22 LTS** for the mobile tooling (Node 26 frays Expo SDK 57).

---

## 1. Start PostgreSQL + the backend

```bash
# one-time: databases + the non-superuser app role
psql -h localhost -p 5433 -U postgres -c "CREATE DATABASE singlepoint;"
psql -h localhost -p 5433 -U postgres -c "CREATE DATABASE singlepoint_test;"
psql -h localhost -p 5433 -U postgres -d singlepoint      -f backend/db/bootstrap.sql
psql -h localhost -p 5433 -U postgres -d singlepoint_test -f backend/db/bootstrap.sql

# run the API (Flyway applies V1..V36, demo data seeds on first start)
cd backend
set "JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.20.8-hotspot"
mvn -q -o spring-boot:run -Dspring-boot.run.profiles=local
```

Ready when the log prints `Started SinglePointApplication`. Quick check:

```bash
curl http://localhost:18080/v3/api-docs.yaml | head -5     # -> openapi: 3.0.1 ... version: MVP-13
```

Swagger UI: `http://localhost:18080/swagger-ui.html`.

### Seed accounts (local profile, first start)

| Role | Phone | Notes |
|---|---|---|
| **Super Admin** | `+919000000000` | platform owner — the console login |
| Green Meadows **admin** | `+919000000101` | tenant admin; an invite code is printed in the startup banner |
| Lakeview Residency **admin** | `+919000000201` | second community (isolation testing) |
| **Provider** — Sparky Electricals | `+919000000301` | VERIFIED, enrolled in Green Meadows |
| **Resident** (Postman) | `+919888100001` | starts at "needs profile" |

**Dev OTP:** any phone works. The 6‑digit code is printed to `backend/logs/app.log` and
returned in the `devCode` field of `POST /auth/otp/request` while `sp.otp.dev-mode=true`.
The mobile/console sign‑in screens show it inline.

### Reset the dev data

Stop the backend, then (works as the non‑superuser `singlepoint_app`):

```bash
psql -h localhost -p 5433 -U postgres -c "DROP DATABASE singlepoint;"
psql -h localhost -p 5433 -U postgres -c "CREATE DATABASE singlepoint;"
psql -h localhost -p 5433 -U postgres -d singlepoint -f backend/db/bootstrap.sql
```

Restart the backend — Flyway + BootstrapService rebuild everything. `singlepoint_test` is
untouched, so `mvn test` is unaffected.

---

## 2. Web — Super Admin console (`admin-web/`)

```bash
cd admin-web
npm install
npm run dev            # http://localhost:19020
```

- Sign in with **`+919000000000`** and the dev code shown on the screen.
- `+910000009999` is the **integration‑test** super admin — it exists only in
  `singlepoint_test`, not the dev DB. Use `+919000000000`.
- Point at a non‑default backend: `set VITE_API_BASE=http://host:18080/api/v1 && npm run dev`.

**Console covers (Super Admin only):** Dashboard (analytics + backup tile), Communities +
lifecycle, self‑onboarding request queue, Providers + KYC + tier, Offers approval, Billing
(plans / subscriptions / invoices, recurring pill), Taxonomy CRUD, Audit log, Broadcasts
(incl. scheduled + cancel), Reports (CSV).

---

## 3. Web — the mobile app in a browser

```bash
cd mobile
npm install
npx expo start --web    # opens http://localhost:19010
```

Everything except native‑only features works: OTP sign‑in, onboarding, tickets, deals,
alerts, settings, "Download my data" (web‑only). Biometric unlock and push registration are
native‑only and are hidden/skipped on web.

Sign in as any seed phone above; each role lands on its own tab bar (Resident / Admin /
Provider / Super Admin).

---

## 4. Mobile — installable Android APK

There is **no Android SDK on this machine**, so the APK is built on **EAS (Expo's cloud
build)** under the Expo account already signed in (`anandhakumar.g`). `mobile/eas.json` is
committed with a `preview` profile that produces a standalone **APK** (not an app‑bundle).

### 4a. Set the API address the APK will talk to

A standalone APK has no Metro server to infer the host from, so the backend URL is **baked
in at build time** from `eas.json` → `build.preview.env.EXPO_PUBLIC_API_URL`.

It is currently:

```
http://192.168.29.218:18080
```

That is this machine's current Wi‑Fi IP. **Confirm / update it** before building:

```bash
# Windows: find the LAN IPv4
ipconfig | findstr /i "IPv4"
```

Edit `mobile/eas.json` if the IP changed. The phone and the machine running the backend
must be on the **same Wi‑Fi/LAN**.

### 4b. Let the phone reach the backend

Spring Boot already binds `0.0.0.0:18080`, so only the Windows firewall is in the way:

```powershell
New-NetFirewallRule -DisplayName "Single Point API 18080" -Direction Inbound `
  -Protocol TCP -LocalPort 18080 -Action Allow
```

Verify from another device's browser: `http://192.168.29.218:18080/swagger-ui.html`.

### 4c. Build the APK

```bash
cd mobile
eas login                       # if not already signed in as anandhakumar.g
eas build --platform android --profile preview
```

First run is interactive — accept the defaults:

1. **"Would you like to create a project for @anandhakumar.g/single-point?"** → **yes**.
   This writes `extra.eas.projectId` into `app.json`; commit that change.
2. **"Generate a new Android Keystore?"** → **yes** (EAS generates and stores it; you never
   handle the file).
3. The build is queued and runs in the cloud (~10–20 min). The CLI prints a build‑details
   URL and, on completion, a **direct `.apk` download link**. All builds are also listed at
   `expo.dev` → project **single-point** → **Builds**.

Notes:

- `--local` builds instead of cloud, but needs the Android SDK + NDK on this machine — not
  installed here, so use the cloud build.
- The `preview` profile produces a plain **release APK** (`android.buildType: apk`), not an
  `.aab`, so it side‑loads directly. It has **no** `expo-dev-client`, so push notifications
  won't register from it — use `--profile development` (after `npx expo install
  expo-dev-client`) if you specifically need to test push on a device.
- Rebuild whenever `EXPO_PUBLIC_API_URL` in `eas.json` changes (the URL is compiled in).

### 4d. Install on the phone

1. Download the `.apk` (open the EAS link on the phone, or copy it over USB).
2. Tap it → Android warns about unknown sources → **Settings ▸ allow this source** → install.
3. Open **Single Point**.

### 4e. Smoke test on the device

1. Sign in with a seed phone + the dev code from `backend/logs/app.log`.
2. If every call fails with a network error: the baked `EXPO_PUBLIC_API_URL` is wrong or the
   firewall is blocking — recheck §4a/§4b and rebuild.
3. Push notifications need a **development build** (`--profile development`, which also needs
   `expo-dev-client` added). The `preview` APK covers everything else. In‑app **Alerts**
   (the notification inbox) work without push.

### Alternative — Expo Go (dev only, no build)

```bash
cd mobile
npx expo start                  # scan the QR with Expo Go
```

Known issues on this project: Expo Go SDK 57 dropped `expo-notifications` (handled with a
lazy import + `ExecutionEnvironment.StoreClient` guard), and tunnelling needs
`@expo/ngrok`. On the same LAN use the **LAN** connection mode, not Tunnel. For a stable
device experience, use the APK from §4.

---

## 5. What to test per role

Sign in as each phone; the app routes to that role's tab bar.

### Resident (`+919888100001` after onboarding, or any new phone)
- OTP sign‑in → complete profile → join a community with the seed invite code (or request
  approval with no code) → land on Home.
- Raise a ticket (category, description, photo, service location) → track it through
  status changes → reopen / close + rate after it's resolved.
- Book a provider directly (if the community has direct service enabled) or as a
  community‑less user.
- **Deals**: browse offers, redeem one, leave feedback on an offer.
- **Alerts** tab: unread badge, open a notification, "Mark all read".
- **Settings**: notification preferences incl. "Offers on WhatsApp"; **Devices** list +
  "Sign out" another device; **Download my data**; **Delete account** (blocked while an
  open ticket / unpaid bill exists).
- Household: invite a family member with a HOUSEHOLD code.
- Switch between communities; leave a community.

### Community Admin (`+919000000101`)
- Queue: triage a ticket — acknowledge, assign to a provider, reroute, resolve directly.
- Members: roster, removal‑check (blocked by open tickets / unpaid bills), remove.
- Community: flats, locations, invite codes, direct‑service toggle, branding.
- Providers: browse the verified catalog, enrol one.
- Broadcast to the community — immediately and **scheduled for later**, then cancel a
  pending one.
- Billing: view the plan, usage vs limits, self‑upgrade, pay a due invoice.

### Provider (`+919000000301`)
- Jobs: accept an assigned ticket, mark en route / resolved, add a payment (online link or
  cash‑OTP), issue a receipt.
- KYC: upload a document, see review status.
- Offers: submit a draft offer for approval.
- Profile: availability, service categories.

### Super Admin (`+919000000000`) — app `(super)` tabs **and** the web console
- Approve / reject a community self‑onboarding request.
- Providers: verify, review KYC, set tier (feature / un‑feature).
- Offers: approve / reject, override the audience.
- Billing: create/edit plans, comp/cancel subscriptions, mark invoices paid.
- Taxonomy: vendor verticals, vendor categories, global ticket categories.
- Audit log: filter by actor / action / outcome / date.
- Broadcast to ALL_ADMINS / ALL_USERS / one community; schedule + cancel.
- Analytics dashboard; CSV exports (tickets / payments / offers / members / communities).
- Backup status (`{enabled:false}` locally).
- Act as an admin for an admin‑less community, then stop acting.

---

## 6. API / regression check (Postman)

`docs/Single-Point-MVP1.postman_collection.json` — import and run **the whole collection top
to bottom** (Runner, or `newman run`). It self‑seeds: folder 1 now issues its own invite
code, folder 9 is ordered so a due invoice exists before it's paid, and a pre‑request guard
turns an unset `{{variable}}` into a readable failure instead of a 500.

Base URL variable defaults to `http://localhost:18080/api/v1`.

---

## 7. Troubleshooting

| Symptom | Fix |
|---|---|
| Backend: Lombok "cannot find symbol" on `@Getter`/`@Setter` across files | Wrong JDK. `set JAVA_HOME` to the JDK 17 path above and re‑run. |
| Console login: "This console is for platform Super Admins only" | Use `+919000000000` (not the test‑only `+910000009999`). |
| APK: every request fails | Baked `EXPO_PUBLIC_API_URL` wrong, phone on a different network, or firewall. §4a/§4b. |
| Expo Go: "Install @expo/ngrok" | Use LAN mode, not Tunnel: `npx expo start` then press `s`/pick LAN, or `--lan`. |
| `mvn test` shows one `resetSchema` TRUNCATE‑deadlock error | Known infra flake — re‑run that one class alone; it passes. Never run two `mvn test` at once. |
| Ports 18080 / 19020 / 19010 in use | Stop the stragglers, or override (`-Dserver.port=`, `--port`). |
