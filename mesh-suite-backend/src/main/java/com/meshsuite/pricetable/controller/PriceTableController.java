package com.meshsuite.pricetable.controller;

import com.meshsuite.auth.service.AuthContextService;
import com.meshsuite.pricetable.dto.PriceTableRequest;
import com.meshsuite.pricetable.dto.PriceTableResponse;
import com.meshsuite.pricetable.dto.PriceTableSummaryResponse;
import com.meshsuite.pricetable.service.PriceTableService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/price-tables")
public class PriceTableController {

    private final PriceTableService tabelaPrecoService;

    public PriceTableController(PriceTableService tabelaPrecoService) {
        this.tabelaPrecoService = tabelaPrecoService;
    }

    @GetMapping
    public Page<PriceTableSummaryResponse> listar(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) Boolean ativo,
            @PageableDefault(size = 10, sort = "name") Pageable pageable) {
        return tabelaPrecoService.listar(busca, ativo, pageable);
    }

    @GetMapping("/{id}")
    public PriceTableResponse buscarPorId(@PathVariable UUID id) {
        return tabelaPrecoService.buscarPorId(id);
    }

    @PostMapping
    public ResponseEntity<PriceTableResponse> criar(@AuthenticationPrincipal AuthContextService.Context principal,
                                                        @Valid @RequestBody PriceTableRequest request) {
        PriceTableResponse response = tabelaPrecoService.criar(principal.tenantId(), request);
        return ResponseEntity.status(201).body(response);
    }

    @PutMapping("/{id}")
    public PriceTableResponse atualizar(@PathVariable UUID id, @Valid @RequestBody PriceTableRequest request) {
        return tabelaPrecoService.atualizar(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        tabelaPrecoService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
