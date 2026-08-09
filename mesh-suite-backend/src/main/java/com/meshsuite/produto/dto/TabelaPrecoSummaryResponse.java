package com.meshsuite.produto.dto;

import com.meshsuite.produto.domain.enums.MetodoAjuste;
import com.meshsuite.produto.domain.enums.OperacaoAjuste;
import com.meshsuite.produto.domain.enums.TipoValorAjuste;

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
