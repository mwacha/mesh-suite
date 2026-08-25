package com.meshsuite.paymentmethod.dto;

import java.math.BigDecimal;

public record PaymentMethodInstallmentResponse(
        Integer installmentNumber,
        Integer daysDue,
        BigDecimal percentage) {
}
