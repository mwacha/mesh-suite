package com.meshsuite.product.dto;

import com.meshsuite.product.domain.enums.ProductStatus;
import jakarta.validation.constraints.NotNull;

public record ProductStatusRequest(@NotNull ProductStatus status) {
}
