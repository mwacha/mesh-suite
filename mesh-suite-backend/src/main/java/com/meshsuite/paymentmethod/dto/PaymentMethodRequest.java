package com.meshsuite.paymentmethod.dto;

import com.meshsuite.paymentmethod.domain.enums.PaymentMethodType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.List;

public record PaymentMethodRequest(
        @NotBlank String description,
        @NotNull PaymentMethodType type,
        String notes,
        Boolean active,
        @NotNull @Min(1) Integer maxInstallments,
        @PositiveOrZero BigDecimal interestRate,
        @PositiveOrZero Integer settlementDays,
        // Opcional: a tela de cadastro trabalha com "máx. de parcelas" e não
        // edita o parcelamento detalhado (dias/percentual). Quando vem null o
        // parcelamento já gravado é preservado; uma lista explícita substitui.
        List<@Valid PaymentMethodInstallmentInput> installments) {
}
