package com.singlepoint.category;

import com.singlepoint.category.domain.VendorCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VendorCategoryRepository extends JpaRepository<VendorCategory, UUID> {

    List<VendorCategory> findByActiveTrueOrderBySortOrderAsc();

    List<VendorCategory> findAllByOrderBySortOrderAsc();

    boolean existsByKind(String kind);
}
