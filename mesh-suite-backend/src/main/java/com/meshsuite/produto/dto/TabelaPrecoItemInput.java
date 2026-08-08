package com.meshsuite.produto.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record TabelaPrecoItemInput(
        @NotNull UUID produtoId,
        BigDecimal precoNestaTabela,
        BigDecimal percentualComissao) {
}
