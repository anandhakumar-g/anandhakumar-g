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
    private final CryptoService crypto;
    private final com.singlepoint.entitlement.EntitlementService entitlements;

    public ProviderService(ServiceProviderRepository providerRepository,
                           TenantServiceProviderRepository tenantProviderRepository,
                           VendorCategoryRepository vendorCategoryRepository,
                           AppUserRepository userRepository, CryptoService crypto,
                           com.singlepoint.provider.kyc.KycService kycService,
                           com.singlepoint.entitlement.EntitlementService entitlements) {
        this.providerRepository = providerRepository;
        this.tenantProviderRepository = tenantProviderRepository;
        this.vendorCategoryRepository = vendorCategoryRepository;
        this.userRepository = userRepository;
        this.crypto = crypto;
        this.kycService = kycService;
        this.entitlements = entitlements;
    }

    private final com.singlepoint.provider.kyc.KycService kycService;

    /** Admin adds a provider to their community's directory. Providers are never self-approved. */
    @Transactional
    public ServiceProvider createForTenant(UUID tenantId, String name, UUID vendorCategoryId, boolean company,
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
        provider = providerRepository.save(provider);

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

    /** Admin edits a provider enrolled in their community. Any null argument is left unchanged. */
    @Transactional
    public ServiceProvider updateForTenant(UUID tenantId, UUID providerId, String name, String contactPhone,
                                           String contactEmail, String serviceArea, UUID vendorCategoryId) {
        requireEnrolled(tenantId, providerId);
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

    private void requireEnrolled(UUID tenantId, UUID providerId) {
        if (!tenantProviderRepository.existsByTenantIdAndServiceProviderId(tenantId, providerId)) {
            throw AppException.notFound("Service provider");
        }
    }

    @Transactional(readOnly = true)
    public ServiceProvider require(UUID id) {
        return providerRepository.findById(id).orElseThrow(() -> AppException.notFound("Service provider"));
    }
}
