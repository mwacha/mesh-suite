package com.meshsuite.category.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record CategoryRequest(
        @NotBlank String name,
        String description,
        Boolean active,
        UUID parentId) {
}
