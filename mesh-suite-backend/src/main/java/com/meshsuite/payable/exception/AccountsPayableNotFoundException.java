package com.meshsuite.payable.exception;

public class AccountsPayableNotFoundException extends RuntimeException {
    public AccountsPayableNotFoundException() {
        super("Conta a pagar não encontrada");
    }
}
