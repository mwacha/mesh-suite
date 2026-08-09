package com.meshsuite.purchaseorder.dto;

import com.meshsuite.purchaseorder.domain.enums.PurchaseOrderStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PurchaseOrderResponse(
        UUID id,
        Integer number,
        UUID supplierId,
        String supplierName,
        UUID buyerId,
        String buyerName,
        LocalDate orderDate,
        LocalDate expectedDeliveryDate,
        PurchaseOrderStatus status,
        BigDecimal discount,
        BigDecimal subtotal,
        BigDecimal total,
        List<PurchaseOrderItemResponse> items) {
}
