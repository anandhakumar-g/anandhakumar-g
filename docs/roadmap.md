# Single Point — Phased MVP Roadmap

Reprioritised for market reach and early revenue: the vendor marketplace / offers side moves
ahead of deeper ticketing polish, since that is where reach and initial profit come from.
Foundation still comes first. **Each MVP is built, released, and verified before the next set
is planned.**

| MVP | Theme | Contents |
|---|---|---|
| **MVP-1** | **Foundation** ✅ *shipped* | Resident onboarding (OTP + tenant/flat join via invite code or admin approval), raise ticket (photo + service location), admin view/resolve/assign/reroute, provider accept/update status, push notifications (outbox), single seeded global category list, manual provider verification, immutable status history, multi-tenant isolation (RLS + RBAC), token-based theming (Light/Dark/accent + tenant branding), entitlements seam. **No payment.** |
| **MVP-2** | **Vendor Marketplace & Offers** ✅ *shipped* | Vendor taxonomy expansion (food & dining, retail, travel, accommodation, events & entertainment), provider KYC upload + verification workflow (VERIFIED now gated on accepted docs), Offers/Coupons module with layered targeting, **Super Admin as validating authority for every offer** (incl. enquiry-based targeting off `request_type = ENQUIRY`), mandatory anti-fatigue controls (opt-in category subscriptions, central weekly frequency cap, digest mode, independent ticket/promo opt-outs), custom typography (Bricolage Grotesque + Plus Jakarta Sans) + deals feed. Dedicated React admin console **deferred** — admin/super-admin work lives in the Expo app (also runs on web). |
| **MVP-3** | Payments (+ taxonomy admin) | Cash-with-OTP confirmation + online gateway (Razorpay/Cashfree) payment, digital receipts, gateway-abstraction + tokenization. **Also pulled forward from MVP-7:** Super Admin vendor-category management — create / rename / reorder / deactivate categories and open new verticals (kinds moved to a lookup table so no migration is needed), with a "Taxonomy" screen in the super-admin app. |
| **MVP-4** | Monetization on | Subscription / entitlements enforced (flip from free to paid for tenants/vendors), Super Admin featured/premium vendor tier. |
| **MVP-5** | Ticketing depth | Admin category management, WhatsApp notifications, reroute/audit-trail polish, provider directory management, provider/resident availability status, mandatory resident approval-of-allocation gate, SLA tracking + breach alerts, ratings aggregation, PII encryption/masking hardening. |
| **MVP-6** | Reach expansion | User portability (switch-community flow), Direct-to-Provider mode (`request_mode = DIRECT_SERVICE`). |
| **MVP-7** | Platform scale | Multi-tenant self-onboarding, full Super Admin console, analytics dashboards. *(Vendor-taxonomy self-service governance pulled forward to MVP-3.)* |

Data-model columns and service seams for later MVPs are pre-placed in MVP-1 where doing so
avoids a migration later (`request_mode`, `sla_due_at`, `flat.owner_user_id`, `tier`,
`EntitlementService`, `vendor_category` taxonomy table, `PushSender` interface).
