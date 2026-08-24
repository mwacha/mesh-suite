package com.meshsuite.produto.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record ProdutoKitItemRequest(
        @NotNull UUID produtoId,
        @NotNull @DecimalMin(value = "0.001") BigDecimal quantidade) {
}
