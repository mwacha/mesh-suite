package com.meshsuite.company.dto;

import java.util.UUID;

public record CompanyResponse(
        UUID id,
        String legalName,
        String cnpj,
        String tradeName,
        String stateRegistration,
        String municipalRegistration,
        String phone,
        String email,
        String website,
        String zipCode,
        String street,
        String number,
        String complement,
        String neighborhood,
        String city,
        String state,
        Boolean active) {
}
