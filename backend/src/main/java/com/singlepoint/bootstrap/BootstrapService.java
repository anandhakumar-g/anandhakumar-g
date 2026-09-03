package com.singlepoint.bootstrap;

import com.singlepoint.crypto.CryptoService;
import com.singlepoint.flat.InviteCodeService;
import com.singlepoint.flat.domain.Flat;
import com.singlepoint.flat.FlatRepository;
import com.singlepoint.flat.domain.InviteCode;
import com.singlepoint.location.LocationRepository;
import com.singlepoint.location.domain.Location;
import com.singlepoint.provider.ProviderService;
import com.singlepoint.provider.ServiceProviderRepository;
import com.singlepoint.provider.domain.ServiceProvider;
import com.singlepoint.provider.domain.VerificationStatus;
import com.singlepoint.provider.kyc.ProviderKycDocument;
import com.singlepoint.provider.kyc.ProviderKycDocumentRepository;
import com.singlepoint.security.TenantScopedExecutor;
import com.singlepoint.tenant.TenantService;
import com.singlepoint.tenant.domain.Tenant;
import com.singlepoint.user.AppUserRepository;
import com.singlepoint.user.domain.AppUser;
import com.singlepoint.user.domain.MembershipRelation;
import com.singlepoint.user.domain.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/** Local-only demo data, created through the real services so PII encryption/hashing is consistent. */
@Component
@Profile("local")
@ConditionalOnProperty(name = "sp.bootstrap.dev-seed", havingValue = "true")
public class BootstrapService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapService.class);

    private final AppUserRepository userRepository;
    private final TenantService tenantService;
    private final FlatRepository flatRepository;
    private final LocationRepository locationRepository;
    private final InviteCodeService inviteCodeService;
    private final ProviderService providerService;
    private final ServiceProviderRepository providerRepository;
    private final ProviderKycDocumentRepository kycRepository;
    private final CryptoService crypto;
    private final TenantScopedExecutor tenantScoped;

    public BootstrapService(AppUserRepository userRepository, TenantService tenantService,
                            FlatRepository flatRepository, LocationRepository locationRepository,
                            InviteCodeService inviteCodeService,
                            ProviderService providerService, ServiceProviderRepository providerRepository,
                            ProviderKycDocumentRepository kycRepository,
                            CryptoService crypto, TenantScopedExecutor tenantScoped) {
        this.userRepository = userRepository;
        this.tenantService = tenantService;
        this.flatRepository = flatRepository;
        this.locationRepository = locationRepository;
        this.inviteCodeService = inviteCodeService;
        this.providerService = providerService;
        this.providerRepository = providerRepository;
        this.kycRepository = kycRepository;
        this.crypto = crypto;
        this.tenantScoped = tenantScoped;
    }

    private void seedAcceptedKyc(UUID providerId, UUID reviewerId) {
        for (ProviderKycDocument.DocType type : new ProviderKycDocument.DocType[]{
                ProviderKycDocument.DocType.GOV_ID,
                ProviderKycDocument.DocType.ADDRESS_PROOF,
                ProviderKycDocument.DocType.COMPANY_REG}) {
            ProviderKycDocument d = new ProviderKycDocument();
            d.setServiceProviderId(providerId);
            d.setDocType(type);
            d.setStorageKey("seed/kyc/" + providerId + "/" + type.name().toLowerCase() + ".txt");
            d.setContentType("text/plain");
            d.setSizeBytes(0);
            d.setOriginalFilename(type.name().toLowerCase() + ".txt");
            d.setStatus(ProviderKycDocument.Status.ACCEPTED);
            d.setReviewedByUserId(reviewerId);
            d.setReviewedAt(java.time.Instant.now());
            kycRepository.save(d);
        }
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByRole(Role.SUPER_ADMIN)) {
            log.info("Dev seed skipped — data already present");
            return;
        }
        log.info("Seeding local demo data...");

        AppUser superAdmin = createUser(Role.SUPER_ADMIN, "Platform Owner", "+919000000000", null);

        Tenant green = tenantService.create("Green Meadows", "Bengaluru", "Whitefield",
                "12 Whitefield Main Rd", "560066", null, "light", "#2E7D32", 72);
        Tenant lake = tenantService.create("Lakeview Residency", "Bengaluru", "Hebbal",
                "5 Outer Ring Rd", "560024", null, "dark", "#1565C0", 48);
        // let the Green Meadows admin enrol verified providers in dev
        tenantService.update(green.getId(), null, null, null, null, null, null, null, null,
                null, null, null, null, true);

        AppUser greenAdmin = createUser(Role.ADMIN, "Green Meadows Admin", "+919000000101", green.getId());
        AppUser lakeAdmin = createUser(Role.ADMIN, "Lakeview Admin", "+919000000201", lake.getId());

        // Provider directory entry (global) + tenant enrolment (RLS) + accepted KYC + verify.
        AppUser providerUser = createUser(Role.PROVIDER, "Sparky Electricals", "+919000000301", null);
        UUID sparkyId = tenantScoped.inTenant(green.getId(), () -> {
            ServiceProvider sp = providerService.createForTenant(green.getId(), "Sparky Electricals",
                    UUID.fromString("22222222-0000-0000-0000-000000000001"), true,
                    "+919000000301", "ops@sparky.example", "Whitefield");
            seedAcceptedKyc(sp.getId(), superAdmin.getId());
            providerService.setVerification(sp.getId(), VerificationStatus.VERIFIED, superAdmin.getId());
            ServiceProvider linked = providerRepository.findById(sp.getId()).orElseThrow();
            linked.setUserId(providerUser.getId());
            providerRepository.save(linked);
            return sp.getId();
        });

        // Flats + invite codes for Green Meadows.
        String inviteA = seedFlatsAndInvite(green.getId(), greenAdmin.getId());
        String inviteL = seedFlatsAndInvite(lake.getId(), lakeAdmin.getId());

        log.info("""

                ================ SINGLE POINT — LOCAL DEMO DATA ================
                 Super Admin ....... +919000000000
                 Green Meadows ..... tenant {} | admin +919000000101 | invite code {}
                 Lakeview Residency  tenant {} | admin +919000000201 | invite code {}
                 Verified provider . Sparky Electricals  +919000000301  (Green Meadows)
                 OTP codes for any number are printed to this log (dev mode).
                ===============================================================
                """, green.getId(), inviteA, lake.getId(), inviteL);
    }

    private AppUser createUser(Role role, String name, String phone, UUID tenantId) {
        AppUser u = new AppUser();
        u.setRole(role);
        u.setName(name);
        u.setPhone(phone);
        u.setPhoneHash(crypto.lookupHash(phone));
        u.setCurrentTenantId(tenantId);
        u.setProfileCompleted(true);
        return userRepository.save(u);
    }

    private String seedFlatsAndInvite(UUID tenantId, UUID adminId) {
        return tenantScoped.inTenant(tenantId, () -> {
            Location loc = new Location();
            loc.setTenantId(tenantId);
            loc.setLabel("Main");
            loc.setGeoLat(new BigDecimal("12.9698"));
            loc.setGeoLng(new BigDecimal("77.7499"));
            loc = locationRepository.save(loc);

            String[] blocks = {"A", "B"};
            Flat firstFlat = null;
            for (String block : blocks) {
                for (int n = 101; n <= 104; n++) {
                    Flat f = new Flat();
                    f.setTenantId(tenantId);
                    f.setLocationId(loc.getId());
                    f.setBlock(block);
                    f.setFlatNumber(String.valueOf(n));
                    f.setAddressText(block + "-" + n);
                    f.setGeoLat(new BigDecimal("12.9698"));
                    f.setGeoLng(new BigDecimal("77.7499"));
                    f = flatRepository.save(f);
                    if (firstFlat == null) firstFlat = f;
                }
            }
            InviteCode code = inviteCodeService.create(tenantId, adminId,
                    firstFlat != null ? firstFlat.getId() : null, MembershipRelation.OCCUPANT, 365, 50);
            return code.getCode();
        });
    }
}
