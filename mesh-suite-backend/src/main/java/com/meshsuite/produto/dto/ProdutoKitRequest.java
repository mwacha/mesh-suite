package com.meshsuite.produto.dto;

import com.meshsuite.produto.StatusProduto;
import com.meshsuite.produto.UnidadeMedida;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ProdutoKitRequest(
        @NotBlank String nome,
        @NotBlank String sku,
        String codigoBarras,
        UnidadeMedida unidadeMedida,
        StatusProduto status,
        String descricao,
        @NotEmpty List<@Valid ProdutoKitItemRequest> itens) {
}
