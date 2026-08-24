package com.meshsuite.produto.dto;

import com.meshsuite.produto.StatusProduto;
import com.meshsuite.produto.UnidadeMedida;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ProdutoKitResponse(
        UUID id,
        String nome,
        String sku,
        String codigoBarras,
        UnidadeMedida unidadeMedida,
        StatusProduto status,
        String descricao,
        BigDecimal precoVenda,
        List<ProdutoKitItemResponse> itens) {
}
