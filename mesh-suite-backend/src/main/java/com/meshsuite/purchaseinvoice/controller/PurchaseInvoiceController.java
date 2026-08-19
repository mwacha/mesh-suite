package com.meshsuite.purchaseinvoice.controller;

import com.meshsuite.auth.service.AuthContextService;
import com.meshsuite.purchaseinvoice.dto.PurchaseInvoiceRequest;
import com.meshsuite.purchaseinvoice.dto.PurchaseInvoiceResponse;
import com.meshsuite.purchaseinvoice.dto.PurchaseInvoiceSummaryResponse;
import com.meshsuite.purchaseinvoice.service.PurchaseInvoiceService;
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
@RequestMapping("/api/purchase-invoices")
public class PurchaseInvoiceController {

    private final PurchaseInvoiceService purchaseInvoiceService;

    public PurchaseInvoiceController(PurchaseInvoiceService purchaseInvoiceService) {
        this.purchaseInvoiceService = purchaseInvoiceService;
    }

    @GetMapping
    public Page<PurchaseInvoiceSummaryResponse> list(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 10, sort = "number", direction = Sort.Direction.DESC) Pageable pageable) {
        return purchaseInvoiceService.list(search, pageable);
    }

    @GetMapping("/{id}")
    public PurchaseInvoiceResponse findById(@PathVariable UUID id) {
        return purchaseInvoiceService.findById(id);
    }

    @PostMapping("/issue/{purchaseOrderId}")
    public ResponseEntity<PurchaseInvoiceResponse> issue(@PathVariable UUID purchaseOrderId,
                                                           @AuthenticationPrincipal AuthContextService.Context principal,
                                                           @Valid @RequestBody PurchaseInvoiceRequest request) {
        PurchaseInvoiceResponse response = purchaseInvoiceService.issue(purchaseOrderId, request, principal.usuarioId());
        return ResponseEntity.status(201).body(response);
    }
}
