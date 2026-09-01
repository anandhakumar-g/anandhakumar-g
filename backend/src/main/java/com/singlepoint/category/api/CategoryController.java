package com.singlepoint.category.api;

import com.singlepoint.category.CategoryRepository;
import com.singlepoint.category.VendorCategoryRepository;
import com.singlepoint.category.domain.Category;
import com.singlepoint.category.domain.VendorCategory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Categories", description = "Ticket + vendor category lists")
public class CategoryController {

    private final CategoryRepository categoryRepository;
    private final VendorCategoryRepository vendorCategoryRepository;

    public CategoryController(CategoryRepository categoryRepository, VendorCategoryRepository vendorCategoryRepository) {
        this.categoryRepository = categoryRepository;
        this.vendorCategoryRepository = vendorCategoryRepository;
    }

    public record CategoryView(UUID id, String name, String requestType, Integer slaHours, int sortOrder) { }
    public record VendorCategoryView(UUID id, String name, String kind, int sortOrder) { }

    @GetMapping("/categories")
    @Operation(summary = "Ticket categories for the raise-ticket picker")
    public ResponseEntity<List<CategoryView>> categories() {
        List<CategoryView> out = categoryRepository.findByTenantIdIsNullAndActiveTrueOrderBySortOrderAsc().stream()
                .map(c -> new CategoryView(c.getId(), c.getName(), c.getRequestType().name(),
                        c.getSlaHours(), c.getSortOrder()))
                .toList();
        return ResponseEntity.ok(out);
    }

    @GetMapping("/vendor-categories")
    @Operation(summary = "Vendor categories (used when adding a provider)")
    public ResponseEntity<List<VendorCategoryView>> vendorCategories() {
        List<VendorCategoryView> out = vendorCategoryRepository.findByActiveTrueOrderBySortOrderAsc().stream()
                .map(v -> new VendorCategoryView(v.getId(), v.getName(), v.getKind().name(), v.getSortOrder()))
                .toList();
        return ResponseEntity.ok(out);
    }
}
