package com.meshsuite.produto.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record VarianteRequest(
        @NotEmpty List<@NotBlank String> combinacao,
        @NotBlank @Size(max = 50) String sku,
        @Size(max = 50) String codigoBarras,
        @NotNull @DecimalMin(value = "0.01") BigDecimal precoVenda,
        BigDecimal precoCusto,
        BigDecimal quantidadeEstoque,
        BigDecimal estoqueMinimo,
        BigDecimal estoqueMaximo,
        BigDecimal peso,
        BigDecimal comprimento,
        BigDecimal largura,
        BigDecimal altura) {
}
