package com.meshsuite.produto.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProdutoKitItemResponse(
        UUID produtoId,
        String produtoNome,
        String produtoSku,
        BigDecimal quantidade,
        BigDecimal precoVenda,
        BigDecimal totalItem) {
}
