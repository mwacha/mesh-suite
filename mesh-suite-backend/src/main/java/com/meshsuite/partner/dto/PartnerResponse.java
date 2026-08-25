package com.meshsuite.partner.dto;

import com.meshsuite.partner.domain.enums.PartnerRole;
import com.meshsuite.partner.domain.enums.PartnerStatus;
import com.meshsuite.partner.domain.enums.PersonType;
import com.meshsuite.partner.domain.enums.TaxIndicator;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record PartnerResponse(
        UUID id,
        PersonType personType,
        String document,
        String tradeName,
        String legalName,
        PartnerStatus status,
        Set<PartnerRole> roles,
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
        List<PartnerContactDto> contacts,
        UUID paymentMethodId,
        String paymentMethodDescription) {
}
