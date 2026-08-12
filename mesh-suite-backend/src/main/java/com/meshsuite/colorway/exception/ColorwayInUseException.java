package com.meshsuite.colorway.exception;

public class ColorwayInUseException extends RuntimeException {
    public ColorwayInUseException(long productCount) {
        super("Não é possível excluir: " + productCount + " produto(s) usam esta cor/estampa");
    }
}
