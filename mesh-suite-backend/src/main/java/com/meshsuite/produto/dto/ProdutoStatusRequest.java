package com.meshsuite.produto.dto;

import com.meshsuite.produto.domain.enums.StatusProduto;
import jakarta.validation.constraints.NotNull;

public record ProdutoStatusRequest(@NotNull StatusProduto status) {
}
