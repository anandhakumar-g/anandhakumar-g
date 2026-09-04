package com.singlepoint.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.singlepoint.crypto.CryptoService;
import com.singlepoint.user.AppUserRepository;
import com.singlepoint.user.domain.AppUser;
import com.singlepoint.user.domain.Role;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Base for full-stack HTTP integration tests. Runs against a real PostgreSQL
 * ({@code singlepoint_test}); each test starts from a freshly migrated schema.
 * (Testcontainers is the CI path — gated on Docker; see docs/decisions.md ADR-009.)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    public static final String SUPER_ADMIN_PHONE = "+910000009999";
    /** Seeded global vendor categories. */
    public static final String VENDOR_CAT_ELECTRICAL = "22222222-0000-0000-0000-000000000001";
    public static final String VENDOR_CAT_RESTAURANT = "22222222-0000-0000-0001-000000000001";

    /** All application tables are wiped before each test (Flyway history is kept). */
    private static final String TRUNCATE_SQL = """
            TRUNCATE TABLE
              audit_log, broadcast, notification, notification_outbox, otp_challenge, device_token,
              notification_preference,
              subscription_invoice, subscription, subscription_plan,
              payment_event, payment_receipt, ticket_payment,
              offer_feedback, offer_redemption, offer_target, offer,
              provider_kyc_document,
              ticket_status_history, ticket_attachment, ticket,
              tenant_service_provider, service_provider,
              invite_code, user_tenant_membership, admin_tenant, flat, location, app_user, tenant,
              category, vendor_category, vendor_category_kind
            RESTART IDENTITY CASCADE
            """;

    /** Minimal taxonomy the tests reference — reseeded each test so runs don't depend on V3 state. */
    private static final String SEED_TAXONOMY_SQL = """
            INSERT INTO category (id, tenant_id, name, request_type, sla_hours, sort_order, active) VALUES
              ('11111111-0000-0000-0000-000000000001', NULL, 'Electrical', 'ISSUE', 24, 10, true),
              ('11111111-0000-0000-0000-000000000002', NULL, 'Plumbing',   'ISSUE', 24, 20, true),
              ('11111111-0000-0000-0000-00000000000d', NULL, 'Enquiry',    'ENQUIRY', NULL, 130, true);
            INSERT INTO vendor_category (id, name, kind, sort_order, active) VALUES
              ('22222222-0000-0000-0000-000000000001', 'Electrical',  'MAINTENANCE',          10,  true),
              ('22222222-0000-0000-0000-000000000002', 'Plumbing',    'MAINTENANCE',          20,  true),
              ('22222222-0000-0000-0001-000000000001', 'Restaurant',  'FOOD_DINING',          200, true),
              ('22222222-0000-0000-0001-000000000002', 'Caterer',     'FOOD_DINING',          210, true),
              ('22222222-0000-0000-0002-000000000001', 'Garments',    'RETAIL',               300, true),
              ('22222222-0000-0000-0003-000000000001', 'Travel Agent','TRAVEL',               400, true),
              ('22222222-0000-0000-0004-000000000001', 'Guest House', 'ACCOMMODATION',        500, true),
              ('22222222-0000-0000-0005-000000000001', 'Party Planning','EVENTS_ENTERTAINMENT', 600, true);
            INSERT INTO vendor_category_kind (code, label, sort_order) VALUES
              ('MAINTENANCE','Home & Maintenance',10), ('FOOD_DINING','Food & Dining',20),
              ('RETAIL','Shops & Retail',30), ('TRAVEL','Travel',40), ('ACCOMMODATION','Stays',50),
              ('EVENTS_ENTERTAINMENT','Events & Entertainment',60), ('OTHER','Other',99);
            INSERT INTO subscription_plan (target, code, name, billing_cycle, price_amount, entitlements, is_default, sort_order) VALUES
              ('TENANT',  'TENANT_FREE',     'Community Free',     'MONTHLY',    0, '{"TICKETS_PER_MONTH":-1,"ADMIN_SEATS":5,"OFFERS_PER_MONTH":10,"WHATSAPP_NOTIFICATIONS":0}',  true,  10),
              ('TENANT',  'TENANT_STANDARD', 'Community Standard', 'MONTHLY', 2999, '{"TICKETS_PER_MONTH":-1,"ADMIN_SEATS":15,"OFFERS_PER_MONTH":40,"WHATSAPP_NOTIFICATIONS":-1}', false, 20),
              ('TENANT',  'TENANT_PLUS',     'Community Plus',     'MONTHLY', 5999, '{"TICKETS_PER_MONTH":-1,"ADMIN_SEATS":-1,"OFFERS_PER_MONTH":-1,"WHATSAPP_NOTIFICATIONS":-1}', false, 30),
              ('PROVIDER','PROVIDER_FREE',   'Vendor Free',        'MONTHLY',    0, '{"DIRECTORY_LISTING":1,"OFFERS_PER_MONTH":8}',   true,  10),
              ('PROVIDER','PROVIDER_LISTING','Vendor Listing',     'MONTHLY',  499, '{"DIRECTORY_LISTING":1,"OFFERS_PER_MONTH":40}',  false, 20);
            """;

    @Autowired protected TestRestTemplate rest;
    @Autowired protected Flyway flyway;
    @Autowired protected JdbcTemplate jdbcTemplate;
    @Autowired protected AppUserRepository userRepository;
    @Autowired protected CryptoService crypto;

    @BeforeEach
    void resetSchema() {
        flyway.migrate(); // no-op once the schema is current
        jdbcTemplate.execute(TRUNCATE_SQL);
        jdbcTemplate.execute(SEED_TAXONOMY_SQL);
        jdbcTemplate.execute("ALTER SEQUENCE ticket_ref_seq RESTART WITH 1000");
        AppUser su = new AppUser();
        su.setRole(Role.SUPER_ADMIN);
        su.setName("Test Platform Owner");
        su.setPhone(SUPER_ADMIN_PHONE);
        su.setPhoneHash(crypto.lookupHash(SUPER_ADMIN_PHONE));
        su.setProfileCompleted(true);
        userRepository.save(su);
    }

    // ---- HTTP helpers --------------------------------------------------------

    protected ResponseEntity<JsonNode> http(HttpMethod method, String path, String token, Object body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) h.setBearerAuth(token);
        return rest.exchange(path, method, new HttpEntity<>(body, h), JsonNode.class);
    }

    protected JsonNode post(String path, String token, Object body) {
        ResponseEntity<JsonNode> r = http(HttpMethod.POST, path, token, body);
        assertTrue(r.getStatusCode().is2xxSuccessful(), path + " -> " + r.getStatusCode() + " " + r.getBody());
        return r.getBody();
    }

    protected JsonNode get(String path, String token) {
        ResponseEntity<JsonNode> r = http(HttpMethod.GET, path, token, null);
        assertTrue(r.getStatusCode().is2xxSuccessful(), path + " -> " + r.getStatusCode() + " " + r.getBody());
        return r.getBody();
    }

    protected JsonNode put(String path, String token, Object body) {
        ResponseEntity<JsonNode> r = http(HttpMethod.PUT, path, token, body);
        assertTrue(r.getStatusCode().is2xxSuccessful(), path + " -> " + r.getStatusCode() + " " + r.getBody());
        return r.getBody();
    }

    protected ResponseEntity<JsonNode> multipart(String path, String token, Map<String, String> parts,
                                                 String fileField, String filename, byte[] bytes) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        if (token != null) h.setBearerAuth(token);
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        parts.forEach(form::add);
        ByteArrayResource res = new ByteArrayResource(bytes) {
            @Override public String getFilename() { return filename; }
        };
        form.add(fileField, res);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(form, h), JsonNode.class);
    }

    // ---- domain helpers ---------------------------------------------------

    protected record Session(String token, String onboardingState, String role, UUID activeTenantId) { }

    protected Session login(String phone) {
        JsonNode otp = post("/api/v1/auth/otp/request", null, Map.of("phone", phone));
        String code = otp.get("devCode").asText();
        JsonNode s = post("/api/v1/auth/otp/verify", null, Map.of("phone", phone, "code", code));
        return toSession(s);
    }

    protected Session completeProfile(String token, String name, String email) {
        return toSession(post("/api/v1/auth/profile", token, Map.of("name", name, "email", email)));
    }

    protected Session toSession(JsonNode s) {
        JsonNode u = s.get("user");
        UUID tenant = u.hasNonNull("activeTenantId") ? UUID.fromString(u.get("activeTenantId").asText()) : null;
        return new Session(s.get("token").asText(), s.get("onboardingState").asText(),
                u.get("role").asText(), tenant);
    }

    protected UUID createTenant(String superToken, String name, String brandColor) {
        JsonNode t = post("/api/v1/superadmin/tenants", superToken,
                Map.of("name", name, "city", "Bengaluru", "brandPrimaryColor", brandColor));
        return UUID.fromString(t.get("id").asText());
    }

    protected String createAdmin(String superToken, UUID tenantId, String phone, String name) {
        return post("/api/v1/superadmin/tenants/" + tenantId + "/admins", superToken,
                Map.of("phone", phone, "name", name)).get("userId").asText();
    }

    /** Attach an already-provisioned admin to a second community. */
    protected void attachAdmin(String superToken, UUID tenantId, String adminUserId) {
        ResponseEntity<JsonNode> r = http(HttpMethod.POST,
                "/api/v1/superadmin/tenants/" + tenantId + "/admins/" + adminUserId, superToken, null);
        assertTrue(r.getStatusCode().is2xxSuccessful(), "attachAdmin -> " + r.getStatusCode() + " " + r.getBody());
    }

    protected String createInvite(String adminToken) {
        JsonNode c = post("/api/v1/admin/invite-codes", adminToken, Map.of("maxUses", 20, "validDays", 30));
        return c.get("code").asText();
    }

    protected UUID createLocation(String adminToken, String label) {
        return UUID.fromString(post("/api/v1/admin/locations", adminToken,
                Map.of("label", label)).get("id").asText());
    }

    /** The admin's first location, creating a default "Main" one if none exists yet. */
    protected UUID ensureLocation(String adminToken) {
        JsonNode locs = get("/api/v1/admin/locations", adminToken);
        if (locs.size() > 0) return UUID.fromString(locs.get(0).get("id").asText());
        return createLocation(adminToken, "Main");
    }

    protected UUID createFlat(String adminToken, String block, String number) {
        return createFlatAt(adminToken, ensureLocation(adminToken), block, number);
    }

    protected UUID createFlatAt(String adminToken, UUID locationId, String block, String number) {
        return UUID.fromString(post("/api/v1/admin/flats", adminToken,
                Map.of("locationId", locationId.toString(), "block", block, "flatNumber", number))
                .get("id").asText());
    }

    /** Admin invite code bound to a specific flat — its first redeemer becomes the flat's PRIMARY. */
    protected String createFlatInvite(String adminToken, UUID flatId) {
        return post("/api/v1/admin/invite-codes", adminToken,
                Map.of("flatId", flatId.toString(), "maxUses", 20, "validDays", 30)).get("code").asText();
    }

    /** MVP-7: the Super Admin creates + verifies a global provider. Enrol it separately per community. */
    protected UUID createVerifiedProvider(String superToken, String name, String phone) {
        JsonNode p = post("/api/v1/superadmin/providers", superToken,
                Map.of("name", name, "vendorCategoryId", VENDOR_CAT_ELECTRICAL, "company", true, "contactPhone", phone));
        UUID id = UUID.fromString(p.get("id").asText());
        // KYC gate: seed accepted docs directly (KycWorkflowIT exercises the real upload/review path).
        for (String docType : new String[]{"GOV_ID", "ADDRESS_PROOF", "COMPANY_REG"}) {
            jdbcTemplate.update(
                    "insert into provider_kyc_document (service_provider_id, doc_type, storage_key, content_type, "
                    + "size_bytes, status, reviewed_at) values (?::uuid, ?, ?, 'text/plain', 0, 'ACCEPTED', now())",
                    id.toString(), docType, "seed/kyc/" + id + "/" + docType);
        }
        JsonNode v = post("/api/v1/superadmin/providers/" + id + "/verify", superToken, Map.of("status", "VERIFIED"));
        assertEquals("VERIFIED", v.get("verificationStatus").asText());
        return id;
    }

    /** Let a community's admin enrol verified providers (Super Admin). */
    protected void enableProviderOnboarding(String superToken, UUID tenantId) {
        put("/api/v1/superadmin/tenants/" + tenantId, superToken, Map.of("providerOnboardingAllowed", true));
    }

    /** Enrol a verified provider into the admin's community. */
    protected void enrolProvider(String adminToken, UUID providerId) {
        post("/api/v1/admin/providers/" + providerId + "/enrol", adminToken, Map.of());
    }

    /** Toggle a community's resident approval-of-allocation gate (Super Admin). */
    protected void setAllocationApproval(String superToken, UUID tenantId, boolean on) {
        put("/api/v1/superadmin/tenants/" + tenantId, superToken,
                Map.of("requireAllocationApproval", on));
    }

    /** Turn on Direct-to-Provider booking for a community (Super Admin). */
    protected void enableDirectService(String superToken, UUID tenantId) {
        put("/api/v1/superadmin/tenants/" + tenantId, superToken, Map.of("directServiceEnabled", true));
    }

    protected String firstCategoryId(String token, String name) {
        for (JsonNode c : get("/api/v1/categories", token)) {
            if (c.get("name").asText().equals(name)) return c.get("id").asText();
        }
        throw new IllegalStateException("category not found: " + name);
    }

    /** MVP-8: a profile-complete resident with no community — onboardingState READY, no active tenant. */
    protected String individualUser(String phone, String name) {
        Session s = completeProfile(login(phone).token(), name, name.toLowerCase() + "@example.com");
        assertEquals("READY", s.onboardingState(), "community-less user should be READY");
        assertTrue(s.activeTenantId() == null, "community-less user has no active tenant");
        return s.token();
    }

    /** Community-less direct booking: raise with a providerId and no flat. */
    protected String bookGlobalProvider(String token, UUID providerId, String categoryName) {
        String cat = firstCategoryId(token, categoryName);
        return post("/api/v1/tickets", token, Map.of(
                "categoryId", cat, "description", "fix it", "serviceAddressText", "12 Nowhere St",
                "providerId", providerId.toString())).get("id").asText();
    }

    protected record Marketplace(String superToken, UUID tenantId, String adminToken,
                                 UUID providerId, String providerToken) { }

    /** One tenant + admin + a VERIFIED provider enrolled in that community and signed in. */
    protected Marketplace marketplace(String adminPhone, String providerPhone) {
        String su = login(SUPER_ADMIN_PHONE).token();
        UUID tenant = createTenant(su, "Green Meadows " + adminPhone, "#2E7D32");
        createAdmin(su, tenant, adminPhone, "Admin " + adminPhone);
        String admin = login(adminPhone).token();
        UUID providerId = createVerifiedProvider(su, "Sparky " + providerPhone, providerPhone);
        enableProviderOnboarding(su, tenant);
        enrolProvider(admin, providerId);
        String providerToken = login(providerPhone).token();
        return new Marketplace(su, tenant, admin, providerId, providerToken);
    }

    protected String joinResident(UUID tenantId, String adminToken, String phone, String name) {
        String code = createInvite(adminToken);
        String tok = completeProfile(login(phone).token(), name, name.toLowerCase() + "@example.com").token();
        return toSession(post("/api/v1/memberships/join", tok,
                Map.of("tenantId", tenantId.toString(), "inviteCode", code))).token();
    }

    /** An already-onboarded resident joins another community with their existing token (invite-code path). */
    protected String joinExistingResident(String residentToken, UUID tenantId, String adminTokenForThatTenant) {
        String code = createInvite(adminTokenForThatTenant);
        return toSession(post("/api/v1/memberships/join", residentToken,
                Map.of("tenantId", tenantId.toString(), "inviteCode", code))).token();
    }

    /** Raise a ticket as the resident and drive it to RESOLVED via the assigned provider. */
    protected String resolvedTicket(String residentToken, String adminToken, String providerToken, UUID providerId) {
        String cat = firstCategoryId(residentToken, "Electrical");
        String id = post("/api/v1/tickets", residentToken, Map.of(
                "categoryId", cat, "description", "fix it", "serviceAddressText", "A-1")).get("id").asText();
        post("/api/v1/tickets/" + id + "/assign", adminToken, Map.of("providerId", providerId.toString()));
        post("/api/v1/tickets/" + id + "/accept", providerToken, Map.of());
        post("/api/v1/tickets/" + id + "/status", providerToken, Map.of("toStatus", "IN_PROGRESS"));
        post("/api/v1/tickets/" + id + "/status", providerToken,
                Map.of("toStatus", "RESOLVED", "resolutionNotes", "done"));
        return id;
    }

    protected String createDraftOffer(String authorToken, String vendorCategoryId) {
        JsonNode o = post("/api/v1/offers", authorToken, Map.ofEntries(
                Map.entry("vendorCategoryId", vendorCategoryId),
                Map.entry("title", "Festive 20% off sweets"),
                Map.entry("description", "Diwali special"),
                Map.entry("discountType", "PERCENTAGE"),
                Map.entry("discountValue", 20),
                Map.entry("couponCode", "DIWALI20"),
                Map.entry("validFrom", java.time.Instant.now().minusSeconds(60).toString()),
                Map.entry("validTo", java.time.Instant.now().plus(java.time.Duration.ofDays(30)).toString()),
                Map.entry("redemptionLimitPerUser", 1)));
        return o.get("id").asText();
    }

    /** Blocks until the notification outbox has drained (poller runs every ~1s in the test profile). */
    protected void awaitOutboxDrained() {
        for (int i = 0; i < 40; i++) {
            Integer pending = jdbcTemplate.queryForObject(
                    "select count(*) from notification_outbox where status = 'PENDING'", Integer.class);
            if (pending != null && pending == 0) {
                try { Thread.sleep(300); } catch (InterruptedException ignored) { }
                return;
            }
            try { Thread.sleep(250); } catch (InterruptedException ignored) { }
        }
        throw new IllegalStateException("outbox did not drain");
    }

    protected long notificationCount(String userId, String templatePrefix, String status) {
        return jdbcTemplate.queryForObject(
                "select count(*) from notification where user_id = ?::uuid and template like ? and status = ?",
                Long.class, userId, templatePrefix + "%", status);
    }
}
