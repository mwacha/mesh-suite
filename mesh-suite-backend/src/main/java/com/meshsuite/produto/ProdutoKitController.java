package com.meshsuite.produto;

import com.meshsuite.auth.AuthContextService;
import com.meshsuite.produto.dto.ProdutoKitRequest;
import com.meshsuite.produto.dto.ProdutoKitResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/produtos/kits")
public class ProdutoKitController {

    private final ProdutoKitService produtoKitService;

    public ProdutoKitController(ProdutoKitService produtoKitService) {
        this.produtoKitService = produtoKitService;
    }

    @GetMapping("/{id}")
    public ProdutoKitResponse buscarPorId(@PathVariable UUID id) {
        return produtoKitService.buscarPorId(id);
    }

    @PostMapping
    public ResponseEntity<ProdutoKitResponse> criar(@AuthenticationPrincipal AuthContextService.Context principal,
                                                      @Valid @RequestBody ProdutoKitRequest request) {
        ProdutoKitResponse response = produtoKitService.criar(principal.tenantId(), request);
        return ResponseEntity.status(201).body(response);
    }
}
