package com.meshsuite.partner.controller;

import com.meshsuite.auth.service.AuthContextService;
import com.meshsuite.partner.domain.enums.PartnerRole;
import com.meshsuite.partner.domain.enums.PartnerStatus;
import com.meshsuite.partner.domain.enums.PersonType;
import com.meshsuite.partner.dto.*;
import com.meshsuite.partner.service.PartnerService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/partners")
public class PartnerController {

    private final PartnerService partnerService;

    public PartnerController(PartnerService partnerService) {
        this.partnerService = partnerService;
    }

    @GetMapping
    public Page<PartnerListItemResponse> list(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) List<PartnerStatus> status,
            @RequestParam(required = false) List<PersonType> tipoDocumento,
            @RequestParam(required = false) String documento,
            @RequestParam(required = false) List<String> uf,
            @RequestParam(required = false) List<String> cidade,
            @RequestParam(required = false) PartnerRole papel,
            @PageableDefault(size = 10, sort = "tradeName") Pageable pageable) {
        return partnerService.list(busca, status, tipoDocumento, documento, uf, cidade, papel, pageable);
    }

    @GetMapping("/summary")
    public PartnerSummaryResponse summary(@RequestParam(required = false) PartnerRole papel) {
        return partnerService.summary(papel);
    }

    @GetMapping("/{id}")
    public PartnerResponse findById(@PathVariable UUID id) {
        return partnerService.findById(id);
    }

    @PostMapping
    public ResponseEntity<PartnerResponse> create(@AuthenticationPrincipal AuthContextService.Context principal,
                                                   @Valid @RequestBody PartnerRequest request) {
        PartnerResponse response = partnerService.create(principal.tenantId(), request);
        return ResponseEntity.status(201).body(response);
    }

    @PutMapping("/{id}")
    public PartnerResponse update(@PathVariable UUID id, @Valid @RequestBody PartnerRequest request) {
        return partnerService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public PartnerResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody PartnerStatusRequest request) {
        return partnerService.updateStatus(id, request.status());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        partnerService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
