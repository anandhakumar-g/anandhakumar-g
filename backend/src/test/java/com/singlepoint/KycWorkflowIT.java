package com.singlepoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.singlepoint.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** MVP-7: provider KYC review + verification is Super-Admin territory. */
class KycWorkflowIT extends IntegrationTestBase {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3, 4};

    @Test
    void uploadReviewVerifyFlow() {
        String su = login(SUPER_ADMIN_PHONE).token();
        UUID tenant = createTenant(su, "Green Meadows", "#2E7D32");
        createAdmin(su, tenant, "+919000000101", "GM Admin");
        String admin = login("+919000000101").token();

        JsonNode prov = post("/api/v1/superadmin/providers", su, Map.of(
                "name", "Sparky Electricals", "vendorCategoryId", VENDOR_CAT_ELECTRICAL,
                "company", true, "contactPhone", "+919000000301"));
        UUID providerId = UUID.fromString(prov.get("id").asText());
        String providerToken = login("+919000000301").token();

        // verify with no accepted docs -> 422 SP-422-KYC
        var early = http(HttpMethod.POST, "/api/v1/superadmin/providers/" + providerId + "/verify", su,
                Map.of("status", "VERIFIED"));
        assertEquals(422, early.getStatusCode().value());
        assertEquals("SP-422-KYC", early.getBody().get("errorCode").asText());

        // provider uploads the three documents
        for (String type : new String[]{"GOV_ID", "ADDRESS_PROOF", "COMPANY_REG"}) {
            var up = multipart("/api/v1/provider/kyc", providerToken, Map.of("docType", type),
                    "file", type.toLowerCase() + ".png", PNG);
            assertEquals(201, up.getStatusCode().value(), up.getBody() + "");
            assertEquals("PENDING", up.getBody().get("status").asText());
        }

        // a community admin cannot review KYC or verify — that authority moved to the Super Admin
        assertEquals(403, http(HttpMethod.GET,
                "/api/v1/superadmin/providers/" + providerId + "/kyc", admin, null).getStatusCode().value());
        assertEquals(403, http(HttpMethod.POST,
                "/api/v1/superadmin/providers/" + providerId + "/verify", admin,
                Map.of("status", "VERIFIED")).getStatusCode().value());

        JsonNode docs = get("/api/v1/superadmin/providers/" + providerId + "/kyc", su);
        assertEquals(3, docs.size());

        String firstDocId = docs.get(0).get("id").asText();
        ResponseEntity<byte[]> file = rest.exchange(
                "/api/v1/superadmin/providers/" + providerId + "/kyc/" + firstDocId + "/file",
                HttpMethod.GET, authGet(su), byte[].class);
        assertEquals(200, file.getStatusCode().value());

        // accept all three, then verification succeeds
        for (JsonNode d : docs) {
            post("/api/v1/superadmin/providers/" + providerId + "/kyc/" + d.get("id").asText() + "/review",
                    su, Map.of("status", "ACCEPTED", "note", "looks good"));
        }
        JsonNode verified = post("/api/v1/superadmin/providers/" + providerId + "/verify", su,
                Map.of("status", "VERIFIED"));
        assertEquals("VERIFIED", verified.get("verificationStatus").asText());
        assertTrue(verified.get("assignable").asBoolean());
    }

    private org.springframework.http.HttpEntity<Void> authGet(String token) {
        org.springframework.http.HttpHeaders h = new org.springframework.http.HttpHeaders();
        h.setBearerAuth(token);
        return new org.springframework.http.HttpEntity<>(h);
    }
}
