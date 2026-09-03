package com.singlepoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.singlepoint.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** MVP-7 (P4/A6): an ADMIN or PROVIDER who owns a flat raises a request for it as the raiser. */
class FlatOwnerRaiseIT extends IntegrationTestBase {

    private Marketplace mk;
    private UUID myFlat;
    private String catId;

    @BeforeEach
    void fixture() {
        mk = marketplace("+919000000101", "+919000000301");
        myFlat = createFlat(mk.adminToken(), "A", "101");
        String code = createFlatInvite(mk.adminToken(), myFlat);
        // the admin joins their own flat
        post("/api/v1/memberships/join", mk.adminToken(),
                Map.of("tenantId", mk.tenantId().toString(), "inviteCode", code));
        catId = firstCategoryId(mk.adminToken(), "Electrical");
    }

    @Test
    void adminOwnerRaisesTracksAndCloses() {
        String adminName = get("/api/v1/me", mk.adminToken()).get("name").asText();
        JsonNode t = post("/api/v1/tickets", mk.adminToken(), Map.of(
                "categoryId", catId, "description", "kitchen plug", "flatId", myFlat.toString(),
                "serviceAddressText", "A-101"));
        assertEquals("NEW", t.get("status").asText());
        assertEquals(adminName, t.get("raisedBy").get("name").asText());

        String id = t.get("id").asText();
        boolean listed = false;
        for (JsonNode row : get("/api/v1/tickets", mk.adminToken()).get("content")) {
            if (row.get("id").asText().equals(id)) listed = true;
        }
        assertTrue(listed);

        post("/api/v1/tickets/" + id + "/resolve", mk.adminToken(), Map.of("resolutionNotes", "done"));
        JsonNode closed = post("/api/v1/tickets/" + id + "/close", mk.adminToken(), Map.of("rating", 5));
        assertEquals("CLOSED", closed.get("status").asText());
    }

    @Test
    void nonResidentMustNameAFlatTheyOwn() {
        // no flatId at all -> 403
        var noFlat = http(HttpMethod.POST, "/api/v1/tickets", mk.adminToken(), Map.of(
                "categoryId", catId, "description", "x", "serviceAddressText", "y"));
        assertEquals(403, noFlat.getStatusCode().value());

        // a flat the admin created but is not a member of -> 403
        UUID notMine = createFlat(mk.adminToken(), "B", "202");
        var notOwned = http(HttpMethod.POST, "/api/v1/tickets", mk.adminToken(), Map.of(
                "categoryId", catId, "description", "x", "flatId", notMine.toString(),
                "serviceAddressText", "y"));
        assertEquals(403, notOwned.getStatusCode().value());
    }

    @Test
    void providerOwnerSeesTheirSelfRaisedTicketAlongsideJobs() {
        UUID provFlat = createFlat(mk.adminToken(), "C", "303");
        String code = createFlatInvite(mk.adminToken(), provFlat);
        post("/api/v1/memberships/join", mk.providerToken(),
                Map.of("tenantId", mk.tenantId().toString(), "inviteCode", code));

        String id = post("/api/v1/tickets", mk.providerToken(), Map.of(
                "categoryId", catId, "description", "my own tap", "flatId", provFlat.toString(),
                "serviceAddressText", "C-303")).get("id").asText();

        boolean listed = false;
        for (JsonNode row : get("/api/v1/tickets", mk.providerToken()).get("content")) {
            if (row.get("id").asText().equals(id)) listed = true;
        }
        assertTrue(listed, "provider sees the ticket they raised for their own flat");
    }

    @Test
    void residentRaiseStillWorksUnchanged() {
        String residentToken = joinResident(mk.tenantId(), mk.adminToken(), "+919888000010", "Ravi");
        JsonNode t = post("/api/v1/tickets", residentToken, Map.of(
                "categoryId", firstCategoryId(residentToken, "Electrical"),
                "description", "leak", "serviceAddressText", "Z-1"));
        assertEquals("NEW", t.get("status").asText());
        assertEquals("COMMUNITY_TICKET", t.get("requestMode").asText());
    }
}
