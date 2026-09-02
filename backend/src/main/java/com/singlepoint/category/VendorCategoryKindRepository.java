package com.singlepoint.category;

import com.singlepoint.category.domain.VendorCategoryKind;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VendorCategoryKindRepository extends JpaRepository<VendorCategoryKind, UUID> {

    List<VendorCategoryKind> findByActiveTrueOrderBySortOrderAsc();

    List<VendorCategoryKind> findAllByOrderBySortOrderAsc();

    Optional<VendorCategoryKind> findByCode(String code);

    boolean existsByCode(String code);
}
