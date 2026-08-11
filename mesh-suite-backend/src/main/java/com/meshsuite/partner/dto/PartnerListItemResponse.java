package com.meshsuite.partner.dto;

import com.meshsuite.partner.domain.enums.PartnerStatus;
import com.meshsuite.partner.domain.enums.PersonType;

import java.util.UUID;

public record PartnerListItemResponse(
        UUID id,
        String tradeName,
        String legalName,
        String document,
        PersonType personType,
        String city,
        String state,
        String whatsapp,
        PartnerStatus status) {
}
