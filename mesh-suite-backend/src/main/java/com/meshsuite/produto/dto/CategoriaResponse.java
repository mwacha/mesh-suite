package com.meshsuite.produto.dto;

import java.time.Instant;
import java.util.UUID;

public record CategoriaResponse(
        UUID id,
        String nome,
        String descricao,
        Boolean ativo,
        Long produtosVinculados,
        Instant criadoEm) {
}
