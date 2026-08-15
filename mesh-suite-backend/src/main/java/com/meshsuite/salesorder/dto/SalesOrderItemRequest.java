package com.meshsuite.salesorder.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record SalesOrderItemRequest(
        @NotNull UUID productId,
        @NotNull @DecimalMin(value = "0.001") BigDecimal quantity,
        @NotNull @DecimalMin(value = "0.00") BigDecimal unitPrice) {
}
