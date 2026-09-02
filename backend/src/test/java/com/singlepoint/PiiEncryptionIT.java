package com.singlepoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.singlepoint.crypto.PiiBackfillRunner;
import com.singlepoint.security.TenantContext;
import com.singlepoint.security.TenantScopedExecutor;
import com.singlepoint.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** MVP-5 (D): ticket free-text is encrypted at rest; the service address is masked for non-parties. */
class PiiEncryptionIT extends IntegrationTestBase {

    @Autowired TenantScopedExecutor tenantScoped;

    private Marketplace mk;
    private String residentToken;

    @BeforeEach
    void fixture() {
        mk = marketplace("+919000000101", "+919000000301");
        residentToken = joinResident(mk.tenantId(), mk.adminToken(), "+919888000010", "Ravi");
    }

    private String raise(String description, String landmark) {
        return post("/api/v1/tickets", residentToken, Map.of(
                "categoryId", firstCategoryId(residentToken, "Electrical"),
                "description", description, "serviceAddressText", "Flat A-101, Green Meadows",
                "serviceLandmark", landmark)).get("id").asText();
    }

    private <T> T wildcard(java.util.function.Supplier<T> q) {
        TenantContext.setWildcard();
        try {
            return q.get();
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void descriptionIsCiphertextAtRestAndRoundTripsForTheRaiser() {
        String id = raise("The main distribution board keeps tripping every evening", "near lift lobby");

        var row = wildcard(() -> jdbcTemplate.queryForMap(
                "select description, description_enc from ticket where id = ?::uuid", id));
        assertNull(row.get("description"), "legacy plaintext column not written");
        String enc = (String) row.get("description_enc");
        assertNotNull(enc);
        assertFalse(enc.contains("distribution board"));
        assertEquals(1, Base64.getDecoder().decode(enc)[0], "envelope version byte");

        JsonNode view = get("/api/v1/tickets/" + id, residentToken);
        assertEquals("The main distribution board keeps tripping every evening", view.get("description").asText());
        assertEquals("Flat A-101, Green Meadows", view.get("serviceAddressText").asText());
    }

    @Test
    void serviceAddressIsMaskedForANonEngagedProvider() {
        String id = raise("fix the light", "opp. park");
        post("/api/v1/tickets/" + id + "/assign", mk.adminToken(), Map.of("providerId", mk.providerId().toString()));
        post("/api/v1/tickets/" + id + "/accept", mk.providerToken(), Map.of());
        post("/api/v1/tickets/" + id + "/status", mk.providerToken(), Map.of("toStatus", "IN_PROGRESS"));
        post("/api/v1/tickets/" + id + "/status", mk.providerToken(),
                Map.of("toStatus", "RESOLVED", "resolutionNotes", "done"));

        // engaged (RESOLVED, not CLOSED) → provider still sees the address
        assertEquals("Flat A-101, Green Meadows",
                get("/api/v1/tickets/" + id, mk.providerToken()).get("serviceAddressText").asText());

        post("/api/v1/tickets/" + id + "/close", residentToken, Map.of("rating", 4));

        JsonNode afterClose = get("/api/v1/tickets/" + id, mk.providerToken());
        assertFalse(afterClose.hasNonNull("serviceAddressText"), "address hidden once the job is closed");
        assertEquals("opp. park", afterClose.get("serviceLandmark").asText(), "landmark still shown");
    }

    @Test
    void readingATicketWritesAnAuditRow() {
        String id = raise("audit me", "x");
        get("/api/v1/tickets/" + id, mk.adminToken());

        Map<String, Object> audit = wildcard(() -> jdbcTemplate.queryForMap(
                "select entity_type, entity_id, action from audit_log "
                + "where entity_type = 'ticket' and entity_id = ?::uuid order by created_at desc limit 1", id));
        assertEquals("ticket", audit.get("entity_type"));
        assertEquals(id, audit.get("entity_id").toString());
        assertTrue(((String) audit.get("action")).contains("TicketController#get"));
    }

    @Test
    void backfillRunnerEncryptsLegacyPlaintextRows() {
        String id = raise("new row already encrypted", "y");
        // simulate a legacy row: plaintext present, ciphertext missing
        wildcard(() -> jdbcTemplate.update(
                "update ticket set description = 'legacy plaintext value', description_enc = null where id = ?::uuid", id));

        new PiiBackfillRunner(jdbcTemplate, crypto, tenantScoped).run(null);

        String enc = wildcard(() -> jdbcTemplate.queryForObject(
                "select description_enc from ticket where id = ?::uuid", String.class, id));
        assertNotNull(enc);
        assertEquals("legacy plaintext value", crypto.decrypt(enc));
    }
}
