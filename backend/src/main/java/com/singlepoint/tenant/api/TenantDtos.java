package com.singlepoint.tenant.api;

import com.singlepoint.tenant.domain.Tenant;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.UUID;

public final class TenantDtos {

    private TenantDtos() { }

    public record TenantCard(UUID id, String name, String city, String locality, String logoUrl) {
        public static TenantCard from(Tenant t) {
            return new TenantCard(t.getId(), t.getName(), t.getCity(), t.getLocality(), t.getLogoUrl());
        }
    }

    public record CreateTenantRequest(@NotBlank @Size(max = 160) String name,
                                      String city, String locality, String address, String pincode,
                                      String logoUrl, String defaultTheme, String brandPrimaryColor,
                                      Integer reopenWindowHours) { }

    public record CreateAdminRequest(@NotBlank String phone, @NotBlank @Size(max = 160) String name) { }

    public record TenantHealth(UUID id, String name, String city, long openTickets, long totalTickets) { }

    /** Sparse update — any null field is left unchanged. Super Admin only. */
    public record UpdateTenantRequest(@Size(max = 160) String name, String city, String locality,
                                      String address, String pincode, String logoUrl,
                                      String defaultTheme, String brandPrimaryColor,
                                      Integer reopenWindowHours, Boolean requireAllocationApproval,
                                      String categoryAdmin, Boolean directServiceEnabled) { }

    /** Community-admin view — only the levers an admin may see/set. */
    public record CommunitySettingsRequest(Integer reopenWindowHours, Boolean requireAllocationApproval,
                                           Boolean directServiceEnabled) { }

    public record TenantSettingsView(UUID id, String name, String city, String locality,
                                     String defaultTheme, String brandPrimaryColor,
                                     int reopenWindowHours, boolean requireAllocationApproval,
                                     String categoryAdmin, boolean directServiceEnabled) {
        public static TenantSettingsView from(Tenant t) {
            return new TenantSettingsView(t.getId(), t.getName(), t.getCity(), t.getLocality(),
                    t.getDefaultTheme(), t.getBrandPrimaryColor(),
                    t.getReopenWindowHours(), t.isRequireAllocationApproval(),
                    t.getCategoryAdmin().name(), t.isDirectServiceEnabled());
        }
    }
}
