package com.meshsuite.produto.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CorEstampaResponse(
        UUID id,
        String nome,
        LocalDate dataVigencia,
        String descricao,
        Boolean ativo,
        Long produtosVinculados,
        Instant criadoEm) {
}
