package com.singlepoint.category;

import com.singlepoint.category.domain.Category;
import com.singlepoint.common.error.AppException;
import com.singlepoint.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * CRUD over ticket categories in a fixed scope: {@code scopeTenantId == null} is the platform-wide
 * catalogue, otherwise a single community's own list. The Super Admin editor uses both; the
 * community editor is pinned to its own tenant.
 */
@Service
public class TicketCategoryAdminService {

    private final CategoryRepository categories;

    public TicketCategoryAdminService(CategoryRepository categories) {
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public List<Category> list(UUID scopeTenantId) {
        return scopeTenantId == null
                ? categories.findByTenantIdIsNullOrderBySortOrderAsc()
                : categories.findByTenantIdOrderBySortOrderAsc(scopeTenantId);
    }

    @Transactional
    public Category create(UUID scopeTenantId, String name, String requestType, String parentCategoryId,
                           Integer slaHours, String defaultProviderKind, Integer sortOrder) {
        String trimmed = name.trim();
        boolean dup = scopeTenantId == null
                ? categories.existsByTenantIdIsNullAndNameIgnoreCase(trimmed)
                : categories.existsByTenantIdAndNameIgnoreCase(scopeTenantId, trimmed);
        if (dup) throw new AppException(ErrorCode.CONFLICT, "A category with that name already exists here");

        Category c = new Category();
        c.setTenantId(scopeTenantId);
        c.setName(trimmed);
        c.setRequestType(parseRequestType(requestType));
        c.setParentCategoryId(resolveParent(parentCategoryId, scopeTenantId));
        c.setSlaHours(slaHours);
        c.setDefaultProviderKind(blankToNull(defaultProviderKind));
        if (sortOrder != null) c.setSortOrder(sortOrder);
        return categories.save(c);
    }

    @Transactional
    public Category update(UUID scopeTenantId, UUID id, String name, String requestType, String parentCategoryId,
                           Integer slaHours, String defaultProviderKind, Integer sortOrder) {
        Category c = requireInScope(scopeTenantId, id);
        if (name != null && !name.isBlank()) c.setName(name.trim());
        if (requestType != null) c.setRequestType(parseRequestType(requestType));
        if (parentCategoryId != null) {
            c.setParentCategoryId(parentCategoryId.isBlank() ? null : resolveParent(parentCategoryId, scopeTenantId));
        }
        if (slaHours != null) c.setSlaHours(slaHours);
        if (defaultProviderKind != null) c.setDefaultProviderKind(blankToNull(defaultProviderKind));
        if (sortOrder != null) c.setSortOrder(sortOrder);
        return categories.save(c);
    }

    @Transactional
    public Category setActive(UUID scopeTenantId, UUID id, boolean active) {
        Category c = requireInScope(scopeTenantId, id);
        c.setActive(active);
        return categories.save(c);
    }

    private Category requireInScope(UUID scopeTenantId, UUID id) {
        Category c = categories.findById(id).orElseThrow(() -> AppException.notFound("Category"));
        boolean inScope = scopeTenantId == null ? c.getTenantId() == null : scopeTenantId.equals(c.getTenantId());
        if (!inScope) throw AppException.notFound("Category");
        return c;
    }

    private UUID resolveParent(String parentCategoryId, UUID scopeTenantId) {
        if (parentCategoryId == null || parentCategoryId.isBlank()) return null;
        UUID pid = UUID.fromString(parentCategoryId);
        Category parent = categories.findById(pid)
                .orElseThrow(() -> new AppException(ErrorCode.VALIDATION_FAILED, "Unknown parent category"));
        // a category may nest under a global parent or one in the same scope
        if (parent.getTenantId() != null && !parent.getTenantId().equals(scopeTenantId)) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Parent category is not in this scope");
        }
        return pid;
    }

    private static Category.RequestType parseRequestType(String raw) {
        try {
            return Category.RequestType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "requestType must be ISSUE, FEEDBACK or ENQUIRY");
        }
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
