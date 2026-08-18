package com.meshsuite.purchaseinvoice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PurchaseInvoiceResponse(
        UUID id,
        Integer number,
        String invoiceNumber,
        String series,
        String model,
        UUID purchaseOrderId,
        Integer purchaseOrderNumber,
        UUID supplierId,
        String supplierName,
        LocalDate issueDate,
        LocalDate entryDate,
        BigDecimal discount,
        BigDecimal subtotal,
        BigDecimal total,
        BigDecimal icmsAmount,
        BigDecimal ipiAmount,
        BigDecimal pisAmount,
        BigDecimal cofinsAmount,
        List<PurchaseInvoiceItemResponse> items) {
}
