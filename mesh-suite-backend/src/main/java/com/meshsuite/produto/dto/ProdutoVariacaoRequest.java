package com.meshsuite.produto.dto;

import com.meshsuite.produto.StatusProduto;
import com.meshsuite.produto.UnidadeMedida;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record ProdutoVariacaoRequest(
        @NotBlank String nome,
        @NotBlank @Size(max = 50) String sku,
        String marca,
        String categoria,
        @NotNull @DecimalMin(value = "0.01") BigDecimal precoVenda,
        StatusProduto status,
        String descricao,
        UnidadeMedida unidadeMedida,
        @NotEmpty List<@Valid TipoVariacaoRequest> tiposVariacao,
        @NotEmpty List<@Valid VarianteRequest> variantes) {
}
