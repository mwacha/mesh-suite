package com.meshsuite.produto;

import com.meshsuite.auth.AuthContextService;
import com.meshsuite.produto.dto.CategoriaRequest;
import com.meshsuite.produto.dto.CategoriaResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/categorias")
public class CategoriaController {

    private final CategoriaService categoriaService;

    public CategoriaController(CategoriaService categoriaService) {
        this.categoriaService = categoriaService;
    }

    @GetMapping
    public Page<CategoriaResponse> listar(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) Boolean ativo,
            @PageableDefault(size = 10, sort = "nome") Pageable pageable) {
        return categoriaService.listar(busca, ativo, pageable);
    }

    @GetMapping("/{id}")
    public CategoriaResponse buscarPorId(@PathVariable UUID id) {
        return categoriaService.buscarPorId(id);
    }

    @PostMapping
    public ResponseEntity<CategoriaResponse> criar(@AuthenticationPrincipal AuthContextService.Context principal,
                                                     @Valid @RequestBody CategoriaRequest request) {
        CategoriaResponse response = categoriaService.criar(principal.tenantId(), request);
        return ResponseEntity.status(201).body(response);
    }

    @PutMapping("/{id}")
    public CategoriaResponse atualizar(@PathVariable UUID id, @Valid @RequestBody CategoriaRequest request) {
        return categoriaService.atualizar(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        categoriaService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
