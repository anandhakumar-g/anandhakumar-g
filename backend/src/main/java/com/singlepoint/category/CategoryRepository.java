package com.singlepoint.category;

import com.singlepoint.category.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findByTenantIdIsNullAndActiveTrueOrderBySortOrderAsc();

    /** Whole global catalogue, including inactive — for the Super Admin editor. */
    List<Category> findByTenantIdIsNullOrderBySortOrderAsc();

    /** One tenant's own categories, including inactive — for the Super Admin / community editor. */
    List<Category> findByTenantIdOrderBySortOrderAsc(UUID tenantId);

    /** Active categories a tenant's residents can raise against: the global set plus the tenant's own. */
    @Query("select c from Category c where (c.tenantId is null or c.tenantId = :tenantId) "
            + "and c.active = true order by c.sortOrder asc")
    List<Category> findVisibleForTenant(UUID tenantId);

    @Query("select c from Category c where c.id = :id and c.active = true "
            + "and (c.tenantId is null or c.tenantId = :tenantId)")
    Optional<Category> findActiveForTenantScope(UUID id, UUID tenantId);

    boolean existsByTenantIdIsNullAndNameIgnoreCase(String name);

    boolean existsByTenantIdAndNameIgnoreCase(UUID tenantId, String name);
}
