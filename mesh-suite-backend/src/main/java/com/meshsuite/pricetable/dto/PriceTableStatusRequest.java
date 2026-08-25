package com.meshsuite.pricetable.dto;

import jakarta.validation.constraints.NotNull;

public record PriceTableStatusRequest(@NotNull Boolean active) {
}
