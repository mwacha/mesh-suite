package com.meshsuite.produto.exception;

public class CorEstampaEmUsoException extends RuntimeException {
    public CorEstampaEmUsoException(long quantidadeProdutos) {
        super("Não é possível excluir: " + quantidadeProdutos + " produto(s) usam esta cor/estampa");
    }
}
