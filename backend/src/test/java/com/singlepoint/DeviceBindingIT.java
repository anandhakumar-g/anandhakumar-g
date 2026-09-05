package com.singlepoint;

import com.singlepoint.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MVP-10 (B): a session minted with an X-Device-Id claim is rejected on any other device;
 * a session minted with no header is never gated (backward compatible).
 */
class DeviceBindingIT extends IntegrationTestBase {

    @Test
    void unboundTokenWorksWithAnyOrNoDeviceHeader() {
        String token = login("+919888900001").token(); // no X-Device-Id sent

        assertEquals(200, httpWithDevice(HttpMethod.GET, "/api/v1/me", token, null, null)
                .getStatusCode().value());
        assertEquals(200, httpWithDevice(HttpMethod.GET, "/api/v1/me", token, null, "some-other-device")
                .getStatusCode().value());
    }

    @Test
    void boundTokenRejectsAMissingOrDifferentDevice() {
        String token = loginWithDevice("+919888900002", "device-A").token();

        assertEquals(200, httpWithDevice(HttpMethod.GET, "/api/v1/me", token, null, "device-A")
                .getStatusCode().value());

        ResponseEntity<com.fasterxml.jackson.databind.JsonNode> noHeader =
                httpWithDevice(HttpMethod.GET, "/api/v1/me", token, null, null);
        assertEquals(401, noHeader.getStatusCode().value());
        assertEquals("SP-401-DEVICE", noHeader.getBody().get("errorCode").asText());

        assertEquals(401, httpWithDevice(HttpMethod.GET, "/api/v1/me", token, null, "device-B")
                .getStatusCode().value());
    }

    @Test
    void refreshAndActiveCommunityPreserveTheBinding() {
        String token = loginWithDevice("+919888900003", "device-A").token();

        // /auth/refresh re-mints; the new token must still be bound to device-A
        String refreshed = postWithDevice("/api/v1/auth/refresh", token, java.util.Map.of(), "device-A")
                .get("token").asText();
        assertEquals(200, httpWithDevice(HttpMethod.GET, "/api/v1/me", refreshed, null, "device-A")
                .getStatusCode().value());
        assertEquals(401, httpWithDevice(HttpMethod.GET, "/api/v1/me", refreshed, null, "device-B")
                .getStatusCode().value());

        // /auth/profile also refreshes the session (completeProfileAndRefresh)
        String afterProfile = postWithDevice("/api/v1/auth/profile", refreshed,
                        java.util.Map.of("name", "Priya"), "device-A")
                .get("token").asText();
        assertEquals(200, httpWithDevice(HttpMethod.GET, "/api/v1/me", afterProfile, null, "device-A")
                .getStatusCode().value());
        assertEquals(401, httpWithDevice(HttpMethod.GET, "/api/v1/me", afterProfile, null, null)
                .getStatusCode().value());
    }
}
