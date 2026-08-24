package com.meshsuite.produto.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record TipoVariacaoRequest(
        @NotBlank String nome,
        @NotEmpty List<@NotBlank String> valores) {
}
