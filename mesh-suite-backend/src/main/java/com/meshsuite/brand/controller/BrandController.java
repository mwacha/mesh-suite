package com.meshsuite.brand.controller;

import com.meshsuite.auth.service.AuthContextService;
import com.meshsuite.brand.dto.BrandCountsResponse;
import com.meshsuite.brand.dto.BrandRequest;
import com.meshsuite.brand.dto.BrandResponse;
import com.meshsuite.brand.service.BrandService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/brands")
public class BrandController {

    private final BrandService brandService;

    public BrandController(BrandService brandService) {
        this.brandService = brandService;
    }

    @GetMapping
    public Page<BrandResponse> list(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) Boolean ativo,
            @PageableDefault(size = 10, sort = "name") Pageable pageable) {
        return brandService.list(busca, ativo, pageable);
    }

    @GetMapping("/counts")
    public BrandCountsResponse counts() {
        return brandService.counts();
    }

    @GetMapping("/{id}")
    public BrandResponse findById(@PathVariable UUID id) {
        return brandService.findById(id);
    }

    @PostMapping
    public ResponseEntity<BrandResponse> create(@AuthenticationPrincipal AuthContextService.Context principal,
                                                  @Valid @RequestBody BrandRequest request) {
        BrandResponse response = brandService.create(principal.tenantId(), request);
        return ResponseEntity.status(201).body(response);
    }

    @PutMapping("/{id}")
    public BrandResponse update(@PathVariable UUID id, @Valid @RequestBody BrandRequest request) {
        return brandService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        brandService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
