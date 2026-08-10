package com.meshsuite.sale.controller;

import com.meshsuite.sale.dto.SaleResponse;
import com.meshsuite.sale.dto.SaleSummaryResponse;
import com.meshsuite.sale.service.SaleService;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sales")
public class SaleController {

    private final SaleService saleService;

    public SaleController(SaleService saleService) {
        this.saleService = saleService;
    }

    @GetMapping
    public Page<SaleSummaryResponse> list(
            @RequestParam(required = false) String busca,
            @PageableDefault(size = 10, sort = "number", direction = Sort.Direction.DESC) Pageable pageable) {
        return saleService.list(busca, pageable);
    }

    @GetMapping("/{id}")
    public SaleResponse findById(@PathVariable UUID id) {
        return saleService.findById(id);
    }

    @PostMapping("/issue/{orderId}")
    public ResponseEntity<SaleResponse> issue(@PathVariable UUID orderId) {
        SaleResponse response = saleService.issue(orderId);
        return ResponseEntity.status(201).body(response);
    }
}
