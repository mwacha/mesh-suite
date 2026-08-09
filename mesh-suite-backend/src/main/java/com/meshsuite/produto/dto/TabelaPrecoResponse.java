package com.meshsuite.produto.dto;

import com.meshsuite.produto.domain.enums.Arredondamento;
import com.meshsuite.produto.domain.enums.MetodoAjuste;
import com.meshsuite.produto.domain.enums.ModoSelecaoProdutos;
import com.meshsuite.produto.domain.enums.OperacaoAjuste;
import com.meshsuite.produto.domain.enums.TipoValorAjuste;

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
