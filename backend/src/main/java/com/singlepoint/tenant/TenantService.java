package com.singlepoint.tenant;

import com.singlepoint.common.error.AppException;
import com.singlepoint.common.error.ErrorCode;
import com.singlepoint.tenant.domain.CategoryAdmin;
import com.singlepoint.tenant.domain.Tenant;
import com.singlepoint.tenant.domain.TenantStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class TenantService {

    private final TenantRepository repository;

    public TenantService(TenantRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<Tenant> search(String query, Pageable pageable) {
        String q = (query == null || query.isBlank()) ? null : query.trim();
        return repository.search(q, TenantStatus.ACTIVE, pageable);
    }

    @Transactional(readOnly = true)
    public Tenant require(UUID id) {
        return repository.findById(id).orElseThrow(() -> AppException.notFound("Community"));
    }

    @Transactional
    public Tenant create(String name, String city, String locality, String address, String pincode,
                         String logoUrl, String defaultTheme, String brandPrimaryColor, Integer reopenWindowHours) {
        Tenant t = new Tenant();
        t.setName(name);
        t.setCity(city);
        t.setLocality(locality);
        t.setAddress(address);
        t.setPincode(pincode);
        t.setLogoUrl(logoUrl);
        t.setDefaultTheme(defaultTheme);
        t.setBrandPrimaryColor(brandPrimaryColor);
        if (reopenWindowHours != null && reopenWindowHours > 0) t.setReopenWindowHours(reopenWindowHours);
        return repository.save(t);
    }

    /** MVP-11 (A): a self-onboarded community, held in PENDING_REVIEW until a Super Admin approves it. */
    @Transactional
    public Tenant createPending(String name, String city, String locality, String address, String pincode,
                                UUID requestedByUserId) {
        Tenant t = new Tenant();
        t.setName(name);
        t.setCity(city);
        t.setLocality(locality);
        t.setAddress(address);
        t.setPincode(pincode);
        t.setStatus(TenantStatus.PENDING_REVIEW);
        t.setRequestedByUserId(requestedByUserId);
        return repository.save(t);
    }

    @Transactional
    public Tenant setStatus(UUID id, TenantStatus status) {
        Tenant t = require(id);
        t.setStatus(status);
        return repository.save(t);
    }

    /**
     * Sparse update. Any null argument is left untouched. {@code categoryAdmin} is Super-Admin
     * territory — pass null from the community-admin path.
     */
    @Transactional
    public Tenant update(UUID id, String name, String city, String locality, String address, String pincode,
                         String logoUrl, String defaultTheme, String brandPrimaryColor,
                         Integer reopenWindowHours, Boolean requireAllocationApproval, String categoryAdmin,
                         Boolean directServiceEnabled, Boolean providerOnboardingAllowed, String status) {
        Tenant t = require(id);
        if (name != null) t.setName(name);
        if (city != null) t.setCity(city);
        if (locality != null) t.setLocality(locality);
        if (address != null) t.setAddress(address);
        if (pincode != null) t.setPincode(pincode);
        if (logoUrl != null) t.setLogoUrl(logoUrl);
        if (defaultTheme != null) t.setDefaultTheme(defaultTheme);
        if (brandPrimaryColor != null) t.setBrandPrimaryColor(brandPrimaryColor);
        if (reopenWindowHours != null && reopenWindowHours > 0) t.setReopenWindowHours(reopenWindowHours);
        if (requireAllocationApproval != null) t.setRequireAllocationApproval(requireAllocationApproval);
        if (categoryAdmin != null) t.setCategoryAdmin(parseCategoryAdmin(categoryAdmin));
        if (directServiceEnabled != null) t.setDirectServiceEnabled(directServiceEnabled);
        if (providerOnboardingAllowed != null) t.setProviderOnboardingAllowed(providerOnboardingAllowed);
        if (status != null) t.setStatus(parseStatus(status));
        return repository.save(t);
    }

    private static CategoryAdmin parseCategoryAdmin(String raw) {
        try {
            return CategoryAdmin.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "categoryAdmin must be SUPER_ADMIN or COMMUNITY");
        }
    }

    /**
     * Console lifecycle transitions only — ACTIVE / SUSPENDED / ARCHIVED. {@code PENDING_REVIEW}
     * is reachable solely through the self-onboarding approve/reject flow, never a direct update.
     */
    private static TenantStatus parseStatus(String raw) {
        TenantStatus s;
        try {
            s = TenantStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "status must be ACTIVE, SUSPENDED or ARCHIVED");
        }
        if (s == TenantStatus.PENDING_REVIEW) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "status PENDING_REVIEW is set only by the onboarding flow");
        }
        return s;
    }
}
