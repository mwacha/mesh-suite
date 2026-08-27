package com.meshsuite.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record KitItemInput(
        @NotNull UUID componentProductId,
        @NotNull @DecimalMin(value = "0.001") BigDecimal quantity) {
}
