package com.singlepoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.singlepoint.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** MVP-8 (B2): an offer can be tagged to a set of communities, or to named individuals by phone. */
class OfferTargetingIT extends IntegrationTestBase {

    private String userId(String token) {
        return get("/api/v1/me", token).get("userId").asText();
    }

    private boolean feedHas(String token, String offerId) {
        for (JsonNode o : get("/api/v1/offers", token)) {
            if (o.get("id").asText().equals(offerId)) return true;
        }
        return false;
    }

    @Test
    void tenantListReachesEveryTargetedCommunity() {
        String su = login(SUPER_ADMIN_PHONE).token();
        UUID tA = createTenant(su, "Alpha", "#2E7D32");
        createAdmin(su, tA, "+919000000101", "A Admin");
        String adminA = login("+919000000101").token();
        UUID tB = createTenant(su, "Beta", "#1565C0");
        createAdmin(su, tB, "+919000000102", "B Admin");
        String adminB = login("+919000000102").token();
        UUID tC = createTenant(su, "Gamma", "#7B1FA2");
        createAdmin(su, tC, "+919000000103", "C Admin");
        String adminC = login("+919000000103").token();

        String rA = joinResident(tA, adminA, "+919888000041", "Ann");
        String rB = joinResident(tB, adminB, "+919888000042", "Ben");
        String rC = joinResident(tC, adminC, "+919888000043", "Cal");

        UUID providerId = createVerifiedProvider(su, "Caterer Co", "+919000000701");
        String providerToken = login("+919000000701").token();
        assertNotNull(providerId);

        String offerId = createDraftOffer(providerToken, VENDOR_CAT_RESTAURANT);
        post("/api/v1/offers/" + offerId + "/submit", providerToken, Map.of("target",
                Map.of("targetType", "TENANT_LIST", "tenantIds", List.of(tA.toString(), tB.toString()))));
        JsonNode approved = post("/api/v1/superadmin/offers/" + offerId + "/approve", su, Map.of());
        assertEquals("ACTIVE", approved.get("status").asText());
        assertEquals("2 communities", approved.get("target").get("summary").asText());

        awaitOutboxDrained();
        assertTrue(notificationCount(userId(rA), "OFFER", "SENT")
                 + notificationCount(userId(rA), "OFFER", "SKIPPED") >= 1);
        assertTrue(notificationCount(userId(rB), "OFFER", "SENT")
                 + notificationCount(userId(rB), "OFFER", "SKIPPED") >= 1);
        assertEquals(0, notificationCount(userId(rC), "OFFER", "SENT")
                       + notificationCount(userId(rC), "OFFER", "SKIPPED"));
    }

    @Test
    void userListTargetsNamedPeopleIncludingACommunitylessOne() {
        var mk = marketplace("+919000000111", "+919000000311");
        String ravi = joinResident(mk.tenantId(), mk.adminToken(), "+919888000051", "Ravi");
        String nomad = individualUser("+919888000052", "Nomad");
        String bystander = joinResident(mk.tenantId(), mk.adminToken(), "+919888000053", "Bystander");

        String offerId = createDraftOffer(mk.providerToken(), VENDOR_CAT_RESTAURANT);
        post("/api/v1/offers/" + offerId + "/submit", mk.providerToken(), Map.of("target",
                Map.of("targetType", "USER_LIST", "phones", List.of("+919888000051", "+919888000052"))));
        JsonNode approved = post("/api/v1/superadmin/offers/" + offerId + "/approve", mk.superToken(), Map.of());
        assertEquals("2 people", approved.get("target").get("summary").asText());
        assertEquals(2, approved.get("target").get("userCount").asInt());

        assertTrue(feedHas(ravi, offerId), "targeted community resident sees it");
        assertTrue(feedHas(nomad, offerId), "targeted community-less user sees it");
        assertFalse(feedHas(bystander, offerId), "a non-targeted resident does not");

        awaitOutboxDrained();
        assertTrue(notificationCount(userId(ravi), "OFFER", "SENT")
                 + notificationCount(userId(ravi), "OFFER", "SKIPPED") >= 1);
    }

    @Test
    void allUnknownPhonesIsRejected() {
        var mk = marketplace("+919000000121", "+919000000321");
        String offerId = createDraftOffer(mk.providerToken(), VENDOR_CAT_RESTAURANT);
        var r = http(HttpMethod.POST, "/api/v1/offers/" + offerId + "/submit", mk.providerToken(),
                Map.of("target", Map.of("targetType", "USER_LIST", "phones", List.of("+910000000000"))));
        assertEquals(400, r.getStatusCode().value());
    }
}
