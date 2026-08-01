package com.meshsuite.pedido.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record ItemPedidoDto(
        @NotNull UUID produtoId,
        @NotNull @DecimalMin(value = "0.001") BigDecimal quantidade,
        @NotNull @DecimalMin(value = "0.00") BigDecimal valorUnitario) {
}
