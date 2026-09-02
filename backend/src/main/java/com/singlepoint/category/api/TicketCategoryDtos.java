package com.singlepoint.category.api;

import com.singlepoint.category.domain.Category;

import javax.validation.constraints.NotBlank;
import java.util.UUID;

/** Shared DTOs for the Super Admin + community ticket-category editors. */
public final class TicketCategoryDtos {

    private TicketCategoryDtos() { }

    public record CategoryView(UUID id, UUID tenantId, String name, String requestType,
                               UUID parentCategoryId, Integer slaHours, String defaultProviderKind,
                               int sortOrder, boolean active) {
        public static CategoryView of(Category c) {
            return new CategoryView(c.getId(), c.getTenantId(), c.getName(), c.getRequestType().name(),
                    c.getParentCategoryId(), c.getSlaHours(), c.getDefaultProviderKind(),
                    c.getSortOrder(), c.isActive());
        }
    }

    public record CreateRequest(@NotBlank String name, @NotBlank String requestType,
                                String parentCategoryId, Integer slaHours, String defaultProviderKind,
                                Integer sortOrder) { }

    /** Sparse — any null field is left unchanged. */
    public record UpdateRequest(String name, String requestType, String parentCategoryId,
                                Integer slaHours, String defaultProviderKind, Integer sortOrder) { }
}
