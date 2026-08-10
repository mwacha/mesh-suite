package com.meshsuite.sale.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record SaleResponse(
        UUID id,
        Integer number,
        UUID orderId,
        Integer orderNumber,
        UUID customerId,
        String customerName,
        UUID salespersonId,
        String salespersonName,
        LocalDate issueDate,
        BigDecimal discount,
        BigDecimal subtotal,
        BigDecimal total,
        BigDecimal icmsAmount,
        BigDecimal ipiAmount,
        BigDecimal pisAmount,
        BigDecimal cofinsAmount,
        List<SaleItemResponse> items) {
}
