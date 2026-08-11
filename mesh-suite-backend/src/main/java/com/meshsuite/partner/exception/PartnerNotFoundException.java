package com.meshsuite.partner.exception;

public class PartnerNotFoundException extends RuntimeException {
    public PartnerNotFoundException() {
        super("Parceiro não encontrado");
    }
}
