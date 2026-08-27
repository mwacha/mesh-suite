package com.meshsuite.brand.exception;

public class BrandNotFoundException extends RuntimeException {
    public BrandNotFoundException() {
        super("Marca não encontrada");
    }
}
