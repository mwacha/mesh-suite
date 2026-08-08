package com.meshsuite.produto.dto;

import com.meshsuite.produto.MetodoAjuste;
import com.meshsuite.produto.OperacaoAjuste;
import com.meshsuite.produto.TipoValorAjuste;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TabelaPrecoSummaryResponse(
        UUID id,
        String nome,
        MetodoAjuste metodoAjuste,
        OperacaoAjuste operacaoAjuste,
        TipoValorAjuste tipoValorAjuste,
        BigDecimal valorAjuste,
        LocalDate inicioVigencia,
        LocalDate terminoVigencia,
        Boolean ativo) {
}
