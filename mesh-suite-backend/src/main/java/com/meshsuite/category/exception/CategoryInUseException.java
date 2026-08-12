package com.meshsuite.category.exception;

public class CategoryInUseException extends RuntimeException {
    public CategoryInUseException(long productCount) {
        super("Não é possível excluir: " + productCount + " produto(s) usam esta categoria");
    }
}
