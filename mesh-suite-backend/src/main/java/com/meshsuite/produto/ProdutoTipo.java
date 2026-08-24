package com.meshsuite.produto;

public enum ProdutoTipo {
    /** Produto simples, vendido/estocado como uma unidade independente. */
    PRODUCT,
    /** Kit: composto por outros produtos (sempre PRODUCT), preço derivado dos itens. */
    PRODUCT_KIT,
    /** "Container" de um produto com variação -- não é vendido/estocado diretamente. */
    VARIATION_PARENT,
    /** Uma combinação gerada (ex.: Tamanho=P, Cor=Branco) de um VARIATION_PARENT; tem SKU e estoque próprios. */
    VARIATION_CHILD
}
