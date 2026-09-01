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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

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
    /** Seeded global vendor category "Electrical" (V3). */
    public static final String VENDOR_CAT_ELECTRICAL = "22222222-0000-0000-0000-000000000001";

    /** All application tables are wiped before each test (Flyway history is kept). */
    private static final String TRUNCATE_SQL = """
            TRUNCATE TABLE
              audit_log, notification, notification_outbox, otp_challenge, device_token,
              ticket_status_history, ticket_attachment, ticket,
              tenant_service_provider, service_provider,
              invite_code, user_tenant_membership, flat, app_user, tenant,
              category, vendor_category
            RESTART IDENTITY CASCADE
            """;

    /** Minimal taxonomy the tests reference — reseeded each test so runs don't depend on V3 state. */
    private static final String SEED_TAXONOMY_SQL = """
            INSERT INTO category (id, tenant_id, name, request_type, sla_hours, sort_order, active) VALUES
              ('11111111-0000-0000-0000-000000000001', NULL, 'Electrical', 'ISSUE', 24, 10, true),
              ('11111111-0000-0000-0000-000000000002', NULL, 'Plumbing',   'ISSUE', 24, 20, true),
              ('11111111-0000-0000-0000-00000000000d', NULL, 'Enquiry',    'ENQUIRY', NULL, 130, true);
            INSERT INTO vendor_category (id, name, kind, sort_order, active) VALUES
              ('22222222-0000-0000-0000-000000000001', 'Electrical', 'MAINTENANCE', 10, true),
              ('22222222-0000-0000-0000-000000000002', 'Plumbing',   'MAINTENANCE', 20, true);
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

    protected void createAdmin(String superToken, UUID tenantId, String phone, String name) {
        post("/api/v1/superadmin/tenants/" + tenantId + "/admins", superToken,
                Map.of("phone", phone, "name", name));
    }

    protected String createInvite(String adminToken) {
        JsonNode c = post("/api/v1/admin/invite-codes", adminToken, Map.of("maxUses", 20, "validDays", 30));
        return c.get("code").asText();
    }

    protected UUID createVerifiedProvider(String adminToken, String superOrAdminToken, String name, String phone) {
        JsonNode p = post("/api/v1/admin/providers", adminToken,
                Map.of("name", name, "vendorCategoryId", VENDOR_CAT_ELECTRICAL, "company", true, "contactPhone", phone));
        UUID id = UUID.fromString(p.get("id").asText());
        JsonNode v = post("/api/v1/admin/providers/" + id + "/verify", adminToken, Map.of("status", "VERIFIED"));
        assertEquals("VERIFIED", v.get("verificationStatus").asText());
        return id;
    }

    protected String firstCategoryId(String token, String name) {
        for (JsonNode c : get("/api/v1/categories", token)) {
            if (c.get("name").asText().equals(name)) return c.get("id").asText();
        }
        throw new IllegalStateException("category not found: " + name);
    }
}
