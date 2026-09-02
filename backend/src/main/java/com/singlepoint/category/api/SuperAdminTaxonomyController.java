package com.singlepoint.category.api;

import com.singlepoint.category.VendorCategoryKindRepository;
import com.singlepoint.category.VendorCategoryRepository;
import com.singlepoint.category.domain.VendorCategory;
import com.singlepoint.category.domain.VendorCategoryKind;
import com.singlepoint.common.error.AppException;
import com.singlepoint.common.error.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/superadmin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Super Admin — Taxonomy", description = "Manage vendor categories and verticals")
public class SuperAdminTaxonomyController {

    private final VendorCategoryRepository categories;
    private final VendorCategoryKindRepository kinds;

    public SuperAdminTaxonomyController(VendorCategoryRepository categories, VendorCategoryKindRepository kinds) {
        this.categories = categories;
        this.kinds = kinds;
    }

    public record KindView(UUID id, String code, String label, int sortOrder, boolean active) {
        static KindView of(VendorCategoryKind k) {
            return new KindView(k.getId(), k.getCode(), k.getLabel(), k.getSortOrder(), k.isActive());
        }
    }
    public record CategoryView(UUID id, String name, String kind, UUID parentCategoryId, int sortOrder, boolean active) {
        static CategoryView of(VendorCategory c) {
            return new CategoryView(c.getId(), c.getName(), c.getKind(), c.getParentCategoryId(),
                    c.getSortOrder(), c.isActive());
        }
    }
    public record KindRequest(@NotBlank String code, @NotBlank String label, Integer sortOrder) { }
    public record CategoryRequest(@NotBlank String name, @NotBlank String kind, String parentCategoryId, Integer sortOrder) { }
    public record RenameRequest(String name, String label, Integer sortOrder) { }

    // ---- kinds (verticals) ---------------------------------------------

    @GetMapping("/vendor-category-kinds")
    public ResponseEntity<List<KindView>> listKinds() {
        return ResponseEntity.ok(kinds.findAllByOrderBySortOrderAsc().stream().map(KindView::of).toList());
    }

    @PostMapping("/vendor-category-kinds")
    @Operation(summary = "Open a new vendor vertical")
    public ResponseEntity<KindView> createKind(@Valid @RequestBody KindRequest body) {
        String code = body.code().trim().toUpperCase().replaceAll("[^A-Z0-9_]", "_");
        if (kinds.existsByCode(code)) throw new AppException(ErrorCode.CONFLICT, "That vertical code already exists");
        VendorCategoryKind k = new VendorCategoryKind();
        k.setCode(code);
        k.setLabel(body.label().trim());
        if (body.sortOrder() != null) k.setSortOrder(body.sortOrder());
        return ResponseEntity.status(HttpStatus.CREATED).body(KindView.of(kinds.save(k)));
    }

    @PutMapping("/vendor-category-kinds/{id}")
    public ResponseEntity<KindView> renameKind(@PathVariable UUID id, @RequestBody RenameRequest body) {
        VendorCategoryKind k = kinds.findById(id).orElseThrow(() -> AppException.notFound("Vertical"));
        if (body.label() != null && !body.label().isBlank()) k.setLabel(body.label().trim());
        if (body.sortOrder() != null) k.setSortOrder(body.sortOrder());
        return ResponseEntity.ok(KindView.of(kinds.save(k)));
    }

    @PostMapping("/vendor-category-kinds/{id}/deactivate")
    public ResponseEntity<KindView> deactivateKind(@PathVariable UUID id) {
        VendorCategoryKind k = kinds.findById(id).orElseThrow(() -> AppException.notFound("Vertical"));
        k.setActive(false);
        return ResponseEntity.ok(KindView.of(kinds.save(k)));
    }

    // ---- categories -------------------------------------------------

    @GetMapping("/vendor-categories")
    @Operation(summary = "All vendor categories, including inactive")
    public ResponseEntity<List<CategoryView>> listCategories() {
        return ResponseEntity.ok(categories.findAllByOrderBySortOrderAsc().stream().map(CategoryView::of).toList());
    }

    @PostMapping("/vendor-categories")
    public ResponseEntity<CategoryView> createCategory(@Valid @RequestBody CategoryRequest body) {
        String kind = body.kind().trim().toUpperCase();
        kinds.findByCode(kind).filter(VendorCategoryKind::isActive)
                .orElseThrow(() -> new AppException(ErrorCode.VALIDATION_FAILED, "Unknown or inactive vertical: " + kind));
        VendorCategory c = new VendorCategory();
        c.setName(body.name().trim());
        c.setKind(kind);
        c.setParentCategoryId(body.parentCategoryId() != null ? UUID.fromString(body.parentCategoryId()) : null);
        if (body.sortOrder() != null) c.setSortOrder(body.sortOrder());
        return ResponseEntity.status(HttpStatus.CREATED).body(CategoryView.of(categories.save(c)));
    }

    @PutMapping("/vendor-categories/{id}")
    public ResponseEntity<CategoryView> updateCategory(@PathVariable UUID id, @RequestBody RenameRequest body) {
        VendorCategory c = categories.findById(id).orElseThrow(() -> AppException.notFound("Category"));
        if (body.name() != null && !body.name().isBlank()) c.setName(body.name().trim());
        if (body.sortOrder() != null) c.setSortOrder(body.sortOrder());
        return ResponseEntity.ok(CategoryView.of(categories.save(c)));
    }

    @PostMapping("/vendor-categories/{id}/deactivate")
    @Operation(summary = "Hide a category from new pickers; existing providers/offers keep it")
    public ResponseEntity<CategoryView> deactivateCategory(@PathVariable UUID id) {
        VendorCategory c = categories.findById(id).orElseThrow(() -> AppException.notFound("Category"));
        c.setActive(false);
        return ResponseEntity.ok(CategoryView.of(categories.save(c)));
    }

    @PostMapping("/vendor-categories/{id}/reactivate")
    public ResponseEntity<CategoryView> reactivateCategory(@PathVariable UUID id) {
        VendorCategory c = categories.findById(id).orElseThrow(() -> AppException.notFound("Category"));
        c.setActive(true);
        return ResponseEntity.ok(CategoryView.of(categories.save(c)));
    }
}
