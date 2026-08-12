package com.meshsuite.product.exception;

public class DuplicateSkuException extends RuntimeException {
    public DuplicateSkuException() {
        super("Já existe um produto cadastrado com este SKU");
    }
}
