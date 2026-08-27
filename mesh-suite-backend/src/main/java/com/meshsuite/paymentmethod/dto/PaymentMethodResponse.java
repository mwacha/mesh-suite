package com.meshsuite.paymentmethod.dto;

import com.meshsuite.paymentmethod.domain.enums.PaymentMethodType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PaymentMethodResponse(
        UUID id,
        String description,
        PaymentMethodType type,
        String notes,
        Boolean active,
        Integer maxInstallments,
        BigDecimal interestRate,
        Integer settlementDays,
        Instant createdAt,
        List<PaymentMethodInstallmentResponse> installments) {
}
