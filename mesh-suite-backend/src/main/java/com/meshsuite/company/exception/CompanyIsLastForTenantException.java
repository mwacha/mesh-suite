package com.meshsuite.company.exception;

public class CompanyIsLastForTenantException extends RuntimeException {
    public CompanyIsLastForTenantException() {
        super("Não é possível excluir: esta é a única empresa cadastrada para o tenant");
    }
}
