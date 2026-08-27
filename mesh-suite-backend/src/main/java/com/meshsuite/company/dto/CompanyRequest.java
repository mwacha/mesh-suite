package com.meshsuite.company.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompanyRequest(
        @NotBlank String legalName,
        @NotBlank @Size(min = 14, max = 14) String cnpj,
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
        String state) {
}
