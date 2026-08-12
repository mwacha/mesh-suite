package com.meshsuite.colorway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record ColorwayRequest(
        @NotBlank String name,
        @NotNull LocalDate effectiveDate,
        String description,
        Boolean active) {
}
