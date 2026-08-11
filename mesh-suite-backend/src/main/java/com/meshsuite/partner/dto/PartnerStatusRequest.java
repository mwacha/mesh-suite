package com.meshsuite.partner.dto;

import com.meshsuite.partner.domain.enums.PartnerStatus;
import jakarta.validation.constraints.NotNull;

public record PartnerStatusRequest(@NotNull PartnerStatus status) {
}
