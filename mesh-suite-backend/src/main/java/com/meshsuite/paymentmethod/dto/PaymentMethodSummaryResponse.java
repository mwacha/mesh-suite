package com.meshsuite.paymentmethod.dto;

import java.util.UUID;

public record PaymentMethodSummaryResponse(
        UUID id,
        String description,
        Boolean active,
        Integer installmentsCount) {
}
