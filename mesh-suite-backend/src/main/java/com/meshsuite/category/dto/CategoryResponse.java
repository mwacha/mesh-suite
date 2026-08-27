package com.meshsuite.category.dto;

import java.time.Instant;
import java.util.UUID;

public record CategoryResponse(
        UUID id,
        String name,
        String description,
        Boolean active,
        UUID parentId,
        String parentName,
        Long linkedProducts,
        Instant createdAt) {
}
