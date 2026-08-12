package com.meshsuite.colorway.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ColorwayResponse(
        UUID id,
        String name,
        LocalDate effectiveDate,
        String description,
        Boolean active,
        Long linkedProducts,
        Instant createdAt) {
}
