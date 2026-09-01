package com.singlepoint.auth;

import com.singlepoint.auth.domain.OtpChallenge;
import com.singlepoint.common.util.PhoneNumbers;
import com.singlepoint.crypto.CryptoService;
import com.singlepoint.provider.ServiceProviderRepository;
import com.singlepoint.provider.TenantServiceProviderRepository;
import com.singlepoint.provider.domain.ServiceProvider;
import com.singlepoint.provider.domain.TenantServiceProvider;
import com.singlepoint.security.JwtService;
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
    private final JwtService jwtService;
    private final CryptoService crypto;

    public AuthService(OtpService otpService, UserService userService,
                       ServiceProviderRepository providerRepository,
                       TenantServiceProviderRepository tenantProviderRepository,
                       UserTenantMembershipRepository membershipRepository,
                       JwtService jwtService, CryptoService crypto) {
        this.otpService = otpService;
        this.userService = userService;
        this.providerRepository = providerRepository;
        this.tenantProviderRepository = tenantProviderRepository;
        this.membershipRepository = membershipRepository;
        this.jwtService = jwtService;
        this.crypto = crypto;
    }

    /** @return dev-mode OTP code (for the client to display) or null. */
    public String requestOtp(String rawPhone) {
        String phone = PhoneNumbers.normalize(rawPhone);
        return otpService.request(phone, OtpChallenge.Purpose.LOGIN);
    }

    @Transactional
    public Session verifyOtp(String rawPhone, String code) {
        String phone = PhoneNumbers.normalize(rawPhone);
        otpService.verify(phone, code, OtpChallenge.Purpose.LOGIN);
        AppUser user = userService.findByPhone(phone).orElseGet(() -> resolveNewUser(phone));
        return buildSession(user);
    }

    @Transactional
    public void completeProfileAndRefresh(UUID userId, String name, String email) {
        userService.completeProfile(userId, name, email);
    }

    @Transactional
    public Session refreshSessionFor(UUID userId) {
        return buildSession(userService.require(userId));
    }

    private Session buildSession(AppUser user) {
        UUID activeTenant = resolveActiveTenant(user);
        OnboardingState state = onboardingState(user, activeTenant);
        String token = jwtService.issue(user.getId(), user.getRole(), activeTenant, user.getName());
        return new Session(token, jwtService.getTtlSeconds(), state, user, activeTenant);
    }

    /** Active tenant differs by role: residents by membership, admins/providers by assignment. */
    private UUID resolveActiveTenant(AppUser user) {
        return switch (user.getRole()) {
            case SUPER_ADMIN -> null;
            case ADMIN -> user.getCurrentTenantId();
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
        if (user.getRole() == Role.RESIDENT && activeTenant == null) {
            boolean pending = !membershipRepository
                    .findByUserIdAndStatus(user.getId(), MembershipStatus.PENDING_APPROVAL).isEmpty();
            return pending ? OnboardingState.PENDING_APPROVAL : OnboardingState.NEEDS_COMMUNITY;
        }
        return OnboardingState.READY;
    }
}
