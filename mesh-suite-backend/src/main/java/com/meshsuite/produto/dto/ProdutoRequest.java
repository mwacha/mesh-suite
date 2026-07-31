package com.meshsuite.produto.dto;

import com.meshsuite.produto.StatusProduto;
import com.meshsuite.produto.UnidadeMedida;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ProdutoRequest(
        @NotBlank String nome,
        @NotBlank String sku,
        String codigoBarras,
        String marca,
        String categoria,
        @NotNull @DecimalMin(value = "0.01") BigDecimal precoVenda,
        BigDecimal precoCusto,
        StatusProduto status,
        String descricao,
        BigDecimal quantidadeEstoque,
        UnidadeMedida unidadeMedida,
        BigDecimal estoqueMinimo,
        BigDecimal estoqueMaximo,
        BigDecimal peso,
        BigDecimal comprimento,
        BigDecimal largura,
        BigDecimal altura) {
}
