package com.meshsuite.produto;

import com.meshsuite.auth.AuthContextService;
import com.meshsuite.produto.dto.TabelaPrecoRequest;
import com.meshsuite.produto.dto.TabelaPrecoResponse;
import com.meshsuite.produto.dto.TabelaPrecoSummaryResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/tabelas-preco")
public class TabelaPrecoController {

    private final TabelaPrecoService tabelaPrecoService;

    public TabelaPrecoController(TabelaPrecoService tabelaPrecoService) {
        this.tabelaPrecoService = tabelaPrecoService;
    }

    @GetMapping
    public Page<TabelaPrecoSummaryResponse> listar(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) Boolean ativo,
            @PageableDefault(size = 10, sort = "nome") Pageable pageable) {
        return tabelaPrecoService.listar(busca, ativo, pageable);
    }

    @GetMapping("/{id}")
    public TabelaPrecoResponse buscarPorId(@PathVariable UUID id) {
        return tabelaPrecoService.buscarPorId(id);
    }

    @PostMapping
    public ResponseEntity<TabelaPrecoResponse> criar(@AuthenticationPrincipal AuthContextService.Context principal,
                                                        @Valid @RequestBody TabelaPrecoRequest request) {
        TabelaPrecoResponse response = tabelaPrecoService.criar(principal.tenantId(), request);
        return ResponseEntity.status(201).body(response);
    }

    @PutMapping("/{id}")
    public TabelaPrecoResponse atualizar(@PathVariable UUID id, @Valid @RequestBody TabelaPrecoRequest request) {
        return tabelaPrecoService.atualizar(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        tabelaPrecoService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
