package com.meshsuite.fiscal.dto;

import java.math.BigDecimal;

public record FiscalCalculationResult(
        BigDecimal icmsValue,
        BigDecimal ipiValue,
        BigDecimal pisValue,
        BigDecimal cofinsValue
) {
}
