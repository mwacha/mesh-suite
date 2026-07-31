package com.meshsuite.produto;

import com.meshsuite.auth.AuthContextService;
import com.meshsuite.produto.dto.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/produtos")
public class ProdutoController {

    private final ProdutoService produtoService;

    public ProdutoController(ProdutoService produtoService) {
        this.produtoService = produtoService;
    }

    @GetMapping
    public Page<ProdutoSummaryResponse> listar(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) StatusProduto status,
            @PageableDefault(size = 10, sort = "nome") Pageable pageable) {
        return produtoService.listar(busca, status, pageable);
    }

    @GetMapping("/resumo")
    public ProdutoResumoResponse resumo() {
        return produtoService.resumo();
    }

    @GetMapping("/{id}")
    public ProdutoResponse buscarPorId(@PathVariable UUID id) {
        return produtoService.buscarPorId(id);
    }

    @PostMapping
    public ResponseEntity<ProdutoResponse> criar(@AuthenticationPrincipal AuthContextService.Context principal,
                                                  @Valid @RequestBody ProdutoRequest request) {
        ProdutoResponse response = produtoService.criar(principal.tenantId(), request);
        return ResponseEntity.status(201).body(response);
    }

    @PutMapping("/{id}")
    public ProdutoResponse atualizar(@PathVariable UUID id, @Valid @RequestBody ProdutoRequest request) {
        return produtoService.atualizar(id, request);
    }

    @PatchMapping("/{id}/status")
    public ProdutoResponse atualizarStatus(@PathVariable UUID id, @Valid @RequestBody ProdutoStatusRequest request) {
        return produtoService.atualizarStatus(id, request.status());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        produtoService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
