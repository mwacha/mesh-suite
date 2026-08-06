package com.meshsuite.payable;

public class AccountsPayableNotFoundException extends RuntimeException {
    public AccountsPayableNotFoundException() {
        super("Conta a pagar não encontrada");
    }
}
