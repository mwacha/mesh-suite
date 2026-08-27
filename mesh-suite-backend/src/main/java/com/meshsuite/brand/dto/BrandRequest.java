package com.meshsuite.brand.dto;

import jakarta.validation.constraints.NotBlank;

public record BrandRequest(
        @NotBlank String name,
        Boolean active) {
}
