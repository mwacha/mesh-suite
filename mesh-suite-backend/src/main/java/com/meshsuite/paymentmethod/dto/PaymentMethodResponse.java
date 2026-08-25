package com.meshsuite.paymentmethod.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PaymentMethodResponse(
        UUID id,
        String description,
        Boolean active,
        Instant createdAt,
        List<PaymentMethodInstallmentResponse> installments) {
}
