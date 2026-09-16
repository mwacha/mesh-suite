package com.meshsuite.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
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
        String state,
        @NotBlank String adminName,
        @NotBlank @Email String adminEmail,
        @NotBlank @Size(min = 8) String senha) {
}
