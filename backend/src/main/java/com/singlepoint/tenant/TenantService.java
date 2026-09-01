package com.singlepoint.tenant;

import com.singlepoint.common.error.AppException;
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
}
