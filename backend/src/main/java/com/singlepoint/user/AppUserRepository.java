package com.singlepoint.user;

import com.singlepoint.user.domain.AppUser;
import com.singlepoint.user.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    /** MVP-9: all account ids (broadcast to ALL_USERS). */
    @Query("select u.id from AppUser u")
    List<UUID> findAllIds();

    Optional<AppUser> findByPhoneHash(String phoneHash);

    boolean existsByPhoneHash(String phoneHash);

    List<AppUser> findByRoleAndCurrentTenantId(Role role, UUID currentTenantId);

    long countByRoleAndCurrentTenantId(Role role, UUID currentTenantId);

    boolean existsByRole(Role role);
}
