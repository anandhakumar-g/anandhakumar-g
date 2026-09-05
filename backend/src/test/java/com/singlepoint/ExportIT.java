package com.singlepoint;

import com.singlepoint.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** MVP-12 (A): CSV export endpoints — content type, header row, filters, RBAC. */
class ExportIT extends IntegrationTestBase {

    private static final char BOM = '﻿';

    private Marketplace mk;
    private String resident;

    @BeforeEach
    void fixture() {
        mk = marketplace("+919000000101", "+919000000301");
        resident = joinResident(mk.tenantId(), mk.adminToken(), "+919888000041", "Rita");
        String cat = firstCategoryId(resident, "Electrical");
        // one open, one resolved + paid
        post("/api/v1/tickets", resident, Map.of("categoryId", cat, "description", "open one", "serviceAddressText", "A-1"));
        String tid = post("/api/v1/tickets", resident, Map.of("categoryId", cat, "description", "resolve me", "serviceAddressText", "A-2")).get("id").asText();
        post("/api/v1/tickets/" + tid + "/assign", mk.adminToken(), Map.of("providerId", mk.providerId().toString()));
        post("/api/v1/tickets/" + tid + "/accept", mk.providerToken(), Map.of());
        post("/api/v1/tickets/" + tid + "/status", mk.providerToken(), Map.of("toStatus", "IN_PROGRESS"));
        post("/api/v1/tickets/" + tid + "/status", mk.providerToken(), Map.of("toStatus", "RESOLVED", "resolutionNotes", "done"));
        post("/api/v1/tickets/" + tid + "/payment/charge", mk.adminToken(), Map.of("amount", 250, "note", "parts"));
        String offerId = createDraftOffer(mk.providerToken(), VENDOR_CAT_ELECTRICAL);
        post("/api/v1/offers/" + offerId + "/submit", mk.providerToken(), Map.of("target", Map.of("targetType", "ALL_TENANTS")));
        post("/api/v1/superadmin/offers/" + offerId + "/approve", mk.superToken(), Map.of());
    }

    private ResponseEntity<String> csv(String path, String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(h), String.class);
    }

    private static String noBom(String body) {
        return !body.isEmpty() && body.charAt(0) == BOM ? body.substring(1) : body;
    }

    private static int dataRows(String body) {
        String[] lines = noBom(body).strip().split("\r\n");
        return lines.length - 1; // minus the header row
    }

    @Test
    void adminTicketExportHasHeaderRowsAndFilters() {
        ResponseEntity<String> all = csv("/api/v1/admin/exports/tickets.csv", mk.adminToken());
        assertEquals(200, all.getStatusCode().value());
        assertTrue(all.getHeaders().getContentType().toString().startsWith("text/csv"));
        assertTrue(all.getHeaders().getFirst("Content-Disposition").contains("attachment"));
        assertTrue(noBom(all.getBody()).startsWith("reference,status,"));
        assertEquals(2, dataRows(all.getBody()));

        assertEquals(1, dataRows(csv("/api/v1/admin/exports/tickets.csv?status=RESOLVED", mk.adminToken()).getBody()));
        assertEquals(0, dataRows(csv("/api/v1/admin/exports/tickets.csv?from=2099-01-01T00:00:00Z", mk.adminToken()).getBody()));
    }

    @Test
    void adminMembersAndPaymentsExport() {
        assertEquals(1, dataRows(csv("/api/v1/admin/exports/members.csv", mk.adminToken()).getBody()));
        assertEquals(1, dataRows(csv("/api/v1/admin/exports/payments.csv", mk.adminToken()).getBody()));
    }

    @Test
    void superAdminExportsSpanThePlatform() {
        assertTrue(dataRows(csv("/api/v1/superadmin/exports/tickets.csv", mk.superToken()).getBody()) >= 2);
        assertTrue(dataRows(csv("/api/v1/superadmin/exports/offers.csv", mk.superToken()).getBody()) >= 1);
        assertTrue(dataRows(csv("/api/v1/superadmin/exports/communities.csv", mk.superToken()).getBody()) >= 1);
    }

    @Test
    void aResidentCannotExport() {
        assertEquals(403, csv("/api/v1/admin/exports/tickets.csv", resident).getStatusCode().value());
        assertEquals(403, csv("/api/v1/superadmin/exports/offers.csv", mk.adminToken()).getStatusCode().value());
    }
}
