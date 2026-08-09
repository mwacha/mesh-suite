package com.meshsuite.produto.dto;

import com.meshsuite.produto.domain.enums.StatusProduto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProdutoSummaryResponse(
        UUID id,
        String nome,
        String sku,
        String marca,
        BigDecimal precoVenda,
        BigDecimal quantidadeEstoque,
        StatusProduto status) {
}
