package com.meshsuite.product.controller;

import com.meshsuite.auth.service.AuthContextService;
import com.meshsuite.product.domain.enums.ProductStatus;
import com.meshsuite.product.domain.enums.ProductType;
import com.meshsuite.product.dto.*;
import com.meshsuite.product.service.KitProductService;
import com.meshsuite.product.service.ProductListingService;
import com.meshsuite.product.service.ProductTypeStrategyResolver;
import com.meshsuite.product.service.SimpleProductService;
import com.meshsuite.product.service.VariationProductService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final SimpleProductService productService;
    private final KitProductService kitProductService;
    private final VariationProductService variationProductService;
    private final ProductTypeStrategyResolver productTypeStrategyResolver;
    private final ProductListingService productListingService;

    public ProductController(SimpleProductService productService, KitProductService kitProductService,
                              VariationProductService variationProductService,
                              ProductTypeStrategyResolver productTypeStrategyResolver,
                              ProductListingService productListingService) {
        this.productService = productService;
        this.kitProductService = kitProductService;
        this.variationProductService = variationProductService;
        this.productTypeStrategyResolver = productTypeStrategyResolver;
        this.productListingService = productListingService;
    }

    @GetMapping
    public Page<ProductListItemResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ProductStatus status,
            @PageableDefault(size = 10, sort = "name") Pageable pageable) {
        return productService.list(search, status, pageable);
    }

    // Unified cross-type listing for the Produtos screen (Simples + Kit + Variação parent rows
    // with a type discriminator, and nested children for Variação) -- deliberately separate from
    // the type-scoped GET above, which other consumers (e.g. the Tabela de Preço item picker)
    // depend on returning only type=PRODUCT.
    @GetMapping("/all")
    public Page<ProductAllListItemResponse> listAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) ProductType type,
            @PageableDefault(size = 10, sort = "name") Pageable pageable) {
        return productListingService.listAll(search, status, type, pageable);
    }

    @GetMapping("/all/resumo")
    public ProductSummaryResponse listAllSummary() {
        return productListingService.summary();
    }

    @GetMapping("/resumo")
    public ProductSummaryResponse summary() {
        return productService.summary();
    }

    @GetMapping("/{id}")
    public ProductResponse findById(@PathVariable UUID id) {
        return productService.findById(id);
    }

    @PostMapping
    public ResponseEntity<ProductResponse> create(@AuthenticationPrincipal AuthContextService.Context principal,
                                                   @Valid @RequestBody ProductRequest request) {
        ProductResponse response = productService.create(principal.tenantId(), request);
        return ResponseEntity.status(201).body(response);
    }

    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable UUID id, @Valid @RequestBody ProductRequest request) {
        return productService.update(id, request);
    }

    // Shared across every ProductType (see ProductTypeStrategyResolver) -- dispatches
    // to whichever concrete strategy actually owns this id, so these two endpoints
    // work for Simples/Kit/Variação alike. A single response shape can't sensibly
    // cover all three, so this returns 204 rather than the old ProductResponse body.
    // No frontend impact: updateProductStatus() already ignores the response body.
    @PatchMapping("/{id}/status")
    public ResponseEntity<Void> updateStatus(@PathVariable UUID id, @Valid @RequestBody ProductStatusRequest request) {
        productTypeStrategyResolver.updateStatus(id, request.status());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        productTypeStrategyResolver.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/kits/{id}")
    public KitProductResponse findKitById(@PathVariable UUID id) {
        return kitProductService.findById(id);
    }

    @PostMapping("/kits")
    public ResponseEntity<KitProductResponse> createKit(@AuthenticationPrincipal AuthContextService.Context principal,
                                                          @Valid @RequestBody KitProductRequest request) {
        KitProductResponse response = kitProductService.create(principal.tenantId(), request);
        return ResponseEntity.status(201).body(response);
    }

    @PutMapping("/kits/{id}")
    public KitProductResponse updateKit(@PathVariable UUID id, @Valid @RequestBody KitProductRequest request) {
        return kitProductService.update(id, request);
    }

    @GetMapping("/variations/{id}")
    public VariationParentResponse findVariationById(@PathVariable UUID id) {
        return variationProductService.findById(id);
    }

    @PostMapping("/variations")
    public ResponseEntity<VariationParentResponse> createVariation(
            @AuthenticationPrincipal AuthContextService.Context principal,
            @Valid @RequestBody VariationParentRequest request) {
        VariationParentResponse response = variationProductService.create(principal.tenantId(), request);
        return ResponseEntity.status(201).body(response);
    }

    @PutMapping("/variations/{id}")
    public VariationParentResponse updateVariation(@PathVariable UUID id, @Valid @RequestBody VariationParentRequest request) {
        return variationProductService.update(id, request);
    }
}
