package com.meshsuite.produto.dto;

import com.meshsuite.produto.ProdutoTipo;
import com.meshsuite.produto.StatusProduto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProdutoSummaryResponse(
        UUID id,
        String nome,
        String sku,
        String marca,
        BigDecimal precoVenda,
        BigDecimal quantidadeEstoque,
        StatusProduto status,
        ProdutoTipo tipo) {
}
