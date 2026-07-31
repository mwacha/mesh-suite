package com.meshsuite.produto;

public class SkuDuplicadoException extends RuntimeException {
    public SkuDuplicadoException() {
        super("Já existe um produto cadastrado com este SKU");
    }
}
