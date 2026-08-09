package com.meshsuite.produto.dto;

import com.meshsuite.produto.domain.enums.Arredondamento;
import com.meshsuite.produto.domain.enums.MetodoAjuste;
import com.meshsuite.produto.domain.enums.ModoSelecaoProdutos;
import com.meshsuite.produto.domain.enums.OperacaoAjuste;
import com.meshsuite.produto.domain.enums.TipoValorAjuste;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record TabelaPrecoRequest(
        @NotBlank String nome,
        @NotNull ModoSelecaoProdutos modoSelecaoProdutos,
        @NotNull MetodoAjuste metodoAjuste,
        OperacaoAjuste operacaoAjuste,
        TipoValorAjuste tipoValorAjuste,
        BigDecimal valorAjuste,
        @NotNull Arredondamento arredondamento,
        @NotNull LocalDate inicioVigencia,
        LocalDate terminoVigencia,
        BigDecimal valorMinimoVenda,
        BigDecimal percentualComissaoPadrao,
        Boolean ativo,
        @NotNull List<@Valid TabelaPrecoItemInput> itens) {
}
