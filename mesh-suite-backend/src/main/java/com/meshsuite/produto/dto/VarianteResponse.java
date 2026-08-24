package com.meshsuite.produto.dto;

import java.math.BigDecimal;
import java.util.List;

public record VarianteResponse(
        List<String> combinacao,
        String sku,
        String codigoBarras,
        BigDecimal precoVenda,
        BigDecimal precoCusto,
        BigDecimal quantidadeEstoque,
        BigDecimal estoqueMinimo,
        BigDecimal estoqueMaximo,
        BigDecimal peso,
        BigDecimal comprimento,
        BigDecimal largura,
        BigDecimal altura) {
}
