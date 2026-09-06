package com.singlepoint.auth;

import com.singlepoint.auth.domain.OtpChallenge;
import com.singlepoint.common.util.PhoneNumbers;
import com.singlepoint.crypto.CryptoService;
import com.singlepoint.provider.ServiceProviderRepository;
import com.singlepoint.provider.TenantServiceProviderRepository;
import com.singlepoint.provider.domain.ServiceProvider;
import com.singlepoint.provider.domain.TenantServiceProvider;
import com.singlepoint.security.JwtService;
import com.singlepoint.tenant.AdminTenantRepository;
import com.singlepoint.tenant.domain.AdminTenant;
import com.singlepoint.user.UserService;
import com.singlepoint.user.UserTenantMembershipRepository;
import com.singlepoint.user.domain.AppUser;
import com.singlepoint.user.domain.MembershipStatus;
import com.singlepoint.user.domain.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AuthService {

    public enum OnboardingState { NEEDS_PROFILE, NEEDS_COMMUNITY, PENDING_APPROVAL, READY }

    public record Session(String token, long expiresInSeconds, OnboardingState onboardingState, AppUser user, UUID activeTenantId) { }

    private final OtpService otpService;
    private final UserService userService;
    private final ServiceProviderRepository providerRepository;
    private final TenantServiceProviderRepository tenantProviderRepository;
    private final UserTenantMembershipRepository membershipRepository;
    private final AdminTenantRepository adminTenantRepository;
    private final JwtService jwtService;
    private final CryptoService crypto;
    private final com.singlepoint.notification.DeviceTokenRepository deviceTokens;

    public AuthService(OtpService otpService, UserService userService,
                       ServiceProviderRepository providerRepository,
                       TenantServiceProviderRepository tenantProviderRepository,
                       UserTenantMembershipRepository membershipRepository,
                       AdminTenantRepository adminTenantRepository,
                       JwtService jwtService, CryptoService crypto,
                       com.singlepoint.notification.DeviceTokenRepository deviceTokens) {
        this.deviceTokens = deviceTokens;
        this.otpService = otpService;
        this.userService = userService;
        this.providerRepository = providerRepository;
        this.tenantProviderRepository = tenantProviderRepository;
        this.membershipRepository = membershipRepository;
        this.adminTenantRepository = adminTenantRepository;
        this.jwtService = jwtService;
        this.crypto = crypto;
    }

    /** @return dev-mode OTP code (for the client to display) or null. */
    public String requestOtp(String rawPhone) {
        String phone = PhoneNumbers.normalize(rawPhone);
        return otpService.request(phone, OtpChallenge.Purpose.LOGIN);
    }

    @Transactional
    public Session verifyOtp(String rawPhone, String code, String deviceId) {
        String phone = PhoneNumbers.normalize(rawPhone);
        otpService.verify(phone, code, OtpChallenge.Purpose.LOGIN);
        AppUser user = userService.findByPhone(phone).orElseGet(() -> resolveNewUser(phone));
        return buildSession(user, deviceId);
    }

    @Transactional
    public void completeProfileAndRefresh(UUID userId, String name, String email) {
        userService.completeProfile(userId, name, email);
    }

    @Transactional
    public Session refreshSessionFor(UUID userId, String deviceId) {
        return buildSession(userService.require(userId), deviceId);
    }

    private Session buildSession(AppUser user, String deviceId) {
        UUID activeTenant = resolveActiveTenant(user);
        OnboardingState state = onboardingState(user, activeTenant);
        String token = jwtService.issue(user.getId(), user.getRole(), activeTenant, user.getName(), deviceId);
        recordDevice(user.getId(), deviceId);
        return new Session(token, jwtService.getTtlSeconds(), state, user, activeTenant);
    }

    /** MVP-13 (C3): one device_token row per (user, device) — a fresh OTP sign-in also un-revokes it. */
    private void recordDevice(UUID userId, String deviceId) {
        if (deviceId == null || deviceId.isBlank()) return;
        var d = deviceTokens.findByUserIdAndDeviceId(userId, deviceId)
                .orElseGet(com.singlepoint.notification.domain.DeviceToken::new);
        d.setUserId(userId);
        d.setDeviceId(deviceId);
        d.setLastSeenAt(java.time.Instant.now());
        d.setRevokedAt(null);
        if (d.getLabel() == null) {
            d.setLabel("Device " + deviceId.substring(0, Math.min(6, deviceId.length())));
        }
        deviceTokens.save(d);
    }

    /**
     * Active tenant differs by role: residents by membership, admins by {@code admin_tenant}
     * assignment, providers by enrolment. A Super Admin is normally cross-tenant (null), but
     * carries a tenant while "acting as admin" for an admin-less community.
     */
    public UUID resolveActiveTenant(AppUser user) {
        return switch (user.getRole()) {
            case SUPER_ADMIN -> user.getCurrentTenantId();
            case ADMIN -> {
                List<AdminTenant> links = adminTenantRepository
                        .findByAdminUserIdAndActiveTrueOrderByCreatedAtAsc(user.getId());
                if (links.isEmpty()) yield null;
                UUID current = user.getCurrentTenantId();
                boolean stillAssigned = current != null
                        && links.stream().anyMatch(l -> l.getTenantId().equals(current));
                if (stillAssigned) yield current;
                UUID first = links.get(0).getTenantId();
                userService.setCurrentTenant(user.getId(), first);
                user.setCurrentTenantId(first);
                yield first;
            }
            case RESIDENT -> userService.resolveActiveTenant(user);
            case PROVIDER -> {
                if (user.getCurrentTenantId() != null) yield user.getCurrentTenantId();
                UUID pid = providerRepository.findByUserId(user.getId())
                        .map(ServiceProvider::getId).orElse(null);
                UUID resolved = pid == null ? null :
                        tenantProviderRepository.findByServiceProviderIdAndActiveTrue(pid).stream()
                                .map(TenantServiceProvider::getTenantId).findFirst().orElse(null);
                if (resolved != null) {
                    userService.setCurrentTenant(user.getId(), resolved);
                    user.setCurrentTenantId(resolved);
                }
                yield resolved;
            }
        };
    }

    private AppUser resolveNewUser(String phone) {
        ServiceProvider provider = providerRepository.findByContactPhoneHash(crypto.lookupHash(phone)).orElse(null);
        if (provider != null) {
            AppUser u = userService.createUser(phone, Role.PROVIDER, provider.getName());
            provider.setUserId(u.getId());
            providerRepository.save(provider);
            return u;
        }
        return userService.createUser(phone, Role.RESIDENT, null);
    }

    private OnboardingState onboardingState(AppUser user, UUID activeTenant) {
        if (!user.isProfileCompleted()) return OnboardingState.NEEDS_PROFILE;
        // MVP-8: a profile-complete resident with no community is READY (a community-less
        // individual). They wait only while a join request is pending approval.
        if (user.getRole() == Role.RESIDENT && activeTenant == null
                && !membershipRepository.findByUserIdAndStatus(
                        user.getId(), MembershipStatus.PENDING_APPROVAL).isEmpty()) {
            return OnboardingState.PENDING_APPROVAL;
        }
        return OnboardingState.READY;
    }
}
