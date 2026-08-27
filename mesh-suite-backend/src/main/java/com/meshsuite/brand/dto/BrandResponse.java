package com.meshsuite.brand.dto;

import java.time.Instant;
import java.util.UUID;

public record BrandResponse(
        UUID id,
        String name,
        Boolean active,
        Long linkedProducts,
        Instant createdAt) {
}
