package com.meshsuite.paymentmethod.controller;

import com.meshsuite.auth.service.AuthContextService;
import com.meshsuite.paymentmethod.domain.enums.PaymentMethodType;
import com.meshsuite.paymentmethod.dto.PaymentMethodCountsResponse;
import com.meshsuite.paymentmethod.dto.PaymentMethodRequest;
import com.meshsuite.paymentmethod.dto.PaymentMethodResponse;
import com.meshsuite.paymentmethod.dto.PaymentMethodSummaryResponse;
import com.meshsuite.paymentmethod.service.PaymentMethodService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payment-methods")
public class PaymentMethodController {

    private final PaymentMethodService paymentMethodService;

    public PaymentMethodController(PaymentMethodService paymentMethodService) {
        this.paymentMethodService = paymentMethodService;
    }

    @GetMapping
    public Page<PaymentMethodSummaryResponse> list(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) PaymentMethodType tipo,
            @RequestParam(required = false) Boolean ativo,
            @PageableDefault(size = 10, sort = "description") Pageable pageable) {
        return paymentMethodService.list(busca, tipo, ativo, pageable);
    }

    @GetMapping("/counts")
    public PaymentMethodCountsResponse counts() {
        return paymentMethodService.counts();
    }

    @GetMapping("/{id}")
    public PaymentMethodResponse findById(@PathVariable UUID id) {
        return paymentMethodService.findById(id);
    }

    @PostMapping
    public ResponseEntity<PaymentMethodResponse> create(@AuthenticationPrincipal AuthContextService.Context principal,
                                                          @Valid @RequestBody PaymentMethodRequest request) {
        PaymentMethodResponse response = paymentMethodService.create(principal.tenantId(), request);
        return ResponseEntity.status(201).body(response);
    }

    @PutMapping("/{id}")
    public PaymentMethodResponse update(@PathVariable UUID id, @Valid @RequestBody PaymentMethodRequest request) {
        return paymentMethodService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        paymentMethodService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
