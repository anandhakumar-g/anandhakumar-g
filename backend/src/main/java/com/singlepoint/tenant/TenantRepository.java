package com.singlepoint.tenant;

import com.singlepoint.tenant.domain.Tenant;
import com.singlepoint.tenant.domain.TenantStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    @Query("""
           select t from Tenant t
           where t.status = :status
             and (:q is null or lower(t.name) like lower(concat('%', :q, '%'))
                              or lower(t.city) like lower(concat('%', :q, '%'))
                              or lower(t.locality) like lower(concat('%', :q, '%')))
           order by t.name asc
           """)
    Page<Tenant> search(@Param("q") String q, @Param("status") TenantStatus status, Pageable pageable);

    /** MVP-11: the self-onboarding review queue. */
    Page<Tenant> findByStatusOrderByCreatedAtDesc(TenantStatus status, Pageable pageable);

    java.util.Optional<Tenant> findFirstByRequestedByUserIdOrderByCreatedAtDesc(UUID requestedByUserId);

    boolean existsByRequestedByUserIdAndStatus(UUID requestedByUserId, TenantStatus status);
}
