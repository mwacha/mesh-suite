package com.meshsuite.partner.dto;

import com.meshsuite.partner.domain.enums.PartnerRole;
import com.meshsuite.partner.domain.enums.PersonType;
import com.meshsuite.partner.domain.enums.TaxIndicator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record PartnerRequest(
        @NotNull(message = "Tipo de Pessoa é obrigatório") PersonType personType,
        @NotBlank(message = "CNPJ / CPF é obrigatório") String document,
        @NotBlank(message = "Nome Fantasia é obrigatório")
        @Size(max = 100, message = "Nome Fantasia deve ter no máximo 100 caracteres") String tradeName,
        @Size(max = 150, message = "Razão Social deve ter no máximo 150 caracteres") String legalName,
        @NotEmpty(message = "Selecione ao menos um Tipo de Papel") Set<PartnerRole> roles,
        @Size(max = 500, message = "E-mail(s) deve ter no máximo 500 caracteres") String billingEmails,
        @Size(max = 20, message = "WhatsApp deve ter no máximo 20 caracteres") String whatsapp,
        TaxIndicator taxIndicator,
        @Size(max = 20, message = "Inscrição Estadual deve ter no máximo 20 caracteres") String stateRegistration,
        @Size(max = 20, message = "Inscrição Municipal deve ter no máximo 20 caracteres") String municipalRegistration,
        @Size(max = 20, message = "Inscrição Suframa deve ter no máximo 20 caracteres") String suframaRegistration,
        @Size(max = 8, message = "CEP deve ter 8 dígitos") String zipCode,
        @Size(max = 100, message = "Endereço deve ter no máximo 100 caracteres") String street,
        @Size(max = 10, message = "Número deve ter no máximo 10 caracteres") String number,
        @Size(max = 60, message = "Bairro deve ter no máximo 60 caracteres") String neighborhood,
        @Size(max = 100, message = "Complemento deve ter no máximo 100 caracteres") String complement,
        @Size(max = 2, message = "UF inválida") String state,
        @Size(max = 60, message = "Cidade deve ter no máximo 60 caracteres") String city,
        String notes,
        @Valid List<PartnerContactDto> contacts,
        UUID paymentMethodId) {
}
