package com.singlepoint.user;

import com.singlepoint.common.error.AppException;
import com.singlepoint.crypto.CryptoService;
import com.singlepoint.user.domain.AppUser;
import com.singlepoint.user.domain.MembershipStatus;
import com.singlepoint.user.domain.Role;
import com.singlepoint.user.domain.UserTenantMembership;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    private final AppUserRepository userRepository;
    private final UserTenantMembershipRepository membershipRepository;
    private final CryptoService crypto;

    public UserService(AppUserRepository userRepository,
                       UserTenantMembershipRepository membershipRepository,
                       CryptoService crypto) {
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.crypto = crypto;
    }

    @Transactional(readOnly = true)
    public AppUser require(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> AppException.notFound("User"));
    }

    @Transactional(readOnly = true)
    public Optional<AppUser> findByPhone(String phoneE164) {
        return userRepository.findByPhoneHash(crypto.lookupHash(phoneE164));
    }

    @Transactional
    public AppUser createUser(String phoneE164, Role role, String name) {
        AppUser u = new AppUser();
        u.setPhone(phoneE164);
        u.setPhoneHash(crypto.lookupHash(phoneE164));
        u.setRole(role);
        u.setName(name);
        u.setProfileCompleted(name != null && !name.isBlank());
        return userRepository.save(u);
    }

    @Transactional
    public AppUser completeProfile(UUID userId, String name, String email) {
        AppUser u = require(userId);
        u.setName(name);
        if (email != null && !email.isBlank()) {
            u.setEmail(email.trim());
            u.setEmailHash(crypto.lookupHash(email));
        }
        u.setProfileCompleted(true);
        return userRepository.save(u);
    }

    @Transactional
    public AppUser setPreferredTheme(UUID userId, String theme) {
        AppUser u = require(userId);
        u.setPreferredTheme(theme);
        return userRepository.save(u);
    }

    @Transactional(readOnly = true)
    public List<UserTenantMembership> memberships(UUID userId) {
        return membershipRepository.findByUserId(userId);
    }

    /** Active tenant = current_tenant_id if it still has an ACTIVE membership, else any ACTIVE one. */
    @Transactional(readOnly = true)
    public UUID resolveActiveTenant(AppUser user) {
        List<UserTenantMembership> active =
                membershipRepository.findByUserIdAndStatus(user.getId(), MembershipStatus.ACTIVE);
        if (active.isEmpty()) return null;
        if (user.getCurrentTenantId() != null) {
            boolean stillActive = active.stream().anyMatch(m -> m.getTenantId().equals(user.getCurrentTenantId()));
            if (stillActive) return user.getCurrentTenantId();
        }
        return active.get(0).getTenantId();
    }

    @Transactional
    public void setCurrentTenant(UUID userId, UUID tenantId) {
        AppUser u = require(userId);
        u.setCurrentTenantId(tenantId);
        userRepository.save(u);
    }
}
