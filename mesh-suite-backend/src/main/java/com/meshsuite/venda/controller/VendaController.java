package com.meshsuite.venda.controller;

import com.meshsuite.venda.dto.VendaResponse;
import com.meshsuite.venda.dto.VendaSummaryResponse;
import com.meshsuite.venda.service.VendaService;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/vendas")
public class VendaController {

    private final VendaService vendaService;

    public VendaController(VendaService vendaService) {
        this.vendaService = vendaService;
    }

    @GetMapping
    public Page<VendaSummaryResponse> listar(
            @RequestParam(required = false) String busca,
            @PageableDefault(size = 10, sort = "numero", direction = Sort.Direction.DESC) Pageable pageable) {
        return vendaService.listar(busca, pageable);
    }

    @GetMapping("/{id}")
    public VendaResponse buscarPorId(@PathVariable UUID id) {
        return vendaService.buscarPorId(id);
    }

    @PostMapping("/faturar/{pedidoId}")
    public ResponseEntity<VendaResponse> faturar(@PathVariable UUID pedidoId) {
        VendaResponse response = vendaService.faturar(pedidoId);
        return ResponseEntity.status(201).body(response);
    }
}
