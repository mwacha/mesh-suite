package com.meshsuite.parceiro;

import com.meshsuite.auth.AuthContextService;
import com.meshsuite.parceiro.dto.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/parceiros")
public class ParceiroController {

    private final ParceiroService parceiroService;

    public ParceiroController(ParceiroService parceiroService) {
        this.parceiroService = parceiroService;
    }

    @GetMapping
    public Page<ParceiroSummaryResponse> listar(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) StatusParceiro status,
            @RequestParam(required = false) TipoPessoa tipoDocumento,
            @RequestParam(required = false) String uf,
            @RequestParam(required = false) String cidade,
            @PageableDefault(size = 10, sort = "nomeFantasia") Pageable pageable) {
        return parceiroService.listar(busca, status, tipoDocumento, uf, cidade, pageable);
    }

    @GetMapping("/resumo")
    public ParceiroResumoResponse resumo() {
        return parceiroService.resumo();
    }

    @GetMapping("/{id}")
    public ParceiroResponse buscarPorId(@PathVariable UUID id) {
        return parceiroService.buscarPorId(id);
    }

    @PostMapping
    public ResponseEntity<ParceiroResponse> criar(@AuthenticationPrincipal AuthContextService.Context principal,
                                                   @Valid @RequestBody ParceiroRequest request) {
        ParceiroResponse response = parceiroService.criar(principal.tenantId(), request);
        return ResponseEntity.status(201).body(response);
    }

    @PutMapping("/{id}")
    public ParceiroResponse atualizar(@PathVariable UUID id, @Valid @RequestBody ParceiroRequest request) {
        return parceiroService.atualizar(id, request);
    }

    @PatchMapping("/{id}/status")
    public ParceiroResponse atualizarStatus(@PathVariable UUID id, @Valid @RequestBody ParceiroStatusRequest request) {
        return parceiroService.atualizarStatus(id, request.status());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        parceiroService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
