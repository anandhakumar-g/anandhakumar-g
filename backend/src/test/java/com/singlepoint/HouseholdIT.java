package com.singlepoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.singlepoint.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** MVP-6 (C): multi-flat membership, PRIMARY/SECONDARY roles, household invite codes, flat-scoped raise. */
class HouseholdIT extends IntegrationTestBase {

    private String su;
    private UUID tenantA;
    private String adminA;
    private UUID flat101;
    private String primaryToken;

    @BeforeEach
    void fixture() {
        su = login(SUPER_ADMIN_PHONE).token();
        tenantA = createTenant(su, "Green Meadows", "#2E7D32");
        createAdmin(su, tenantA, "+919000000101", "GM Admin");
        adminA = login("+919000000101").token();
        flat101 = createFlat(adminA, "A", "101");

        String code = createFlatInvite(adminA, flat101);
        primaryToken = completeProfile(login("+919888000010").token(), "Ravi", "ravi@example.com").token();
        primaryToken = toSession(post("/api/v1/memberships/join", primaryToken,
                Map.of("tenantId", tenantA.toString(), "inviteCode", code))).token();
    }

    private String meUserId(String token) {
        return get("/api/v1/me", token).get("userId").asText();
    }

    @Test
    void firstJoinerOfAFlatIsPrimary() {
        JsonNode me = get("/api/v1/me", primaryToken);
        JsonNode membership = me.get("memberships").get(0);
        assertEquals("PRIMARY", membership.get("householdRole").asText());
        assertEquals(flat101.toString(), membership.get("flatId").asText());
    }

    @Test
    void primaryInvitesAFamilyMemberAsSecondaryWithNoAdminStep() {
        JsonNode code = post("/api/v1/me/household/invites", primaryToken, Map.of("flatId", flat101.toString()));
        String household = code.get("code").asText();

        String familyToken = completeProfile(login("+919888000011").token(), "Sita", "sita@example.com").token();
        JsonNode joined = post("/api/v1/memberships/join", familyToken,
                Map.of("tenantId", tenantA.toString(), "inviteCode", household));
        assertEquals("READY", joined.get("onboardingState").asText());

        JsonNode roster = get("/api/v1/me/household/" + flat101 + "/members", primaryToken);
        assertEquals(2, roster.size());
        assertEquals("PRIMARY", roster.get(0).get("householdRole").asText());
        JsonNode secondary = roster.get(1);
        assertEquals("SECONDARY", secondary.get("householdRole").asText());

        // a SECONDARY cannot manage the roster
        var denyInvite = http(HttpMethod.POST, "/api/v1/me/household/invites", familyToken,
                Map.of("flatId", flat101.toString()));
        assertEquals(403, denyInvite.getStatusCode().value());
        var denyRemove = http(HttpMethod.POST,
                "/api/v1/me/household/" + flat101 + "/members/" + meUserId(primaryToken) + "/remove",
                familyToken, Map.of());
        assertEquals(403, denyRemove.getStatusCode().value());

        // PRIMARY removes the SECONDARY
        JsonNode after = post("/api/v1/me/household/" + flat101 + "/members/" + secondary.get("userId").asText()
                + "/remove", primaryToken, Map.of());
        assertEquals(1, after.size());
    }

    @Test
    void aFamilyMemberRaisesForTheirFlat() {
        String household = post("/api/v1/me/household/invites", primaryToken,
                Map.of("flatId", flat101.toString())).get("code").asText();
        String familyToken = completeProfile(login("+919888000012").token(), "Anil", "anil@example.com").token();
        familyToken = toSession(post("/api/v1/memberships/join", familyToken,
                Map.of("tenantId", tenantA.toString(), "inviteCode", household))).token();

        String cat = firstCategoryId(familyToken, "Electrical");
        // flatId is required now that the caller has a flat membership
        var noFlat = http(HttpMethod.POST, "/api/v1/tickets", familyToken, Map.of(
                "categoryId", cat, "description", "x", "serviceAddressText", "A-101"));
        assertEquals(400, noFlat.getStatusCode().value());

        JsonNode t = post("/api/v1/tickets", familyToken, Map.of(
                "categoryId", cat, "description", "geyser leak", "flatId", flat101.toString()));
        assertEquals("A - 101", t.get("flatLabel").asText());
    }

    @Test
    void raisingForAFlatThatIsntYoursIs403() {
        UUID otherFlat = createFlat(adminA, "B", "202");
        String cat = firstCategoryId(primaryToken, "Electrical");
        var r = http(HttpMethod.POST, "/api/v1/tickets", primaryToken, Map.of(
                "categoryId", cat, "description", "x", "flatId", otherFlat.toString(),
                "serviceAddressText", "B-202"));
        assertEquals(403, r.getStatusCode().value());
    }

    @Test
    void oneUserHoldsFlatsInTwoCommunities() {
        UUID tenantB = createTenant(su, "Lakeview", "#1565C0");
        createAdmin(su, tenantB, "+919000000201", "LV Admin");
        String adminB = login("+919000000201").token();
        UUID flatB = createFlat(adminB, "C", "1");
        String codeB = createFlatInvite(adminB, flatB);
        post("/api/v1/memberships/join", primaryToken, Map.of("tenantId", tenantB.toString(), "inviteCode", codeB));

        assertEquals(2, get("/api/v1/me", primaryToken).get("memberships").size());

        // active community is still A → only A's flat
        assertEquals(1, get("/api/v1/me/flats", primaryToken).size());
        String bToken = post("/api/v1/me/active-community", primaryToken, Map.of("tenantId", tenantB.toString()))
                .get("token").asText();
        JsonNode bFlats = get("/api/v1/me/flats", bToken);
        assertEquals(1, bFlats.size());
        assertEquals(flatB.toString(), bFlats.get(0).get("flatId").asText());
    }
}
