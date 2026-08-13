package com.meshsuite.product.controller;

import com.meshsuite.auth.service.AuthContextService;
import com.meshsuite.product.domain.enums.ProductStatus;
import com.meshsuite.product.dto.*;
import com.meshsuite.product.service.ProductService;
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

    private final ProductService produtoService;

    public ProductController(ProductService produtoService) {
        this.produtoService = produtoService;
    }

    @GetMapping
    public Page<ProductListItemResponse> listar(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) ProductStatus status,
            @PageableDefault(size = 10, sort = "name") Pageable pageable) {
        return produtoService.listar(busca, status, pageable);
    }

    @GetMapping("/resumo")
    public ProductSummaryResponse resumo() {
        return produtoService.resumo();
    }

    @GetMapping("/{id}")
    public ProductResponse buscarPorId(@PathVariable UUID id) {
        return produtoService.buscarPorId(id);
    }

    @PostMapping
    public ResponseEntity<ProductResponse> criar(@AuthenticationPrincipal AuthContextService.Context principal,
                                                  @Valid @RequestBody ProductRequest request) {
        ProductResponse response = produtoService.criar(principal.tenantId(), request);
        return ResponseEntity.status(201).body(response);
    }

    @PutMapping("/{id}")
    public ProductResponse atualizar(@PathVariable UUID id, @Valid @RequestBody ProductRequest request) {
        return produtoService.atualizar(id, request);
    }

    @PatchMapping("/{id}/status")
    public ProductResponse atualizarStatus(@PathVariable UUID id, @Valid @RequestBody ProductStatusRequest request) {
        return produtoService.atualizarStatus(id, request.status());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        produtoService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
