package com.meshsuite.produto.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record TabelaPrecoItemResponse(
        UUID produtoId,
        String produtoNome,
        String produtoSku,
        BigDecimal precoCadastrado,
        BigDecimal precoNestaTabela,
        BigDecimal percentualComissao) {
}
