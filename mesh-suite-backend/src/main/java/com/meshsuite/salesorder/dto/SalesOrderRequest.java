package com.meshsuite.salesorder.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record SalesOrderRequest(
        @NotNull UUID customerId,
        @NotNull UUID salespersonId,
        LocalDate orderDate,
        LocalDate deliveryDate,
        BigDecimal discount,
        @NotEmpty List<@Valid SalesOrderItemRequest> items) {
}
