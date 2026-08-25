package com.meshsuite.paymentmethod.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record PaymentMethodRequest(
        @NotBlank String description,
        Boolean active,
        @NotEmpty List<@Valid PaymentMethodInstallmentInput> installments) {
}
