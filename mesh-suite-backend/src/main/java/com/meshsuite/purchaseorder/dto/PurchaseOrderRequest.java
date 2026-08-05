package com.meshsuite.purchaseorder.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PurchaseOrderRequest(
        @NotNull UUID supplierId,
        @NotNull UUID buyerId,
        LocalDate orderDate,
        LocalDate expectedDeliveryDate,
        @DecimalMin(value = "0.00") BigDecimal discount,
        @NotEmpty List<@Valid PurchaseOrderItemRequest> items) {
}
