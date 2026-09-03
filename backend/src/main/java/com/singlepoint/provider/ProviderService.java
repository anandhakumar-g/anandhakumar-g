package com.singlepoint.provider;

import com.singlepoint.category.VendorCategoryRepository;
import com.singlepoint.common.error.AppException;
import com.singlepoint.common.error.ErrorCode;
import com.singlepoint.common.util.PhoneNumbers;
import com.singlepoint.crypto.CryptoService;
import com.singlepoint.provider.domain.Availability;
import com.singlepoint.provider.domain.ServiceProvider;
import com.singlepoint.provider.domain.TenantServiceProvider;
import com.singlepoint.provider.domain.VerificationStatus;
import com.singlepoint.user.AppUserRepository;
import com.singlepoint.user.domain.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ProviderService {

    private final ServiceProviderRepository providerRepository;
    private final TenantServiceProviderRepository tenantProviderRepository;
    private final VendorCategoryRepository vendorCategoryRepository;
    private final AppUserRepository userRepository;
    private final com.singlepoint.tenant.TenantRepository tenantRepository;
    private final CryptoService crypto;
    private final com.singlepoint.entitlement.EntitlementService entitlements;

    public ProviderService(ServiceProviderRepository providerRepository,
                           TenantServiceProviderRepository tenantProviderRepository,
                           VendorCategoryRepository vendorCategoryRepository,
                           AppUserRepository userRepository,
                           com.singlepoint.tenant.TenantRepository tenantRepository, CryptoService crypto,
                           com.singlepoint.provider.kyc.KycService kycService,
                           com.singlepoint.entitlement.EntitlementService entitlements) {
        this.providerRepository = providerRepository;
        this.tenantProviderRepository = tenantProviderRepository;
        this.vendorCategoryRepository = vendorCategoryRepository;
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.crypto = crypto;
        this.kycService = kycService;
        this.entitlements = entitlements;
    }

    private final com.singlepoint.provider.kyc.KycService kycService;

    /** Upsert the global service_provider row, deduped by phone hash. No enrolment. */
    private ServiceProvider upsertGlobal(String name, UUID vendorCategoryId, boolean company,
                                         String contactPhone, String contactEmail, String serviceArea) {
        vendorCategoryRepository.findById(vendorCategoryId)
                .orElseThrow(() -> AppException.notFound("Vendor category"));
        String phone = PhoneNumbers.normalize(contactPhone);
        String phoneHash = crypto.lookupHash(phone);
        ServiceProvider provider = providerRepository.findByContactPhoneHash(phoneHash).orElseGet(ServiceProvider::new);
        provider.setName(name);
        provider.setVendorCategoryId(vendorCategoryId);
        provider.setCompany(company);
        provider.setContactPhone(phone);
        provider.setContactPhoneHash(phoneHash);
        provider.setContactEmail(contactEmail);
        provider.setServiceArea(serviceArea);
        if (provider.getVerificationStatus() == null) {
            provider.setVerificationStatus(VerificationStatus.PENDING_VERIFICATION);
        }
        return providerRepository.save(provider);
    }

    /** MVP-7: the Super Admin creates a global provider. Verification + KYC review follow. */
    @Transactional
    public ServiceProvider createGlobal(String name, UUID vendorCategoryId, boolean company,
                                        String contactPhone, String contactEmail, String serviceArea) {
        return upsertGlobal(name, vendorCategoryId, company, contactPhone, contactEmail, serviceArea);
    }

    /** Seed/bootstrap path only: create the global row and enrol it in one community. */
    @Transactional
    public ServiceProvider createForTenant(UUID tenantId, String name, UUID vendorCategoryId, boolean company,
                                           String contactPhone, String contactEmail, String serviceArea) {
        ServiceProvider provider = upsertGlobal(name, vendorCategoryId, company, contactPhone, contactEmail, serviceArea);
        if (!tenantProviderRepository.existsByTenantIdAndServiceProviderId(tenantId, provider.getId())) {
            TenantServiceProvider tsp = new TenantServiceProvider();
            tsp.setTenantId(tenantId);
            tsp.setServiceProviderId(provider.getId());
            tsp.setServiceArea(serviceArea);
            tenantProviderRepository.save(tsp);
        }
        return provider;
    }

    /**
     * MVP-7: a community admin enrols an already-verified provider from the global directory.
     * Requires the community's {@code provider_onboarding_allowed} flag and a VERIFIED provider.
     */
    @Transactional
    public ServiceProvider enrol(UUID tenantId, UUID providerId) {
        boolean allowed = tenantRepository.findById(tenantId)
                .map(com.singlepoint.tenant.domain.Tenant::isProviderOnboardingAllowed).orElse(false);
        if (!allowed) {
            throw new AppException(ErrorCode.FORBIDDEN,
                    "This community is not permitted to enrol providers");
        }
        ServiceProvider p = require(providerId);
        if (p.getVerificationStatus() != VerificationStatus.VERIFIED) {
            throw new AppException(ErrorCode.PROVIDER_NOT_ASSIGNABLE,
                    "Only a verified provider can be enrolled");
        }
        TenantServiceProvider tsp = tenantProviderRepository
                .findByTenantIdAndServiceProviderId(tenantId, providerId).orElse(null);
        if (tsp == null) {
            tsp = new TenantServiceProvider();
            tsp.setTenantId(tenantId);
            tsp.setServiceProviderId(providerId);
            tsp.setServiceArea(p.getServiceArea());
        }
        tsp.setActive(true);
        tenantProviderRepository.save(tsp);
        return p;
    }

    /** MVP-7: the global verified directory an admin picks from. Optional vendor-category filter. */
    @Transactional(readOnly = true)
    public List<ServiceProvider> verifiedGlobal(UUID vendorCategoryId) {
        return providerRepository.findAll().stream()
                .filter(p -> p.getVerificationStatus() == VerificationStatus.VERIFIED)
                .filter(p -> vendorCategoryId == null || vendorCategoryId.equals(p.getVendorCategoryId()))
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .toList();
    }

    /** Every global provider (Super Admin console). */
    @Transactional(readOnly = true)
    public List<ServiceProvider> allGlobal() {
        return providerRepository.findAll().stream()
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .toList();
    }

    /**
     * Verification transition. Moving a provider to VERIFIED now requires that the KYC document
     * gate is satisfied (accepted GOV_ID + ADDRESS_PROOF, plus COMPANY_REG for companies).
     */
    @Transactional
    public ServiceProvider setVerification(UUID providerId, VerificationStatus status, UUID adminUserId) {
        ServiceProvider p = providerRepository.findById(providerId)
                .orElseThrow(() -> AppException.notFound("Service provider"));
        if (status == VerificationStatus.VERIFIED) {
            kycService.assertVerifiable(p);
        }
        p.setVerificationStatus(status);
        if (status == VerificationStatus.VERIFIED) {
            p.setVerifiedByUserId(adminUserId);
            p.setVerifiedAt(Instant.now());
        }
        return providerRepository.save(p);
    }

    /** Featured providers first, then by name. Lapsed / unlisted providers are excluded. */
    @Transactional(readOnly = true)
    public List<ServiceProvider> directoryForTenant(UUID tenantId) {
        return directoryForTenant(tenantId, null);
    }

    /**
     * Featured providers first; then either by rating (desc, unrated last) when
     * {@code sort} is {@code "rating"}, or by name. Lapsed / unlisted providers are excluded.
     */
    @Transactional(readOnly = true)
    public List<ServiceProvider> directoryForTenant(UUID tenantId, String sort) {
        List<UUID> ids = tenantProviderRepository.findByTenantIdAndActiveTrue(tenantId)
                .stream().map(TenantServiceProvider::getServiceProviderId).toList();
        var pSubject = com.singlepoint.billing.domain.SubjectType.PROVIDER;
        boolean byRating = "rating".equalsIgnoreCase(sort);
        return providerRepository.findAllById(ids).stream()
                .filter(p -> entitlements.isEntitled(pSubject, p.getId(), "DIRECTORY_LISTING")
                        && !entitlements.isLapsed(pSubject, p.getId()))
                .sorted((a, b) -> {
                    int t = Boolean.compare(b.getTier() == com.singlepoint.provider.domain.ProviderTier.FEATURED,
                            a.getTier() == com.singlepoint.provider.domain.ProviderTier.FEATURED);
                    if (t != 0) return t;
                    if (byRating) {
                        double ra = a.getRatingAvg() == null ? -1 : a.getRatingAvg().doubleValue();
                        double rb = b.getRatingAvg() == null ? -1 : b.getRatingAvg().doubleValue();
                        int r = Double.compare(rb, ra);
                        if (r != 0) return r;
                    }
                    return a.getName().compareToIgnoreCase(b.getName());
                })
                .toList();
    }

    /** Every provider enrolled in this community, active or not — for the admin management list. */
    @Transactional(readOnly = true)
    public List<ServiceProvider> allForTenant(UUID tenantId) {
        List<UUID> ids = tenantProviderRepository.findByTenantId(tenantId)
                .stream().map(TenantServiceProvider::getServiceProviderId).toList();
        return providerRepository.findAllById(ids).stream()
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean isEnrolledAndActive(UUID tenantId, UUID providerId) {
        return tenantProviderRepository.findByTenantIdAndServiceProviderId(tenantId, providerId)
                .map(TenantServiceProvider::isActive).orElse(false);
    }

    @Transactional
    public ServiceProvider setTier(UUID providerId, com.singlepoint.provider.domain.ProviderTier tier) {
        ServiceProvider p = require(providerId);
        p.setTier(tier);
        return providerRepository.save(p);
    }

    /** MVP-7: the Super Admin edits a global provider. Any null argument is left unchanged. */
    @Transactional
    public ServiceProvider updateGlobal(UUID providerId, String name, String contactPhone,
                                        String contactEmail, String serviceArea, UUID vendorCategoryId) {
        ServiceProvider p = require(providerId);
        if (name != null && !name.isBlank()) p.setName(name.trim());
        if (contactEmail != null) p.setContactEmail(contactEmail.isBlank() ? null : contactEmail.trim());
        if (serviceArea != null) p.setServiceArea(serviceArea.isBlank() ? null : serviceArea.trim());
        if (vendorCategoryId != null) {
            vendorCategoryRepository.findById(vendorCategoryId)
                    .orElseThrow(() -> AppException.notFound("Vendor category"));
            p.setVendorCategoryId(vendorCategoryId);
        }
        if (contactPhone != null && !contactPhone.isBlank()) {
            String phone = PhoneNumbers.normalize(contactPhone);
            String hash = crypto.lookupHash(phone);
            if (!hash.equals(p.getContactPhoneHash())
                    && providerRepository.findByContactPhoneHash(hash).isPresent()) {
                throw new AppException(ErrorCode.CONFLICT, "Another provider already uses that phone number");
            }
            p.setContactPhone(phone);
            p.setContactPhoneHash(hash);
        }
        return providerRepository.save(p);
    }

    /** Add / remove a provider from a single community's directory (the enrolment row, not the global flag). */
    @Transactional
    public ServiceProvider setEnrolmentActive(UUID tenantId, UUID providerId, boolean active) {
        TenantServiceProvider tsp = tenantProviderRepository.findByTenantIdAndServiceProviderId(tenantId, providerId)
                .orElseThrow(() -> AppException.notFound("Service provider"));
        tsp.setActive(active);
        tenantProviderRepository.save(tsp);
        return require(providerId);
    }

    /** Provider self-service: contact email, service area and availability only (name/phone are admin-only). */
    @Transactional
    public ServiceProvider updateOwnProfile(UUID userId, String contactEmail, String serviceArea,
                                            String availability, String availabilityNote) {
        ServiceProvider p = providerRepository.findByUserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.FORBIDDEN, "No provider profile for this account"));
        if (contactEmail != null) p.setContactEmail(contactEmail.isBlank() ? null : contactEmail.trim());
        if (serviceArea != null) p.setServiceArea(serviceArea.isBlank() ? null : serviceArea.trim());
        if (availability != null) {
            try {
                p.setAvailability(Availability.valueOf(availability.trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new AppException(ErrorCode.VALIDATION_FAILED, "availability must be AVAILABLE, BUSY or AWAY");
            }
        }
        if (availabilityNote != null) p.setAvailabilityNote(availabilityNote.isBlank() ? null : availabilityNote.trim());
        return providerRepository.save(p);
    }

    @Transactional(readOnly = true)
    public ServiceProvider requireOwn(UUID userId) {
        return providerRepository.findByUserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.FORBIDDEN, "No provider profile for this account"));
    }

    @Transactional(readOnly = true)
    public ServiceProvider require(UUID id) {
        return providerRepository.findById(id).orElseThrow(() -> AppException.notFound("Service provider"));
    }
}
