package com.meshsuite.paymentmethod.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record PaymentMethodInstallmentInput(
        @NotNull Integer daysDue,
        @NotNull BigDecimal percentage) {
}
