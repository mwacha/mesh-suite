package com.meshsuite.stock;

public class StockValidationException extends RuntimeException {
    public StockValidationException(String message) {
        super(message);
    }
}
