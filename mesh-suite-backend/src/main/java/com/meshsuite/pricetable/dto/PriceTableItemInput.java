package com.meshsuite.pricetable.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record PriceTableItemInput(
        @NotNull UUID productId,
        BigDecimal tablePrice,
        BigDecimal commissionPercentage) {
}
