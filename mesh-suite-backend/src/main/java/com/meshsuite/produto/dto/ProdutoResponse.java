package com.meshsuite.produto.dto;

import com.meshsuite.produto.StatusProduto;
import com.meshsuite.produto.UnidadeMedida;

import java.math.BigDecimal;
import java.util.UUID;

public record ProdutoResponse(
        UUID id,
        String nome,
        String sku,
        String codigoBarras,
        String marca,
        String categoria,
        BigDecimal precoVenda,
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
