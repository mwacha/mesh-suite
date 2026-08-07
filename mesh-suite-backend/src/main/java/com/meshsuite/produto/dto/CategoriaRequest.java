package com.meshsuite.produto.dto;

import jakarta.validation.constraints.NotBlank;

public record CategoriaRequest(
        @NotBlank String nome,
        String descricao,
        Boolean ativo) {
}
