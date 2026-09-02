package com.singlepoint.category.api;

import com.singlepoint.category.CategoryRepository;
import com.singlepoint.category.VendorCategoryKindRepository;
import com.singlepoint.category.VendorCategoryRepository;
import com.singlepoint.category.domain.VendorCategoryKind;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Categories", description = "Ticket + vendor category lists")
public class CategoryController {

    private final CategoryRepository categoryRepository;
    private final VendorCategoryRepository vendorCategoryRepository;
    private final VendorCategoryKindRepository vendorCategoryKindRepository;

    public CategoryController(CategoryRepository categoryRepository,
                             VendorCategoryRepository vendorCategoryRepository,
                             VendorCategoryKindRepository vendorCategoryKindRepository) {
        this.categoryRepository = categoryRepository;
        this.vendorCategoryRepository = vendorCategoryRepository;
        this.vendorCategoryKindRepository = vendorCategoryKindRepository;
    }

    public record CategoryView(UUID id, String name, String requestType, Integer slaHours, int sortOrder) { }
    public record VendorCategoryView(UUID id, String name, String kind, String kindLabel,
                                     UUID parentCategoryId, int sortOrder) { }

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
    @Operation(summary = "Active vendor categories (add-provider picker + offer authoring); group by kind")
    public ResponseEntity<List<VendorCategoryView>> vendorCategories() {
        Map<String, String> labels = vendorCategoryKindRepository.findAll().stream()
                .collect(Collectors.toMap(VendorCategoryKind::getCode, VendorCategoryKind::getLabel, (a, b) -> a));
        List<VendorCategoryView> out = vendorCategoryRepository.findByActiveTrueOrderBySortOrderAsc().stream()
                .map(v -> new VendorCategoryView(v.getId(), v.getName(), v.getKind(),
                        labels.getOrDefault(v.getKind(), humanise(v.getKind())),
                        v.getParentCategoryId(), v.getSortOrder()))
                .toList();
        return ResponseEntity.ok(out);
    }

    private static String humanise(String code) {
        String s = code.toLowerCase().replace('_', ' ');
        return s.isEmpty() ? "Other" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
