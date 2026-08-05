package com.meshsuite.purchaseorder.dto;

import com.meshsuite.purchaseorder.PurchaseOrderStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PurchaseOrderSummaryResponse(
        UUID id,
        Integer number,
        String supplierName,
        String buyerName,
        LocalDate orderDate,
        BigDecimal total,
        PurchaseOrderStatus status) {
}
