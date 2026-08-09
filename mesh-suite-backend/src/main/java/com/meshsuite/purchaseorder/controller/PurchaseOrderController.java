package com.meshsuite.purchaseorder.controller;

import com.meshsuite.auth.service.AuthContextService;
import com.meshsuite.purchaseorder.domain.enums.PurchaseOrderStatus;
import com.meshsuite.purchaseorder.dto.*;
import com.meshsuite.purchaseorder.service.PurchaseOrderService;
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
@RequestMapping("/api/purchase-orders")
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;

    public PurchaseOrderController(PurchaseOrderService purchaseOrderService) {
        this.purchaseOrderService = purchaseOrderService;
    }

    @GetMapping
    public Page<PurchaseOrderSummaryResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) PurchaseOrderStatus status,
            @PageableDefault(size = 10, sort = "number", direction = Sort.Direction.DESC) Pageable pageable) {
        return purchaseOrderService.list(search, status, pageable);
    }

    @GetMapping("/counts")
    public PurchaseOrderCountsResponse counts() {
        return purchaseOrderService.counts();
    }

    @GetMapping("/{id}")
    public PurchaseOrderResponse findById(@PathVariable UUID id) {
        return purchaseOrderService.findById(id);
    }

    @PostMapping
    public ResponseEntity<PurchaseOrderResponse> create(@AuthenticationPrincipal AuthContextService.Context principal,
                                                          @Valid @RequestBody PurchaseOrderRequest request) {
        PurchaseOrderResponse response = purchaseOrderService.create(principal.tenantId(), request);
        return ResponseEntity.status(201).body(response);
    }

    @PutMapping("/{id}")
    public PurchaseOrderResponse update(@PathVariable UUID id, @Valid @RequestBody PurchaseOrderRequest request) {
        return purchaseOrderService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public PurchaseOrderResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody PurchaseOrderStatusRequest request) {
        return purchaseOrderService.updateStatus(id, request.status());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        purchaseOrderService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
