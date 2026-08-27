package com.meshsuite.brand.exception;

public class BrandInUseException extends RuntimeException {
    public BrandInUseException(long productCount) {
        super("Não é possível excluir: " + productCount + " produto(s) usam esta marca");
    }
}
