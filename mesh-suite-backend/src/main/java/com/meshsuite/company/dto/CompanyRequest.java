package com.meshsuite.company.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompanyRequest(
        @NotBlank(message = "Razão Social é obrigatória")
        @Size(max = 150, message = "Razão Social deve ter no máximo 150 caracteres") String legalName,
        @NotBlank(message = "CNPJ é obrigatório")
        @Size(min = 14, max = 14, message = "CNPJ deve ter 14 dígitos") String cnpj,
        @Size(max = 100, message = "Nome Fantasia deve ter no máximo 100 caracteres") String tradeName,
        @Size(max = 20, message = "Inscrição Estadual deve ter no máximo 20 caracteres") String stateRegistration,
        @Size(max = 20, message = "Inscrição Municipal deve ter no máximo 20 caracteres") String municipalRegistration,
        @Size(max = 20, message = "Telefone deve ter no máximo 20 caracteres") String phone,
        @Size(max = 254, message = "E-mail deve ter no máximo 254 caracteres") String email,
        @Size(max = 255, message = "Site deve ter no máximo 255 caracteres") String website,
        @Size(max = 8, message = "CEP deve ter 8 dígitos") String zipCode,
        @Size(max = 100, message = "Logradouro deve ter no máximo 100 caracteres") String street,
        @Size(max = 10, message = "Número deve ter no máximo 10 caracteres") String number,
        @Size(max = 100, message = "Complemento deve ter no máximo 100 caracteres") String complement,
        @Size(max = 60, message = "Bairro deve ter no máximo 60 caracteres") String neighborhood,
        @Size(max = 60, message = "Cidade deve ter no máximo 60 caracteres") String city,
        @Size(max = 2, message = "UF inválida") String state) {
}
