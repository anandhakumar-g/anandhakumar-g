package com.singlepoint.provider;

import com.singlepoint.category.VendorCategoryRepository;
import com.singlepoint.common.error.AppException;
import com.singlepoint.common.error.ErrorCode;
import com.singlepoint.common.util.PhoneNumbers;
import com.singlepoint.crypto.CryptoService;
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

    public ProviderService(ServiceProviderRepository providerRepository,
                           TenantServiceProviderRepository tenantProviderRepository,
                           VendorCategoryRepository vendorCategoryRepository,
                           AppUserRepository userRepository, CryptoService crypto,
                           com.singlepoint.provider.kyc.KycService kycService) {
        this.providerRepository = providerRepository;
        this.tenantProviderRepository = tenantProviderRepository;
        this.vendorCategoryRepository = vendorCategoryRepository;
        this.userRepository = userRepository;
        this.crypto = crypto;
        this.kycService = kycService;
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

    @Transactional(readOnly = true)
    public List<ServiceProvider> directoryForTenant(UUID tenantId) {
        List<UUID> ids = tenantProviderRepository.findByTenantIdAndActiveTrue(tenantId)
                .stream().map(TenantServiceProvider::getServiceProviderId).toList();
        return providerRepository.findAllById(ids);
    }

    @Transactional(readOnly = true)
    public ServiceProvider require(UUID id) {
        return providerRepository.findById(id).orElseThrow(() -> AppException.notFound("Service provider"));
    }
}
