package com.meshsuite.colorway.exception;

public class ColorwayNotFoundException extends RuntimeException {
    public ColorwayNotFoundException() {
        super("Cor/Estampa não encontrada");
    }
}
