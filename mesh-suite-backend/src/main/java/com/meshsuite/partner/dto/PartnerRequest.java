package com.meshsuite.partner.dto;

import com.meshsuite.partner.domain.enums.PartnerRole;
import com.meshsuite.partner.domain.enums.PersonType;
import com.meshsuite.partner.domain.enums.TaxIndicator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Set;

public record PartnerRequest(
        @NotNull PersonType personType,
        @NotBlank String document,
        @NotBlank String tradeName,
        String legalName,
        @NotEmpty Set<PartnerRole> roles,
        String billingEmails,
        String whatsapp,
        TaxIndicator taxIndicator,
        String stateRegistration,
        String municipalRegistration,
        String suframaRegistration,
        String zipCode,
        String street,
        String number,
        String neighborhood,
        String complement,
        String state,
        String city,
        String notes,
        List<PartnerContactDto> contacts) {
}
