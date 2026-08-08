package com.meshsuite.produto.dto;

import com.meshsuite.produto.Arredondamento;
import com.meshsuite.produto.MetodoAjuste;
import com.meshsuite.produto.ModoSelecaoProdutos;
import com.meshsuite.produto.OperacaoAjuste;
import com.meshsuite.produto.TipoValorAjuste;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record TabelaPrecoResponse(
        UUID id,
        String nome,
        ModoSelecaoProdutos modoSelecaoProdutos,
        MetodoAjuste metodoAjuste,
        OperacaoAjuste operacaoAjuste,
        TipoValorAjuste tipoValorAjuste,
        BigDecimal valorAjuste,
        Arredondamento arredondamento,
        LocalDate inicioVigencia,
        LocalDate terminoVigencia,
        BigDecimal valorMinimoVenda,
        BigDecimal percentualComissaoPadrao,
        Boolean ativo,
        Instant criadoEm,
        List<TabelaPrecoItemResponse> itens) {
}
