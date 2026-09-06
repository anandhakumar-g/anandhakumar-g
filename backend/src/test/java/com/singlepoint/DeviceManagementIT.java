package com.singlepoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.singlepoint.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.junit.jupiter.api.Assertions.*;

/** MVP-13 (C3): a user can list the devices signed in to their account and sign any of them out. */
class DeviceManagementIT extends IntegrationTestBase {

    private static final String PHONE = "+919888100301";

    private JsonNode devices(String token, String deviceId) {
        return httpWithDevice(HttpMethod.GET, "/api/v1/me/devices", token, null, deviceId).getBody();
    }

    @Test
    void listRevokeAndRevokeOthers() {
        String a = loginWithDevice(PHONE, "devA").token();
        String b = loginWithDevice(PHONE, "devB").token();

        JsonNode list = devices(a, "devA");
        assertEquals(2, list.size());
        assertTrue(anyMatch(list, "devA is current", n -> n.get("current").asBoolean() && !n.get("revoked").asBoolean()));

        String devBId = idOfOther(list);
        assertEquals(204, httpWithDevice(HttpMethod.POST, "/api/v1/me/devices/" + devBId + "/revoke", a, null, "devA")
                .getStatusCode().value());

        // devB is kicked, devA still works
        assertEquals(401, httpWithDevice(HttpMethod.GET, "/api/v1/me", b, null, "devB").getStatusCode().value());
        assertEquals(200, httpWithDevice(HttpMethod.GET, "/api/v1/me", a, null, "devA").getStatusCode().value());

        // a fresh OTP sign-in on devB un-revokes it...
        String b2 = loginWithDevice(PHONE, "devB").token();
        assertEquals(200, httpWithDevice(HttpMethod.GET, "/api/v1/me", b2, null, "devB").getStatusCode().value());

        // ...then revoke-others from devA kills devB again but keeps devA
        assertEquals(204, httpWithDevice(HttpMethod.POST, "/api/v1/me/devices/revoke-others", a, null, "devA")
                .getStatusCode().value());
        assertEquals(401, httpWithDevice(HttpMethod.GET, "/api/v1/me", b2, null, "devB").getStatusCode().value());
        assertEquals(200, httpWithDevice(HttpMethod.GET, "/api/v1/me", a, null, "devA").getStatusCode().value());
    }

    private static boolean anyMatch(JsonNode arr, String why, java.util.function.Predicate<JsonNode> p) {
        for (JsonNode n : arr) if (p.test(n)) return true;
        fail(why);
        return false;
    }

    private static String idOfOther(JsonNode arr) {
        for (JsonNode n : arr) if (!n.get("current").asBoolean()) return n.get("id").asText();
        throw new IllegalStateException("no non-current device");
    }
}
