package com.meshsuite.produto.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CorEstampaRequest(
        @NotBlank String nome,
        @NotNull LocalDate dataVigencia,
        String descricao,
        Boolean ativo) {
}
