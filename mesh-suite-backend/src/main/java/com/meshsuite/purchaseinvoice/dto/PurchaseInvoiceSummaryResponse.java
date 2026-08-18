package com.meshsuite.purchaseinvoice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PurchaseInvoiceSummaryResponse(
        UUID id,
        Integer number,
        String invoiceNumber,
        String supplierName,
        LocalDate issueDate,
        BigDecimal total) {
}
