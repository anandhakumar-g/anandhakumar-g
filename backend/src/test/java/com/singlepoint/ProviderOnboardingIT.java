package com.singlepoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.singlepoint.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** MVP-7 (B): the Super Admin onboards providers; a community admin only enrols verified ones. */
class ProviderOnboardingIT extends IntegrationTestBase {

    private String su;
    private UUID tenant;
    private String admin;

    @BeforeEach
    void fixture() {
        su = login(SUPER_ADMIN_PHONE).token();
        tenant = createTenant(su, "Green Meadows", "#2E7D32");
        createAdmin(su, tenant, "+919000000101", "GM Admin");
        admin = login("+919000000101").token();
    }

    @Test
    void communityAdminCannotCreateOrVerifyProviders() {
        var create = http(HttpMethod.POST, "/api/v1/admin/providers", admin, Map.of(
                "name", "X", "vendorCategoryId", VENDOR_CAT_ELECTRICAL, "company", true, "contactPhone", "+919000000301"));
        assertTrue(create.getStatusCode().value() >= 400, "admin create is gone");
    }

    @Test
    void catalogRequiresThePermissionFlag() {
        assertEquals(403, http(HttpMethod.GET, "/api/v1/admin/providers/catalog", admin, null)
                .getStatusCode().value());
    }

    @Test
    void superAdminOnboardsAndAdminEnrols() {
        JsonNode created = post("/api/v1/superadmin/providers", su, Map.of(
                "name", "Sparky Electricals", "vendorCategoryId", VENDOR_CAT_ELECTRICAL,
                "company", true, "contactPhone", "+919000000301"));
        UUID providerId = UUID.fromString(created.get("id").asText());
        assertEquals("PENDING_VERIFICATION", created.get("verificationStatus").asText());

        // verify without docs -> 422
        assertEquals(422, http(HttpMethod.POST, "/api/v1/superadmin/providers/" + providerId + "/verify", su,
                Map.of("status", "VERIFIED")).getStatusCode().value());

        for (String docType : new String[]{"GOV_ID", "ADDRESS_PROOF", "COMPANY_REG"}) {
            jdbcTemplate.update(
                    "insert into provider_kyc_document (service_provider_id, doc_type, storage_key, content_type, "
                    + "size_bytes, status, reviewed_at) values (?::uuid, ?, ?, 'text/plain', 0, 'ACCEPTED', now())",
                    providerId.toString(), docType, "seed/kyc/" + providerId + "/" + docType);
        }
        assertEquals("VERIFIED", post("/api/v1/superadmin/providers/" + providerId + "/verify", su,
                Map.of("status", "VERIFIED")).get("verificationStatus").asText());

        // enrol needs the permission flag
        assertEquals(403, http(HttpMethod.POST, "/api/v1/admin/providers/" + providerId + "/enrol", admin, Map.of())
                .getStatusCode().value());

        enableProviderOnboarding(su, tenant);
        JsonNode catalog = get("/api/v1/admin/providers/catalog", admin);
        assertEquals(1, catalog.size());
        assertEquals(providerId.toString(), catalog.get(0).get("id").asText());

        assertEquals(201, http(HttpMethod.POST, "/api/v1/admin/providers/" + providerId + "/enrol", admin, Map.of())
                .getStatusCode().value());
        JsonNode enrolled = get("/api/v1/admin/providers", admin);
        assertEquals(1, enrolled.size());
        assertEquals(providerId.toString(), enrolled.get(0).get("id").asText());
        assertTrue(enrolled.get(0).get("assignable").asBoolean());
    }

    @Test
    void cannotEnrolAnUnverifiedProvider() {
        JsonNode pending = post("/api/v1/superadmin/providers", su, Map.of(
                "name", "Pending Co", "vendorCategoryId", VENDOR_CAT_ELECTRICAL,
                "company", false, "contactPhone", "+919000000302"));
        UUID id = UUID.fromString(pending.get("id").asText());
        enableProviderOnboarding(su, tenant);
        var r = http(HttpMethod.POST, "/api/v1/admin/providers/" + id + "/enrol", admin, Map.of());
        assertEquals(422, r.getStatusCode().value());
    }
}
