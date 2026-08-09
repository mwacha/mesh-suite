package com.meshsuite.pedido.controller;

import com.meshsuite.auth.service.AuthContextService;
import com.meshsuite.pedido.domain.enums.StatusPedido;
import com.meshsuite.pedido.dto.*;
import com.meshsuite.pedido.service.PedidoService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    private final PedidoService pedidoService;

    public PedidoController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    @GetMapping
    public Page<PedidoSummaryResponse> listar(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) StatusPedido status,
            @PageableDefault(size = 10, sort = "numero", direction = Sort.Direction.DESC) Pageable pageable) {
        return pedidoService.listar(busca, status, pageable);
    }

    @GetMapping("/resumo")
    public PedidoResumoResponse resumo() {
        return pedidoService.resumo();
    }

    @GetMapping("/{id}")
    public PedidoResponse buscarPorId(@PathVariable UUID id) {
        return pedidoService.buscarPorId(id);
    }

    @PostMapping
    public ResponseEntity<PedidoResponse> criar(@AuthenticationPrincipal AuthContextService.Context principal,
                                                 @Valid @RequestBody PedidoRequest request) {
        PedidoResponse response = pedidoService.criar(principal.tenantId(), request);
        return ResponseEntity.status(201).body(response);
    }

    @PutMapping("/{id}")
    public PedidoResponse atualizar(@PathVariable UUID id, @Valid @RequestBody PedidoRequest request) {
        return pedidoService.atualizar(id, request);
    }

    @PatchMapping("/{id}/status")
    public PedidoResponse atualizarStatus(@PathVariable UUID id, @Valid @RequestBody PedidoStatusRequest request) {
        return pedidoService.avancarStatus(id, request.status());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        pedidoService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
