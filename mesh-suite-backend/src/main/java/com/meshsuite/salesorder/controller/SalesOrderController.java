package com.meshsuite.salesorder.controller;

import com.meshsuite.auth.service.AuthContextService;
import com.meshsuite.salesorder.domain.enums.SalesOrderStatus;
import com.meshsuite.salesorder.dto.*;
import com.meshsuite.salesorder.service.SalesOrderService;
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
@RequestMapping("/api/sales-orders")
public class SalesOrderController {

    private final SalesOrderService salesOrderService;

    public SalesOrderController(SalesOrderService salesOrderService) {
        this.salesOrderService = salesOrderService;
    }

    @GetMapping
    public Page<SalesOrderSummaryResponse> list(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) SalesOrderStatus status,
            @RequestParam(required = false) UUID salespersonId,
            @PageableDefault(size = 10, sort = "number", direction = Sort.Direction.DESC) Pageable pageable) {
        return salesOrderService.list(busca, status, salespersonId, pageable);
    }

    @GetMapping("/counts")
    public SalesOrderCountsResponse counts() {
        return salesOrderService.counts();
    }

    @GetMapping("/{id}")
    public SalesOrderResponse findById(@PathVariable UUID id) {
        return salesOrderService.findById(id);
    }

    @PostMapping
    public ResponseEntity<SalesOrderResponse> create(@AuthenticationPrincipal AuthContextService.Context principal,
                                                       @Valid @RequestBody SalesOrderRequest request) {
        SalesOrderResponse response = salesOrderService.create(principal.tenantId(), request);
        return ResponseEntity.status(201).body(response);
    }

    @PutMapping("/{id}")
    public SalesOrderResponse update(@PathVariable UUID id, @Valid @RequestBody SalesOrderRequest request) {
        return salesOrderService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public SalesOrderResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody SalesOrderStatusRequest request) {
        return salesOrderService.advanceStatus(id, request.status());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        salesOrderService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
