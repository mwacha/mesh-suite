package com.meshsuite.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record VariationAxisInput(
        @NotBlank String name,
        @NotEmpty List<@NotBlank String> values) {
}
