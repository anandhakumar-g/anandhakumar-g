# Single Point — Console

A standalone **Super-Admin-only** web console (Vite + React + TypeScript). It talks to the
same backend API as the mobile app (`/api/v1/superadmin/*`) — the Expo app still covers
everything a *community admin* does.

```bash
npm install
npm run dev        # http://localhost:19020
npm run build      # tsc --noEmit + vite build
```

Point it at a non-default backend with `VITE_API_BASE` (default
`http://localhost:18080/api/v1`), e.g. `VITE_API_BASE=https://api.example.com/api/v1 npm run dev`.

## v1 scope
Login (Super Admin only) · Dashboard (platform analytics + totals) · Communities (list +
health, suspend/archive) · Community requests (self-onboarding approval queue) · Providers
(verify / KYC review / tier) · Offers (approval queue) · Billing (plans / subscriptions /
invoices).

## Deliberately not here (use the Expo `(super)` screens)
Ticket-category & vendor-taxonomy CRUD · the audit-log viewer · broadcasts · per-ticket
drill-down.
