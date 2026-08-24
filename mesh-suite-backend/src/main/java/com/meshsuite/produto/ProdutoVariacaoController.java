package com.meshsuite.produto;

import com.meshsuite.auth.AuthContextService;
import com.meshsuite.produto.dto.ProdutoVariacaoRequest;
import com.meshsuite.produto.dto.ProdutoVariacaoResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/produtos/variacoes")
public class ProdutoVariacaoController {

    private final ProdutoVariacaoService produtoVariacaoService;

    public ProdutoVariacaoController(ProdutoVariacaoService produtoVariacaoService) {
        this.produtoVariacaoService = produtoVariacaoService;
    }

    @GetMapping("/{id}")
    public ProdutoVariacaoResponse buscarPorId(@PathVariable UUID id) {
        return produtoVariacaoService.buscarPorId(id);
    }

    @PostMapping
    public ResponseEntity<ProdutoVariacaoResponse> criar(@AuthenticationPrincipal AuthContextService.Context principal,
                                                           @Valid @RequestBody ProdutoVariacaoRequest request) {
        ProdutoVariacaoResponse response = produtoVariacaoService.criar(principal.tenantId(), request);
        return ResponseEntity.status(201).body(response);
    }

    @PutMapping("/{id}")
    public ProdutoVariacaoResponse atualizar(@PathVariable UUID id, @Valid @RequestBody ProdutoVariacaoRequest request) {
        return produtoVariacaoService.atualizar(id, request);
    }
}
