package com.meshsuite.produto.dto;

import com.meshsuite.produto.StatusProduto;
import com.meshsuite.produto.UnidadeMedida;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ProdutoVariacaoResponse(
        UUID id,
        String nome,
        String sku,
        String marca,
        String categoria,
        BigDecimal precoVenda,
        StatusProduto status,
        String descricao,
        UnidadeMedida unidadeMedida,
        List<TipoVariacaoResponse> tiposVariacao,
        List<VarianteResponse> variantes) {
}
