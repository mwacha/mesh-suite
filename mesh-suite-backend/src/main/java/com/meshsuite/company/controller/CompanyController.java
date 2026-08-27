package com.meshsuite.company.controller;

import com.meshsuite.auth.service.AuthContextService;
import com.meshsuite.company.dto.CompanyCountsResponse;
import com.meshsuite.company.dto.CompanyRequest;
import com.meshsuite.company.dto.CompanyResponse;
import com.meshsuite.company.dto.CompanyStatusRequest;
import com.meshsuite.company.service.CompanyService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/companies")
public class CompanyController {

    private final CompanyService companyService;

    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    @GetMapping
    public Page<CompanyResponse> list(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) Boolean ativo,
            @RequestParam(required = false) String uf,
            @RequestParam(required = false) String cidade,
            @PageableDefault(size = 10, sort = "legalName") Pageable pageable) {
        return companyService.list(busca, ativo, uf, cidade, pageable);
    }

    @GetMapping("/counts")
    public CompanyCountsResponse counts() {
        return companyService.counts();
    }

    @GetMapping("/{id}")
    public CompanyResponse findById(@PathVariable UUID id) {
        return companyService.findById(id);
    }

    @PostMapping
    public ResponseEntity<CompanyResponse> create(@AuthenticationPrincipal AuthContextService.Context principal,
                                                    @Valid @RequestBody CompanyRequest request) {
        CompanyResponse response = companyService.create(principal.tenantId(), request);
        return ResponseEntity.status(201).body(response);
    }

    @PutMapping("/{id}")
    public CompanyResponse update(@PathVariable UUID id, @Valid @RequestBody CompanyRequest request) {
        return companyService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public CompanyResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody CompanyStatusRequest request) {
        return companyService.updateStatus(id, request.active());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        companyService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
