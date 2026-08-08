package com.meshsuite.produto;

import com.meshsuite.auth.AuthContextService;
import com.meshsuite.produto.dto.CorEstampaRequest;
import com.meshsuite.produto.dto.CorEstampaResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/cores-estampas")
public class CorEstampaController {

    private final CorEstampaService corEstampaService;

    public CorEstampaController(CorEstampaService corEstampaService) {
        this.corEstampaService = corEstampaService;
    }

    @GetMapping
    public Page<CorEstampaResponse> listar(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) Boolean ativo,
            @PageableDefault(size = 10, sort = "nome") Pageable pageable) {
        return corEstampaService.listar(busca, ativo, pageable);
    }

    @GetMapping("/{id}")
    public CorEstampaResponse buscarPorId(@PathVariable UUID id) {
        return corEstampaService.buscarPorId(id);
    }

    @PostMapping
    public ResponseEntity<CorEstampaResponse> criar(@AuthenticationPrincipal AuthContextService.Context principal,
                                                      @Valid @RequestBody CorEstampaRequest request) {
        CorEstampaResponse response = corEstampaService.criar(principal.tenantId(), request);
        return ResponseEntity.status(201).body(response);
    }

    @PutMapping("/{id}")
    public CorEstampaResponse atualizar(@PathVariable UUID id, @Valid @RequestBody CorEstampaRequest request) {
        return corEstampaService.atualizar(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        corEstampaService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
